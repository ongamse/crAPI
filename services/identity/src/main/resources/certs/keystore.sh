# Usage: keystore.sh <server.crt> <server.key> <outdir>
# Builds the identity PKCS12 keystore from a locally generated key. Output must NOT be committed.
# Password comes from TLS_KEYSTORE_PASSWORD in the environment.
set -e
: "${TLS_KEYSTORE_PASSWORD:?Set TLS_KEYSTORE_PASSWORD}"
CRT="$1"; KEY="$2"; OUT="${3:-.}"
rm -f "$OUT/server.p12"
openssl pkcs12 -export -in "$CRT" -inkey "$KEY" -out "$OUT/server.p12" -name identity -passout env:TLS_KEYSTORE_PASSWORD
