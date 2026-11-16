/*
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package auth

import (
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"os"
	"sync"
	"time"

	"crapi.proj/goservice/api/utils"
	jwt "github.com/dgrijalva/jwt-go"
)

const (
	jwksCacheTTL     = 5 * time.Minute
	jwksFetchTimeout = 10 * time.Second
	jwksMaxBodyBytes = 1 << 20
)

type jwk struct {
	Kty string `json:"kty"`
	Kid string `json:"kid"`
	Alg string `json:"alg"`
	Use string `json:"use"`
	N   string `json:"n"`
	E   string `json:"e"`
}

type jwkSet struct {
	Keys []jwk `json:"keys"`
}

type jwksCacheEntry struct {
	keys      map[string]*rsa.PublicKey
	fetchedAt time.Time
}

var (
	jwksMu    sync.Mutex
	jwksCache *jwksCacheEntry
)

func identityJWKSURL() string {
	scheme := "http"
	if tlsEnabled, ok := os.LookupEnv("TLS_ENABLED"); ok && utils.IsTrue(tlsEnabled) {
		scheme = "https"
	}
	return fmt.Sprintf("%s://%s/identity/api/auth/jwks.json", scheme, os.Getenv("IDENTITY_SERVICE"))
}

func decodeRSAPublicKey(k jwk) (*rsa.PublicKey, error) {
	nBytes, err := base64.RawURLEncoding.DecodeString(k.N)
	if err != nil {
		return nil, err
	}
	eBytes, err := base64.RawURLEncoding.DecodeString(k.E)
	if err != nil {
		return nil, err
	}
	e := new(big.Int).SetBytes(eBytes)
	if !e.IsInt64() || e.Int64() < 3 || e.Int64() > 1<<31-1 {
		return nil, errors.New("invalid RSA exponent in JWKS")
	}
	return &rsa.PublicKey{N: new(big.Int).SetBytes(nBytes), E: int(e.Int64())}, nil
}

// fetchIdentityJWKS downloads the identity service's public RS256 keys.
func fetchIdentityJWKS() (map[string]*rsa.PublicKey, error) {
	client := &http.Client{Timeout: jwksFetchTimeout}
	resp, err := client.Get(identityJWKSURL())
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unable to load JWKS: status %d", resp.StatusCode)
	}
	var set jwkSet
	if err := json.NewDecoder(io.LimitReader(resp.Body, jwksMaxBodyBytes)).Decode(&set); err != nil {
		return nil, err
	}
	keys := make(map[string]*rsa.PublicKey)
	for _, k := range set.Keys {
		if k.Kty != "RSA" || (k.Alg != "" && k.Alg != "RS256") || (k.Use != "" && k.Use != "sig") {
			continue
		}
		pub, err := decodeRSAPublicKey(k)
		if err != nil {
			continue
		}
		keys[k.Kid] = pub
	}
	if len(keys) == 0 {
		return nil, errors.New("no usable RS256 keys in JWKS")
	}
	return keys, nil
}

func lookupJWKSKey(keys map[string]*rsa.PublicKey, kid string) *rsa.PublicKey {
	if kid == "" {
		if len(keys) == 1 {
			for _, k := range keys {
				return k
			}
		}
		return nil
	}
	return keys[kid]
}

// identitySigningKey returns the identity service's RS256 public key for the
// given key id, refreshing the cached JWKS once when the key is not found.
func identitySigningKey(kid string) (*rsa.PublicKey, error) {
	jwksMu.Lock()
	defer jwksMu.Unlock()
	if jwksCache != nil && time.Since(jwksCache.fetchedAt) < jwksCacheTTL {
		if key := lookupJWKSKey(jwksCache.keys, kid); key != nil {
			return key, nil
		}
	}
	keys, err := fetchIdentityJWKS()
	if err != nil {
		return nil, err
	}
	jwksCache = &jwksCacheEntry{keys: keys, fetchedAt: time.Now()}
	if key := lookupJWKSKey(keys, kid); key != nil {
		return key, nil
	}
	return nil, errors.New("no matching signing key in JWKS")
}

// parseVerifiedToken parses a JWT and verifies its RS256 signature against
// the identity service's JWKS. Keys supplied by token headers are never used.
func parseVerifiedToken(tokenString string) (*jwt.Token, error) {
	parser := &jwt.Parser{ValidMethods: []string{"RS256"}}
	return parser.Parse(tokenString, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodRSA); !ok || t.Method.Alg() != "RS256" {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		kid, _ := t.Header["kid"].(string)
		return identitySigningKey(kid)
	})
}
