## JWKS json file

crAPI's identity service signs JWTs with an RSA (RS256) key supplied as a JWKS file.
No private key is stored in the repository. To use a custom key, create a JWKS file
containing a single RSA JWK (with `kty`, `alg`, `use`, `kid`, `n`, `e` and the private
members) and place it at `keys/jwks.json` in the deployment folder, or export it
base64-encoded in the `JWKS` environment variable. If no key is supplied, the identity
service generates one at startup.

The public key used to verify JWTs is served at `/.well-known/jwks.json`.
