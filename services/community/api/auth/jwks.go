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

// allowedJWTAlgorithms lists the only signing algorithms accepted. The
// identity service signs tokens with its RSA key.
var allowedJWTAlgorithms = []string{"RS256"}

const (
	jwksFetchTimeout    = 10 * time.Second
	jwksMaxResponseSize = 1 << 20
	// minimum delay between JWKS refreshes triggered by unknown kids
	jwksRefreshInterval = 10 * time.Second
)

type jwk struct {
	Kty string `json:"kty"`
	Kid string `json:"kid"`
	N   string `json:"n"`
	E   string `json:"e"`
}

type jwkSet struct {
	Keys []jwk `json:"keys"`
}

var (
	jwksMu          sync.Mutex
	jwksCache       = map[string]*rsa.PublicKey{}
	jwksLastRefresh time.Time
)

// jwksURL returns the identity service JWKS endpoint.
func jwksURL() string {
	scheme := "http"
	if tlsEnabled, ok := os.LookupEnv("TLS_ENABLED"); ok && utils.IsTrue(tlsEnabled) {
		scheme = "https"
	}
	return fmt.Sprintf("%s://%s/identity/api/auth/jwks.json", scheme, os.Getenv("IDENTITY_SERVICE"))
}

// parseJWKS converts a JWKS document into kid -> RSA public key. Only the
// public part of RSA keys is used.
func parseJWKS(data []byte) (map[string]*rsa.PublicKey, error) {
	var set jwkSet
	if err := json.Unmarshal(data, &set); err != nil {
		return nil, err
	}
	keys := make(map[string]*rsa.PublicKey)
	for _, k := range set.Keys {
		if k.Kty != "RSA" {
			continue
		}
		nBytes, err := base64.RawURLEncoding.DecodeString(k.N)
		if err != nil {
			return nil, err
		}
		eBytes, err := base64.RawURLEncoding.DecodeString(k.E)
		if err != nil {
			return nil, err
		}
		e := new(big.Int).SetBytes(eBytes)
		if !e.IsInt64() || e.Int64() <= 1 || e.Int64() > 1<<31-1 {
			return nil, errors.New("invalid RSA exponent in JWKS")
		}
		keys[k.Kid] = &rsa.PublicKey{N: new(big.Int).SetBytes(nBytes), E: int(e.Int64())}
	}
	return keys, nil
}

func fetchJWKS() (map[string]*rsa.PublicKey, error) {
	client := &http.Client{Timeout: jwksFetchTimeout}
	resp, err := client.Get(jwksURL())
	if err != nil {
		return nil, err
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unable to fetch JWKS: status %d", resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, jwksMaxResponseSize))
	if err != nil {
		return nil, err
	}
	return parseJWKS(body)
}

// getSigningKey resolves the public key matching the kid header of the token,
// refreshing the cached key set once when the kid is unknown (key rotation).
func getSigningKey(kid string) (*rsa.PublicKey, error) {
	jwksMu.Lock()
	defer jwksMu.Unlock()
	if key, ok := jwksCache[kid]; ok {
		return key, nil
	}
	if len(jwksCache) == 0 || time.Since(jwksLastRefresh) >= jwksRefreshInterval {
		keys, err := fetchJWKS()
		jwksLastRefresh = time.Now()
		if err != nil {
			return nil, err
		}
		jwksCache = keys
	}
	if key, ok := jwksCache[kid]; ok {
		return key, nil
	}
	return nil, errors.New("signing key not found in JWKS")
}

// jwksKeyFunc is a jwt.Keyfunc that only accepts RS256 tokens signed by a key
// published in the identity service JWKS.
func jwksKeyFunc(token *jwt.Token) (interface{}, error) {
	if token.Method == nil || token.Method.Alg() != "RS256" {
		return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
	}
	kid, ok := token.Header["kid"].(string)
	if !ok {
		return nil, errors.New("token is missing kid header")
	}
	return getSigningKey(kid)
}

// parseVerifiedToken parses the token and verifies its signature and
// standard claims against the identity service public keys.
func parseVerifiedToken(tokenString string) (*jwt.Token, error) {
	parser := &jwt.Parser{ValidMethods: allowedJWTAlgorithms}
	return parser.Parse(tokenString, jwksKeyFunc)
}
