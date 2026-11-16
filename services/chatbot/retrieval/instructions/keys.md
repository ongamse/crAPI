## JWKS json file

The identity service signs JWTs with an RS256 key supplied at runtime as a `jwks.json` file
(a JWKS document with exactly one RSA key, `"alg": "RS256"`, `"use": "sig"` and a `kid`).
The key is not stored in the repository. The public part of the key is available at
`/.well-known/jwks.json`.
