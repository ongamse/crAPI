## JWKS json file

The identity service signs JWTs with an RS256 key. The private JWKS is never committed to the
repository. To use a persistent key, place a `jwks.json` file in the `keys` folder of the
deployment (for example `deploy/docker/keys/jwks.json`). If no file is provided, the identity
service generates an ephemeral key at startup. The public key is always available at
`/.well-known/jwks.json`.

The file must contain exactly one RSA key with `"alg": "RS256"` in the following format (values
are placeholders):

```
# jwks key
{
    "keys": [
        {
            "kty": "RSA",
            "alg": "RS256",
            "use": "sig",
            "kid": "<key id>",
            "n": "<base64url modulus>",
            "e": "AQAB",
            "d": "<base64url private exponent>",
            "p": "<base64url prime p>",
            "q": "<base64url prime q>",
            "dp": "<base64url dp>",
            "dq": "<base64url dq>",
            "qi": "<base64url qi>"
        }
    ]
}

```
