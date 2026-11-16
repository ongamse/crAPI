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

package responses

import (
	"bytes"
	"encoding/json"
	"log"
	"net/http"
)

// JSON return response in json
func JSON(w http.ResponseWriter, statusCode int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("X-Content-Type-Options", "nosniff")

	// Encode first so a failure can be reported as JSON with a proper status.
	var buf bytes.Buffer
	if err := json.NewEncoder(&buf).Encode(data); err != nil {
		log.Println("Error encoding response:", err)
		statusCode = http.StatusInternalServerError
		buf.Reset()
		// Static JSON body; never echo error text or untrusted data.
		buf.WriteString("{\"error\":\"internal server error\"}\n")
	}

	w.WriteHeader(statusCode)
	if _, writeErr := w.Write(buf.Bytes()); writeErr != nil {
		log.Println("Error writing response:", writeErr)
	}
}

// ERROR return
func ERROR(w http.ResponseWriter, statusCode int, err error) {
	if err != nil {
		JSON(w, statusCode, struct {
			Error string `json:"error"`
		}{
			Error: err.Error(),
		})
		return
	}
}
