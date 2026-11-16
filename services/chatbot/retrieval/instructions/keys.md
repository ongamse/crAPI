## JWKS json file

crAPI signs JWTs with an RS256 key pair supplied to the identity service at runtime as a JWKS
file (`jwks.json`). The private key members (`d`, `p`, `q`, `dp`, `dq`, `qi`) must never be
committed or shared; only the public key (`n`, `e`, `kid`, `kty`, `use`, `alg`) is published at
`/.well-known/jwks.json`.

```
# jwks key (structure only, values are placeholders)
{
    "keys": [
        {
            "kty": "RSA",
            "alg": "RS256",
            "use": "sig",
            "kid": "<key id>",
            "n": "<public modulus>",
            "e": "AQAB",
            "d": "<private exponent - secret>",
            "p": "<prime p - secret>",
            "q": "<prime q - secret>",
            "dp": "<secret>",
            "dq": "<secret>",
            "qi": "<secret>"
        }
    ]
}
```
