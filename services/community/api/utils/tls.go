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
	"crypto/x509"
	"errors"
	"fmt"
	"net/http"
	"os"
	"time"
)

// TLSConfigFromEnv returns a TLS client config that verifies server
// certificates. When TLS_CA_BUNDLE points to a PEM file, its certificates are
// trusted in addition to the system roots (for private CAs). Certificate
// verification is never disabled.
func TLSConfigFromEnv() (*tls.Config, error) {
	cfg := &tls.Config{MinVersion: tls.VersionTLS12}
	bundle := os.Getenv("TLS_CA_BUNDLE")
	if bundle == "" {
		return cfg, nil
	}
	pool, err := x509.SystemCertPool()
	if err != nil || pool == nil {
		pool = x509.NewCertPool()
	}
	pem, err := os.ReadFile(bundle)
	if err != nil {
		return nil, fmt.Errorf("unable to read TLS_CA_BUNDLE: %w", err)
	}
	if !pool.AppendCertsFromPEM(pem) {
		return nil, errors.New("TLS_CA_BUNDLE contains no valid PEM certificates")
	}
	cfg.RootCAs = pool
	return cfg, nil
}

// NewHTTPClient returns an HTTP client for calling the identity service. It
// uses a dedicated transport (the global http.DefaultTransport is left
// untouched) that verifies server certificates. A zero timeout means no
// timeout.
func NewHTTPClient(timeout time.Duration) (*http.Client, error) {
	tlsConfig, err := TLSConfigFromEnv()
	if err != nil {
		return nil, err
	}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.TLSClientConfig = tlsConfig
	return &http.Client{Timeout: timeout, Transport: transport}, nil
}
