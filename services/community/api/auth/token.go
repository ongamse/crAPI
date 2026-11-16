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
	"bytes"
	"crypto/rsa"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math/big"
	"net/http"
	"os"
	"strings"
	"time"

	"crapi.proj/goservice/api/models"
	"crapi.proj/goservice/api/utils"
	jwt "github.com/dgrijalva/jwt-go"
	"github.com/jinzhu/gorm"
)

type Token struct {
	Token string `json:"token"`
}

// ExtractToken return token from Authorization Bearer
func ExtractToken(r *http.Request) string {
	keys := r.URL.Query()
	token := keys.Get("token")
	if token != "" {
		return token
	}
	bearerToken := r.Header.Get("Authorization")
	if len(strings.Split(bearerToken, " ")) == 2 {
		return strings.Split(bearerToken, " ")[1]
	}
	return ""
}

// ExtractTokenID Verify token either it's valid or not.
// If token is valid we extract username from token Claims.
// Then check that username in postgres database.
func ExtractTokenID(r *http.Request, db *gorm.DB) (uint32, error) {
	http.DefaultTransport.(*http.Transport).TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	tokenVerifyURL := fmt.Sprintf("http://%s/identity/api/auth/verify", os.Getenv("IDENTITY_SERVICE"))
	tls_enabled, is_tls := os.LookupEnv("TLS_ENABLED")
	if is_tls && utils.IsTrue(tls_enabled) {
		tokenVerifyURL = fmt.Sprintf("https://%s/identity/api/auth/verify", os.Getenv("IDENTITY_SERVICE"))
	}
	tokenString := ExtractToken(r)
	tokenJSON, err := json.Marshal(Token{Token: tokenString})
	if err != nil {
		log.Println(err)
		return 0, err
	}

	resp, err := http.Post(tokenVerifyURL, "application/json", bytes.NewBuffer(tokenJSON))
	if err != nil {
		log.Println(err)
		return 0, err
	}
	defer func() {
		if err := resp.Body.Close(); err != nil {
			log.Println("Error closing response body:", err)
		}
	}()

	tokenValid := resp.StatusCode == 200
	// Verify the signature against the identity service's JWKS public key,
	// accepting only RS256 (the algorithm the identity service signs with).
	jwksURL := strings.TrimSuffix(tokenVerifyURL, "verify") + "jwks.json"
	token, err := new(jwt.Parser).ParseWithClaims(
		tokenString,
		jwt.MapClaims{},
		func(t *jwt.Token) (interface{}, error) {
			if t.Method.Alg() != jwt.SigningMethodRS256.Alg() {
				return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
			}
			kid, _ := t.Header["kid"].(string)
			return fetchIdentityPublicKey(jwksURL, kid)
		},
	)
	if err != nil {
		log.Println(err)
		return 0, err
	}
	claims, ok := token.Claims.(jwt.MapClaims)

	if ok && tokenValid {
		name := claims["sub"]
		if name != nil {
			//converting name interface to string
			email := fmt.Sprintf("%v", name)
			// checking username in postgres databse.
			err := CheckTokenInDB(email, db)
			return 0, err
		}
		var uid uint32
		return uint32(uid), nil
	}

	return 0, errors.New("Unauthorized")
}

type jwk struct {
	Kty string `json:"kty"`
	Kid string `json:"kid"`
	Alg string `json:"alg"`
	N   string `json:"n"`
	E   string `json:"e"`
}

type jwks struct {
	Keys []jwk `json:"keys"`
}

// fetchIdentityPublicKey loads the RSA public key used to verify tokens from
// the identity service's JWKS endpoint. When kid is set, the key with that id
// is selected; otherwise the JWKS must contain exactly one RS256 key.
func fetchIdentityPublicKey(jwksURL string, kid string) (*rsa.PublicKey, error) {
	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Get(jwksURL)
	if err != nil {
		return nil, err
	}
	defer func() {
		if err := resp.Body.Close(); err != nil {
			log.Println("Error closing response body:", err)
		}
	}()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unable to fetch JWKS: status %d", resp.StatusCode)
	}

	var set jwks
	if err := json.NewDecoder(io.LimitReader(resp.Body, 1<<20)).Decode(&set); err != nil {
		return nil, err
	}

	var selected *jwk
	for i := range set.Keys {
		k := &set.Keys[i]
		if k.Kty != "RSA" || (k.Alg != "" && k.Alg != "RS256") {
			continue
		}
		if kid != "" && k.Kid != kid {
			continue
		}
		if kid == "" && selected != nil {
			return nil, errors.New("ambiguous JWKS key")
		}
		selected = k
		if kid != "" {
			break
		}
	}
	if selected == nil {
		return nil, errors.New("no matching JWKS key found")
	}

	nBytes, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(selected.N, "="))
	if err != nil {
		return nil, err
	}
	eBytes, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(selected.E, "="))
	if err != nil {
		return nil, err
	}
	e := new(big.Int).SetBytes(eBytes)
	if !e.IsInt64() || e.Int64() <= 1 || e.Int64() > 1<<31-1 {
		return nil, errors.New("invalid JWKS exponent")
	}
	return &rsa.PublicKey{N: new(big.Int).SetBytes(nBytes), E: int(e.Int64())}, nil
}

// CheckTokenInDB call FindUserByEmail and check that email in postgres database
func CheckTokenInDB(username string, db *gorm.DB) error {
	email := fmt.Sprintf("%v", username)
	//Calling user model for database query
	num, err := models.FindAuthorByEmail(email, db)

	if num != nil {
		return nil
	}
	if err != nil {
		return err
	}
	return err

}

// Pretty display the claims licely in the terminal
func Pretty(data interface{}) {
	b, err := json.MarshalIndent(data, "", " ")
	if err != nil {
		log.Println(err)
		return
	}

	log.Println(string(b))
}

//
