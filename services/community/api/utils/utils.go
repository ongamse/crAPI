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
	"log"
	"os"
	"strings"
	"sync"
)

var (
	clientTLSConfig     *tls.Config
	clientTLSConfigOnce sync.Once
)

// ClientTLSConfig returns a TLS config for outbound HTTPS calls that always
// verifies the server certificate. Trust roots are the system trust store,
// plus an optional operator-supplied PEM CA bundle from SSL_CERT_FILE.
func ClientTLSConfig() *tls.Config {
	clientTLSConfigOnce.Do(func() {
		pool, err := x509.SystemCertPool()
		if err != nil || pool == nil {
			pool = x509.NewCertPool()
		}
		if caFile := os.Getenv("SSL_CERT_FILE"); caFile != "" {
			pem, err := os.ReadFile(caFile)
			if err != nil {
				log.Printf("Unable to read CA bundle %s: %v", caFile, err)
			} else if !pool.AppendCertsFromPEM(pem) {
				log.Printf("No valid certificates found in CA bundle %s", caFile)
			}
		}
		clientTLSConfig = &tls.Config{MinVersion: tls.VersionTLS12, RootCAs: pool}
	})
	return clientTLSConfig.Clone()
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
