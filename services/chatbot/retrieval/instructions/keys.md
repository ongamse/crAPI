## JWKS json file

```
# jwks key (public part only; the private key is supplied at runtime and is never committed)
{
    "keys": [
        {
            "kty": "RSA",
            "e": "AQAB",
            "use": "sig",
            "kid": "<KEY_ID: base64url thumbprint of the public key>",
            "alg": "RS256",
            "n": "<RSA_MODULUS: base64url-encoded public modulus>"
        }
    ]
}

```