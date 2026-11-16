#
# Licensed under the Apache License, Version 2.0 (the “License”);
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an “AS IS” BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""
Contains all the methods related to jwt token
"""
import requests
import jwt
from functools import wraps
from rest_framework import status
from rest_framework.response import Response
from django.conf import settings
from utils import messages
from crapi.user.models import User
import logging

logger = logging.getLogger()

# Only RS256 is accepted; the identity service signs tokens with its RSA key.
ALLOWED_JWT_ALGORITHMS = ["RS256"]

_jwks_cache = {}


def _fetch_jwks():
    """
    fetch the public key set published by the identity service
    :return: dict of kid -> RSA public key
    """
    response = requests.get(
        settings.IDENTITY_JWKS,
        verify=settings.HTTP_CLIENT_VERIFY,
        timeout=settings.HTTP_CLIENT_TIMEOUT,
    )
    response.raise_for_status()
    keys = {}
    for jwk in response.json().get("keys", []):
        if jwk.get("kty") != "RSA":
            continue
        # Only the public part is ever used for verification
        public_jwk = {k: jwk[k] for k in ("kty", "n", "e", "kid") if k in jwk}
        keys[jwk.get("kid")] = jwt.PyJWK(public_jwk, algorithm="RS256").key
    return keys


def _get_signing_key(token):
    """
    resolve the public key matching the kid header of the token
    :param token: encoded jwt
    :return: RSA public key
    """
    kid = jwt.get_unverified_header(token).get("kid")
    if kid not in _jwks_cache:
        # Refresh once to pick up rotated keys
        try:
            _jwks_cache.clear()
            _jwks_cache.update(_fetch_jwks())
        except (requests.RequestException, ValueError) as e:
            raise jwt.exceptions.PyJWKClientError(
                f"Unable to fetch JWKS: {e}"
            ) from e
    if kid not in _jwks_cache:
        raise jwt.exceptions.PyJWKClientError("Signing key not found in JWKS")
    return _jwks_cache[kid]


def jwt_auth_required(func):
    """
    decorator for authorizing http requests
    :param func: view function of the api
    :return: a new view function which
        calls the actual view function if authorized
        returns error message if not authorized
    """

    @wraps(func)
    def new_func(*args, **kwargs):
        try:
            request = args[1]
            if (
                "HTTP_AUTHORIZATION" in request.META
                and request.META.get("HTTP_AUTHORIZATION")[0:7] == "Bearer "
            ):
                token = request.META.get("HTTP_AUTHORIZATION")[7:]
                tokenJson = {"token": token}
                identity_url = settings.IDENTITY_VERIFY
                logger.debug(f"Identity url: {identity_url}, tokenJson: {tokenJson}")
                token_verify_response = requests.post(
                    identity_url,
                    json=tokenJson,
                    verify=settings.HTTP_CLIENT_VERIFY,
                    timeout=settings.HTTP_CLIENT_TIMEOUT,
                )
                logger.debug(
                    f"Identity url: {identity_url}, token_verify_response: {token_verify_response}"
                )
                response_status_code = token_verify_response.status_code
                if response_status_code == status.HTTP_200_OK:
                    decoded = jwt.decode(
                        token,
                        key=_get_signing_key(token),
                        algorithms=ALLOWED_JWT_ALGORITHMS,
                        options={"verify_aud": False},
                    )
                    username = decoded["sub"]
                    user = User.objects.get(email=username)
                    # Add user object to the view function if authorized
                    kwargs["user"] = user
                    return func(*args, **kwargs)
                logger.debug("JWT token verification failed")
                return Response(
                    {"message": messages.INVALID_TOKEN},
                    status=status.HTTP_401_UNAUTHORIZED,
                    content_type="application/json",
                )
            logger.debug("JWT token not found in request header")
            return Response(
                {"message": messages.JWT_REQUIRED},
                status=status.HTTP_401_UNAUTHORIZED,
                content_type="application/json",
            )

        except (jwt.exceptions.PyJWTError, User.DoesNotExist) as e:
            logger.debug(
                f"JWT token verification failed with exception: {e}", exc_info=True
            )
            return Response(
                {"message": messages.INVALID_TOKEN},
                status=status.HTTP_401_UNAUTHORIZED,
                content_type="application/json",
            )

    return new_func
