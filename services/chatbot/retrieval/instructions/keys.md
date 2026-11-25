## JWKS json file

The identity service signs JWTs with an RSA key supplied as a JWKS file. The signing key is not
stored in this repository. To use your own key, create a `jwks.json` file in the `keys` folder of
your deployment (for example `deploy/docker/keys/jwks.json`). If no file is provided, the identity
service generates an ephemeral RSA key at startup (tokens become invalid after a restart).

The file must contain exactly one RSA key with `"alg": "RS256"`, in the following format. The
values below are placeholders; generate your own key pair and keep the private members (`p`, `q`,
`d`, `dp`, `dq`, `qi`) secret.

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
            "p": "<base64url first prime factor>",
            "q": "<base64url second prime factor>",
            "dp": "<base64url first factor CRT exponent>",
            "dq": "<base64url second factor CRT exponent>",
            "qi": "<base64url first CRT coefficient>"
        }
    ]
}

```
