## JWKS json file

crAPI signs JWTs with an RS256 key held by the identity service. No key material is stored in the repository.

- The public JWK set used to verify tokens is served by the identity service at `/.well-known/jwks.json`.
- To use a stable signing key, mount a complete RSA JWKS (including the private components) as `keys/jwks.json` in the deployment folder, or pass it base64 encoded in the `JWKS` environment variable.
- When no key is supplied, the identity service generates an ephemeral RS256 key at startup.
