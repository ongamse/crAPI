## JWKS json file

The identity service signs JWTs with an RSA key pair that is provided at runtime as a
JWKS file (a single RS256 key). No key is stored in the repository; generate your own
and mount it at `keys/jwks.json` in the deployment folder you use. The file has this
shape (values below are placeholders, not real key material):

```
# jwks key
{
    "keys": [
        {
            "kty": "RSA",
            "use": "sig",
            "alg": "RS256",
            "kid": "<key id>",
            "n": "<base64url modulus>",
            "e": "<base64url public exponent>",
            "d": "<base64url private exponent>",
            "p": "<base64url prime p>",
            "q": "<base64url prime q>",
            "dp": "<base64url d mod (p-1)>",
            "dq": "<base64url d mod (q-1)>",
            "qi": "<base64url q^-1 mod p>"
        }
    ]
}
```
