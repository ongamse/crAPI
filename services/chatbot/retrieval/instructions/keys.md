## JWKS json file

crAPI signs its JWTs with an RSA key supplied as a JWKS document. The key is never committed to the repository.

```
# jwks key (structure only, values are supplied by the deployer)
{
    "keys": [
        {
            "kty": "RSA",
            "alg": "RS256",
            "use": "sig",
            "kid": "<key id>",
            "n": "<public modulus>",
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

To use a custom key, place a *jwks.json* file in the *keys* folder of your deployment folder (for example `/deploy/docker/keys`), or pass the base64 encoded document in the `JWKS` environment variable of the identity service. If no key is supplied, the identity service generates an ephemeral key at startup. The public part is served at `/.well-known/jwks.json`.
