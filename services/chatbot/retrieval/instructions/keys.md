## JWKS json file

The identity service signs JWTs with an RSA key (RS256) that is supplied at deploy time as a
JWKS json file. The private key is never stored in the repository.

- Docker: place `jwks.json` in `deploy/docker/keys` (mounted at `/app/keys/jwks.json`).
- Kubernetes: create the `jwt-key-secret` secret from your own `jwks.json`.
- Helm: put `jwks.json` in `deploy/helm/keys` or provide your own secret.

The file must contain exactly one RSA key with `alg` set to `RS256`, in this shape (values omitted):

```
{
    "keys": [
        {
            "kty": "RSA",
            "use": "sig",
            "alg": "RS256",
            "kid": "<key id>",
            "n": "<modulus>",
            "e": "<public exponent>",
            "d": "<private exponent>",
            "p": "<prime p>",
            "q": "<prime q>",
            "dp": "<d mod (p-1)>",
            "dq": "<d mod (q-1)>",
            "qi": "<q^-1 mod p>"
        }
    ]
}
```

The public part is served at `/.well-known/jwks.json`.
