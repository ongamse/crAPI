## JWKS json file

The identity service signs JWTs with an RSA (RS256) key supplied at runtime as a
JWKS json (a single key, including its private parameters). The key is not stored
in the repository: supply your own via a `keys/jwks.json` file in the deployment
folder or the base64 encoded `JWKS` environment variable. The public part is
served at `/.well-known/jwks.json`.

Expected shape (placeholders only, generate your own key and rotate it if it is
ever exposed):

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
            "qi": "<base64url q inverse mod p>"
        }
    ]
}

```
