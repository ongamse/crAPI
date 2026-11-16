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
import time
import requests
import jwt
from jwt import PyJWKSet
from functools import wraps
from rest_framework import status
from rest_framework.response import Response
from django.conf import settings
from utils import messages
from crapi.user.models import User
import logging

logger = logging.getLogger()

JWKS_CACHE_TTL_SECONDS = 300
_jwks_cache = {"jwk_set": None, "fetched_at": 0.0}


def _get_identity_jwk_set(force_refresh=False):
    """
    Fetch (and briefly cache) the identity service's public JWKS.
    :raises jwt.exceptions.PyJWKClientError: if the JWKS cannot be retrieved
    """
    now = time.monotonic()
    cached = _jwks_cache["jwk_set"]
    if (
        force_refresh
        or cached is None
        or now - _jwks_cache["fetched_at"] > JWKS_CACHE_TTL_SECONDS
    ):
        try:
            response = requests.get(
                settings.IDENTITY_JWKS,
                verify=settings.OUTBOUND_TLS_VERIFY,
                timeout=10,
            )
            response.raise_for_status()
            cached = PyJWKSet.from_dict(response.json())
        except (requests.RequestException, ValueError) as e:
            raise jwt.exceptions.PyJWKClientError(
                f"Unable to load JWKS from identity service: {e}"
            ) from e
        _jwks_cache["jwk_set"] = cached
        _jwks_cache["fetched_at"] = now
    return cached


def _find_signing_key(token, jwk_set):
    kid = jwt.get_unverified_header(token).get("kid")
    keys = jwk_set.keys
    if kid is None:
        return keys[0] if len(keys) == 1 else None
    for key in keys:
        if key.key_id == kid:
            return key
    return None


def decode_verified_token(token):
    """
    Decode a JWT after verifying its RS256 signature against the identity
    service's JWKS (never trusting header-supplied keys).
    :param token: encoded jwt string
    :return: decoded claims
    :raises jwt.exceptions.PyJWTError: if the token cannot be verified
    """
    signing_key = _find_signing_key(token, _get_identity_jwk_set())
    if signing_key is None:
        # key may have rotated; refresh once before failing closed
        signing_key = _find_signing_key(token, _get_identity_jwk_set(True))
    if signing_key is None:
        raise jwt.exceptions.InvalidKeyError("No matching signing key in JWKS")
    return jwt.decode(token, key=signing_key.key, algorithms=["RS256"])


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
                    verify=settings.OUTBOUND_TLS_VERIFY,
                    timeout=settings.OUTBOUND_REQUEST_TIMEOUT,
                )
                logger.debug(
                    f"Identity url: {identity_url}, token_verify_response: {token_verify_response}"
                )
                response_status_code = token_verify_response.status_code
                if response_status_code == status.HTTP_200_OK:
                    decoded = decode_verified_token(token)
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

        except (
            jwt.exceptions.PyJWTError,
            KeyError,
            User.DoesNotExist,
            requests.exceptions.Timeout,
        ) as e:
            logger.debug(
                f"JWT token verification failed with exception: {e}", exc_info=True
            )
            return Response(
                {"message": messages.INVALID_TOKEN},
                status=status.HTTP_401_UNAUTHORIZED,
                content_type="application/json",
            )

    return new_func
