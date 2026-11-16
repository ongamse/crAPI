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

package utils

import (
	"crypto/tls"
	"encoding/pem"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestNewHTTPClientVerifiesCertificates(t *testing.T) {
	srv := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	t.Run("untrusted server rejected", func(t *testing.T) {
		t.Setenv("TLS_CA_BUNDLE", "")
		client, err := NewHTTPClient(0)
		if err != nil {
			t.Fatal(err)
		}
		if resp, err := client.Get(srv.URL); err == nil {
			_ = resp.Body.Close()
			t.Fatal("expected certificate verification failure")
		}
	})

	t.Run("server trusted via TLS_CA_BUNDLE", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "ca.pem")
		data := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: srv.Certificate().Raw})
		if err := os.WriteFile(path, data, 0o600); err != nil {
			t.Fatal(err)
		}
		t.Setenv("TLS_CA_BUNDLE", path)
		client, err := NewHTTPClient(0)
		if err != nil {
			t.Fatal(err)
		}
		resp, err := client.Get(srv.URL)
		if err != nil {
			t.Fatalf("expected request to succeed with CA bundle: %v", err)
		}
		_ = resp.Body.Close()
	})

	t.Run("invalid bundle fails closed", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "bad.pem")
		if err := os.WriteFile(path, []byte("not a cert"), 0o600); err != nil {
			t.Fatal(err)
		}
		t.Setenv("TLS_CA_BUNDLE", path)
		if _, err := NewHTTPClient(0); err == nil {
			t.Fatal("expected error for invalid bundle")
		}
		t.Setenv("TLS_CA_BUNDLE", filepath.Join(t.TempDir(), "missing.pem"))
		if _, err := NewHTTPClient(0); err == nil {
			t.Fatal("expected error for missing bundle")
		}
	})

	t.Run("config never skips verification", func(t *testing.T) {
		t.Setenv("TLS_CA_BUNDLE", "")
		cfg, err := TLSConfigFromEnv()
		if err != nil {
			t.Fatal(err)
		}
		if cfg.InsecureSkipVerify || cfg.MinVersion < tls.VersionTLS12 {
			t.Fatal("unexpected insecure TLS config")
		}
	})
}
