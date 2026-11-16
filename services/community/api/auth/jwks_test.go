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
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"math/big"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	jwt "github.com/dgrijalva/jwt-go"
)

func resetJWKSCache() {
	jwksMu.Lock()
	defer jwksMu.Unlock()
	jwksCache = map[string]*rsa.PublicKey{}
	jwksLastRefresh = time.Time{}
}

func newJWKSServer(t *testing.T, kid string, pub *rsa.PublicKey) *httptest.Server {
	t.Helper()
	body, err := json.Marshal(map[string]interface{}{
		"keys": []map[string]string{{
			"kty": "RSA",
			"kid": kid,
			"n":   base64.RawURLEncoding.EncodeToString(pub.N.Bytes()),
			"e":   base64.RawURLEncoding.EncodeToString(big.NewInt(int64(pub.E)).Bytes()),
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write(body)
	}))
}

func signToken(t *testing.T, method jwt.SigningMethod, key interface{}, kid string) string {
	t.Helper()
	tok := jwt.NewWithClaims(method, jwt.MapClaims{
		"sub": "user@example.com",
		"exp": time.Now().Add(time.Hour).Unix(),
	})
	tok.Header["kid"] = kid
	s, err := tok.SignedString(key)
	if err != nil {
		t.Fatal(err)
	}
	return s
}

func TestParseVerifiedToken(t *testing.T) {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	other, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	srv := newJWKSServer(t, "kid1", &priv.PublicKey)
	defer srv.Close()
	host := strings.TrimPrefix(srv.URL, "http://")
	t.Setenv("IDENTITY_SERVICE", host)
	t.Setenv("TLS_ENABLED", "false")

	t.Run("valid signature", func(t *testing.T) {
		resetJWKSCache()
		tok, err := parseVerifiedToken(signToken(t, jwt.SigningMethodRS256, priv, "kid1"))
		if err != nil || !tok.Valid {
			t.Fatalf("expected valid token, got err=%v", err)
		}
		claims := tok.Claims.(jwt.MapClaims)
		if claims["sub"] != "user@example.com" {
			t.Fatalf("unexpected sub claim: %v", claims["sub"])
		}
	})

	t.Run("forged signature rejected", func(t *testing.T) {
		resetJWKSCache()
		if _, err := parseVerifiedToken(signToken(t, jwt.SigningMethodRS256, other, "kid1")); err == nil {
			t.Fatal("expected token signed with unknown key to be rejected")
		}
	})

	t.Run("alg none rejected", func(t *testing.T) {
		resetJWKSCache()
		s := signToken(t, jwt.SigningMethodNone, jwt.UnsafeAllowNoneSignatureType, "kid1")
		if _, err := parseVerifiedToken(s); err == nil {
			t.Fatal("expected alg=none token to be rejected")
		}
	})

	t.Run("hmac confusion rejected", func(t *testing.T) {
		resetJWKSCache()
		s := signToken(t, jwt.SigningMethodHS256, priv.PublicKey.N.Bytes(), "kid1")
		if _, err := parseVerifiedToken(s); err == nil {
			t.Fatal("expected HS256 token to be rejected")
		}
	})

	t.Run("unknown kid rejected", func(t *testing.T) {
		resetJWKSCache()
		if _, err := parseVerifiedToken(signToken(t, jwt.SigningMethodRS256, priv, "nope")); err == nil {
			t.Fatal("expected unknown kid to be rejected")
		}
	})
}
