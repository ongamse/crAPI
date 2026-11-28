## JWKS json file

crAPI signs JWTs with an RSA key supplied as a JWKS file. No key is shipped in the repository.
Provide your own `jwks.json` (mounted at `/app/keys/jwks.json` in the identity service) or let the
identity service generate an ephemeral key at startup.

```
# jwks key format (values intentionally omitted)
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
            "p": "<first prime factor>",
            "q": "<second prime factor>",
            "dp": "<first factor CRT exponent>",
            "dq": "<second factor CRT exponent>",
            "qi": "<first CRT coefficient>"
        }
    ]
}
```
