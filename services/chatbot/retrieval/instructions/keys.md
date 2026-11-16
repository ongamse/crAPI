## JWKS json file

The identity service signs JWTs with an RSA key (RS256). A custom key can be supplied as a
`jwks.json` file mounted into the identity service (for example `deploy/docker/keys/jwks.json`).
If no file is supplied, the service generates an ephemeral key at startup.

The file holds a single RSA JWK. Generate your own key pair and never commit it. The file has the
following shape, with every value elided:

```
# jwks key
{
    "keys": [
        {
            "kty": "RSA",
            "use": "sig",
            "alg": "RS256",
            "kid": "<key id>",
            "n": "<public modulus, base64url>",
            "e": "<public exponent, base64url>",
            "d": "<private exponent, base64url>",
            "p": "<prime factor p, base64url>",
            "q": "<prime factor q, base64url>",
            "dp": "<d mod (p-1), base64url>",
            "dq": "<d mod (q-1), base64url>",
            "qi": "<CRT coefficient, base64url>"
        }
    ]
}
```
