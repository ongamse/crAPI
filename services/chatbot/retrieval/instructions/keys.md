## JWKS json file

The identity service signs JWTs with an RSA key (RS256) supplied as a JWKS file. No private key is
committed to the repository. By default the identity service generates an ephemeral RSA key at
startup. To use a persistent key, mount your own `jwks.json` at `/app/keys/jwks.json` (for example
from `deploy/docker/keys`).

```
# jwks key format (placeholders only, generate your own key)
{
    "keys": [
        {
            "kty": "RSA",
            "alg": "RS256",
            "use": "sig",
            "kid": "<key id>",
            "n": "<public modulus, base64url>",
            "e": "AQAB",
            "d": "<private exponent, base64url>",
            "p": "<prime p, base64url>",
            "q": "<prime q, base64url>",
            "dp": "<d mod (p-1), base64url>",
            "dq": "<d mod (q-1), base64url>",
            "qi": "<q^-1 mod p, base64url>"
        }
    ]
}
```

The public part of the key is served at `/.well-known/jwks.json`.
