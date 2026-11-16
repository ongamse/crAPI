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
	"log"
	"net/http"
	"os"
	"strings"
	"sync"
)

var (
	outboundOnce      sync.Once
	outboundTransport http.RoundTripper
)

// failingTransport rejects every request. It is used when TLS_CA_BUNDLE is set
// but cannot be loaded, so outbound calls fail closed instead of silently
// falling back to a different trust configuration.
type failingTransport struct{ err error }

func (f failingTransport) RoundTrip(*http.Request) (*http.Response, error) {
	return nil, f.err
}

func buildOutboundTransport() http.RoundTripper {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	tlsConfig := &tls.Config{MinVersion: tls.VersionTLS12}
	if bundlePath := os.Getenv("TLS_CA_BUNDLE"); bundlePath != "" {
		pool, err := x509.SystemCertPool()
		if err != nil || pool == nil {
			pool = x509.NewCertPool()
		}
		pem, err := os.ReadFile(bundlePath)
		if err != nil {
			log.Printf("Unable to read TLS_CA_BUNDLE: %v", err)
			return failingTransport{err: err}
		}
		if !pool.AppendCertsFromPEM(pem) {
			err = errors.New("TLS_CA_BUNDLE does not contain any valid PEM certificates")
			log.Print(err)
			return failingTransport{err: err}
		}
		tlsConfig.RootCAs = pool
	}
	transport.TLSClientConfig = tlsConfig
	return transport
}

// OutboundTransport returns the shared transport for outbound HTTPS calls.
// Certificates are verified against the system roots, plus the PEM bundle in
// TLS_CA_BUNDLE when set (e.g. a private or self-signed CA). It is built once.
func OutboundTransport() http.RoundTripper {
	outboundOnce.Do(func() {
		outboundTransport = buildOutboundTransport()
	})
	return outboundTransport
}

// OutboundHTTPClient returns an http.Client using OutboundTransport.
func OutboundHTTPClient() *http.Client {
	return &http.Client{Transport: OutboundTransport()}
}

func IsTrue(a string) bool {
	a = strings.ToLower(a)
	true_list := []string{"true", "1"}
	for _, b := range true_list {
		if b == a {
			return true
		}
	}
	return false
}

func IsTLSEnabled() bool {
	tls_enabled, is_tls := os.LookupEnv("TLS_ENABLED")
	if is_tls && IsTrue(tls_enabled) {
		return true
	}
	return false
}
