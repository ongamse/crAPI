#!/usr/bin/env python3
"""
MCP Server Implementation for OWASP crAPI API

This MCP server exposes the API operations defined in the OpenAPI specification
as MCP tools and resources.
"""

import argparse
import logging
import os
from typing import Any, Dict, List, Optional, Union

import httpx
from mcp.server.fastmcp import Context, FastMCP

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# Create MCP server
app = FastMCP(name="OWASP crAPI MCP Server")

# API configuration
API_URL = os.environ.get("API_URL", "http://crapi.allvapps.com:30080")
API_TOKEN = os.environ.get(
    "API_TOKEN",
    "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJHYXJmaWVsZC5MaW5kZ3JlbkBleGFtcGxlLmNvbSIsImlhdCI6MTc1MTU2ODczNiwiZXhwIjoxNzUyMTczNTM2LCJyb2xlIjoidXNlciJ9.ampuVX-YCNFPMwVycsz-wm9lESLozTeB7EAntV2WvBqd49XpwTBIY7FIk7tLEaEZ8PrZDEDIV1Dfoy235WBwTIznmT6frOE-z0tJoX45tRW6Elz3XO1XuWrA0RQWcClEJQ5hIsvlDvZjWHYfDrq7q_o5iKxn7Tdch19s_pSVKmfOaJ3p6-VIX1f_YnsMo4SZQaDspPvtMLlojwmeE1bfrsbA_lyt8YBovFCCFeI2WKGxk2-uNIThrCG1koP_cNoTS2TQkWDmc4-bwFybXTWgLG82InkWk4nqKfquRF6HVhTSztSq1AmIpUR3zF_tUkMPkT5b-Lps_PEXmJPC9-ObBg",
)
API_AUTH_TYPE = os.environ.get("API_AUTH_TYPE", "Bearer")


# Async HTTP client for API calls
async def get_http_client():
    """Create and configure the HTTP client with appropriate authentication."""
    headers = {}

    if API_AUTH_TYPE == "Bearer":
        headers["Authorization"] = f"Bearer {API_TOKEN}"
    elif API_AUTH_TYPE == "token":
        headers["Authorization"] = API_TOKEN

    return httpx.AsyncClient(
        base_url=API_URL,
        headers=headers,
    )


# MCP tools for API operations


@app.tool(description="Used to create an account")
async def signup(ctx: Context) -> str:
    """
    Used to create an account
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/auth/signup"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="POST /identity/api/auth/login")
async def login(ctx: Context) -> str:
    """
    POST /identity/api/auth/login
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/auth/login"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Sends an OTP to email to reset password")
async def forgot_password(ctx: Context) -> str:
    """
    Sends an OTP to email to reset password
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/auth/forget-password"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="To validate the One-Time-Password sent using `forgot password`")
async def check_otp_v3(ctx: Context) -> str:
    """
    To validate the One-Time-Password sent using `forgot password`
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/auth/v3/check-otp"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="To validate the One-Time-Password sent using `forgot password`")
async def check_otp_v2(ctx: Context) -> str:
    """
    To validate the One-Time-Password sent using `forgot password`
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/auth/v2/check-otp"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="POST /identity/api/auth/v4.0/user/login-with-token")
async def login_with_token(ctx: Context) -> str:
    """
    POST /identity/api/auth/v4.0/user/login-with-token
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/auth/v4.0/user/login-with-token"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="POST /identity/api/auth/v2.7/user/login-with-token")
async def login_with_token_v2_7(ctx: Context) -> str:
    """
    POST /identity/api/auth/v2.7/user/login-with-token
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/auth/v2.7/user/login-with-token"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Reset user password using JWT token")
async def reset_password(ctx: Context) -> str:
    """
    Reset user password using JWT token
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/user/reset-password"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Sends token to new email")
async def change_email(ctx: Context) -> str:
    """
    Sends token to new email
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/user/change-email"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Verify token sent for changing email")
async def verify_email_token(ctx: Context) -> str:
    """
    Verify token sent for changing email
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/user/verify-email-token"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="GET /identity/api/v2/user/dashboard")
async def get_dashboard(ctx: Context) -> str:
    """
    GET /identity/api/v2/user/dashboard
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/user/dashboard"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="POST /identity/api/v2/user/pictures")
async def update_profile_pic(ctx: Context) -> str:
    """
    POST /identity/api/v2/user/pictures
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/user/pictures"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="POST /identity/api/v2/user/videos")
async def upload_profile_video(ctx: Context) -> str:
    """
    POST /identity/api/v2/user/videos
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/user/videos"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Get the video associated with the user's profile.")
async def get_profile_video(video_id: int, ctx: Context) -> str:
    """
    Get the video associated with the user's profile.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/user/videos/{video_id}"

            # Extract query parameters
            query_params = {}
            request_body = None

            if video_id is not None:
                url = url.replace("{video_id}", str(video_id))

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Update the video identified by video_id in this user's profile.")
async def update_profile_video(video_id: int, ctx: Context) -> str:
    """
    Update the video identified by video_id in this user's profile.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/user/videos/{video_id}"

            # Extract query parameters
            query_params = {}
            request_body = None

            if video_id is not None:
                url = url.replace("{video_id}", str(video_id))

            # Make the request
            response = await client.put(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(
    description="Delete the video identified by video_id from this user's profile."
)
async def delete_profile_video(video_id: int, ctx: Context) -> str:
    """
    Delete the video identified by video_id from this user's profile.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/user/videos/{video_id}"

            # Extract query parameters
            query_params = {}
            request_body = None

            if video_id is not None:
                url = url.replace("{video_id}", str(video_id))

            # Make the request
            response = await client.delete(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Convert the format for the specified video.")
async def convert_profile_video(ctx: Context, video_id: int = 0) -> str:
    """
    Convert the format for the specified video.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/user/videos/convert_video"

            # Extract query parameters
            query_params = {}
            request_body = None

            if video_id is not None:
                query_params["video_id"] = video_id

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Delete profile video of other users by video_id as admin")
async def admin_delete_profile_video(video_id: int, ctx: Context) -> str:
    """
    Delete profile video of other users by video_id as admin
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/admin/videos/{video_id}"

            # Extract query parameters
            query_params = {}
            request_body = None

            if video_id is not None:
                url = url.replace("{video_id}", str(video_id))

            # Make the request
            response = await client.delete(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="GET /identity/api/v2/vehicle/vehicles")
async def get_vehicles(ctx: Context) -> str:
    """
    GET /identity/api/v2/vehicle/vehicles
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/vehicle/vehicles"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="POST /identity/api/v2/vehicle/add_vehicle")
async def add_vehicle(ctx: Context) -> str:
    """
    POST /identity/api/v2/vehicle/add_vehicle
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/vehicle/add_vehicle"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Get user's vehicle location")
async def get_location(ctx: Context) -> str:
    """
    Get user's vehicle location
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/vehicle/{vehicleId}/location"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Resend vehicles details to be added to the user dashboard")
async def vehicle_resend_email(ctx: Context) -> str:
    """
    Resend vehicles details to be added to the user dashboard
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/identity/api/v2/vehicle/resend_email"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to get a specific post in the forum")
async def get_post(ctx: Context) -> str:
    """
    Used to get a specific post in the forum
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/community/api/v2/community/posts/{postId}"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to create a new post in the forum")
async def create_post(ctx: Context) -> str:
    """
    Used to create a new post in the forum
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/community/api/v2/community/posts"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to add a comment to an existing post in the forum")
async def post_comment(ctx: Context) -> str:
    """
    Used to add a comment to an existing post in the forum
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/community/api/v2/community/posts/{postId}/comment"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to fetch the most recent posts in the forum.")
async def get_recent_posts(ctx: Context, limit: int = 0, offset: int = 0) -> str:
    """
    Used to fetch the most recent posts in the forum.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/community/api/v2/community/posts/recent"

            # Extract query parameters
            query_params = {}
            request_body = None

            if limit is not None:
                query_params["limit"] = limit
            if offset is not None:
                query_params["offset"] = offset

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to add a new coupon to the shop database")
async def add_new_coupon(ctx: Context) -> str:
    """
    Used to add a new coupon to the shop database
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/community/api/v2/coupon/new-coupon"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to validate the provided discount coupon code")
async def validate_coupon(ctx: Context) -> str:
    """
    Used to validate the provided discount coupon code
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/community/api/v2/coupon/validate-coupon"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to get products for the shop")
async def get_products(ctx: Context) -> str:
    """
    Used to get products for the shop
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/shop/products"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.get(
                url,
                params=query_params,
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to add the specified product to the product catalog.")
async def add_new_product(ctx: Context) -> str:
    """
    Used to add the specified product to the product catalog.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/shop/products"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to create a new order for a product in the shop.")
async def create_order(ctx: Context) -> str:
    """
    Used to create a new order for a product in the shop.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/shop/orders"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to update the order specified by the order_id.")
async def update_order(order_id: int, ctx: Context) -> str:
    """
    Used to update the order specified by the order_id.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/shop/orders/{order_id}"

            # Extract query parameters
            query_params = {}
            request_body = None

            if order_id is not None:
                url = url.replace("{order_id}", str(order_id))

            # Make the request
            response = await client.put(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to get the order details for order identified by order_id.")
async def get_order_byID(order_id: int, ctx: Context) -> str:
    """
    Used to get the order details for order identified by order_id.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/shop/orders/{order_id}"

            # Extract query parameters
            query_params = {}
            request_body = None

            if order_id is not None:
                url = url.replace("{order_id}", str(order_id))

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to get user's past orders")
async def get_orders(limit: int, offset: int, ctx: Context) -> str:
    """
    Used to get user's past orders
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/shop/orders/all"

            # Extract query parameters
            query_params = {}
            request_body = None

            if limit is not None:
                query_params["limit"] = limit
            if offset is not None:
                query_params["offset"] = offset

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to return order specified by the order_id")
async def return_order(order_id: int, ctx: Context) -> str:
    """
    Used to return order specified by the order_id
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/shop/orders/return_order"

            # Extract query parameters
            query_params = {}
            request_body = None

            if order_id is not None:
                query_params["order_id"] = order_id

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to apply the coupon for the current user.")
async def apply_coupon(ctx: Context) -> str:
    """
    Used to apply the coupon for the current user.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/shop/apply_coupon"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to get the return qr code image for UPS shipments.")
async def get_workshop_qr_code(Accept: str, ctx: Context) -> str:
    """
    Used to get the return qr code image for UPS shipments.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/shop/return_qr_code"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to get all the users in the workshop database.")
async def get_workshop_users_all(ctx: Context, limit: int = 0, offset: int = 0) -> str:
    """
    Used to get all the users in the workshop database.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/management/users/all"

            # Extract query parameters
            query_params = {}
            request_body = None

            if limit is not None:
                query_params["limit"] = limit
            if offset is not None:
                query_params["offset"] = offset

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to get all the available mechanics")
async def get_mechanics(ctx: Context) -> str:
    """
    Used to get all the available mechanics
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/mechanic/"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(
    description="Used to contact a mechanic for a service request on your vehicle"
)
async def contact_mechanic(ctx: Context) -> str:
    """
    Used to contact a mechanic for a service request on your vehicle
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/merchant/contact_mechanic"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to create the service report and assign to the mechanic")
async def create_service_report(
    mechanic_code: str, problem_details: str, vin: str, ctx: Context
) -> str:
    """
    Used to create the service report and assign to the mechanic
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/mechanic/receive_report"

            # Extract query parameters
            query_params = {}
            request_body = None

            if mechanic_code is not None:
                query_params["mechanic_code"] = mechanic_code
            if problem_details is not None:
                query_params["problem_details"] = problem_details
            if vin is not None:
                query_params["vin"] = vin

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to get the service report specified by the report_id")
async def get_report_byID(report_id: int, ctx: Context) -> str:
    """
    Used to get the service report specified by the report_id
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/mechanic/mechanic_report"

            # Extract query parameters
            query_params = {}
            request_body = None

            if report_id is not None:
                query_params["report_id"] = report_id

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Fetch all service requests assigned to this specific mechanic.")
async def get_service_requests_for_mechanic(
    limit: int, offset: int, ctx: Context
) -> str:
    """
    Fetch all service requests assigned to this specific mechanic.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/mechanic/service_requests"

            # Extract query parameters
            query_params = {}
            request_body = None

            if limit is not None:
                query_params["limit"] = limit
            if offset is not None:
                query_params["offset"] = offset

            # Make the request
            response = await client.get(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


@app.tool(description="Used to register a new mechanic in the workshop.")
async def mechanic_signup(ctx: Context) -> str:
    """
    Used to register a new mechanic in the workshop.
    """
    async with await get_http_client() as client:
        try:
            # Build the URL with path parameters
            url = "/workshop/api/mechanic/signup"

            # Extract query parameters
            query_params = {}
            request_body = None

            # Make the request
            response = await client.post(
                url, params=query_params, json=request_body if request_body else None
            )

            # Check if the request was successful
            response.raise_for_status()

            # Return the response
            return str(response.text)

        except httpx.HTTPStatusError as e:
            return f"API Error: {e.response.status_code} - {e.response.text}"
        except Exception as e:
            return f"Error: {str(e)}"


# MCP resources


@app.resource("api://info")
def get_api_info() -> str:
    """
    Get API information
    """
    return f"""
    Title: OWASP crAPI API
    Version: 1-oas3
    Description: API description
    """


@app.resource("schema://Order")
def get_Order_schema() -> str:
    """
    Get the Order schema definition
    """
    return """
    properties:\n  created_on:\n    format: date-time\n    type: string\n  id:\n    readOnly: true\n    type: integer\n  product:\n    $ref: '#/components/schemas/Product'\n  quantity:\n    type: integer\n  status:\n    $ref: '#/components/schemas/OrderStatusEnum'\n  user:\n    $ref: '#/components/schemas/User'\nrequired:\n- created_on\n- id\n- product\n- user\ntype: object\n
    """


@app.resource("schema://User")
def get_User_schema() -> str:
    """
    Get the User schema definition
    """
    return """
    properties:\n  email:\n    type: string\n  number:\n    nullable: true\n    type: string\nrequired:\n- email\ntype: object\n
    """


@app.resource("schema://NewProduct")
def get_NewProduct_schema() -> str:
    """
    Get the NewProduct schema definition
    """
    return """
    example:\n  image_url: http://example.com/wheelbase.png\n  name: WheelBase\n  price: '10.12'\nproperties:\n  image_url:\n    format: url\n    type: string\n  name:\n    type: string\n  price:\n    format: decimal\n    pattern: ^\\d{0,18}(\\.\\d{0,2})?$\n    type: string\nrequired:\n- image_url\n- name\n- price\ntype: object\n
    """


@app.resource("schema://Products")
def get_Products_schema() -> str:
    """
    Get the Products schema definition
    """
    return """
    items:\n  $ref: '#/components/schemas/Product'\ntype: array\n
    """


@app.resource("schema://Product")
def get_Product_schema() -> str:
    """
    Get the Product schema definition
    """
    return """
    example:\n  id: 1\n  image_url: images/seat.svg\n  name: Seat\n  price: '10.00'\nproperties:\n  id:\n    readOnly: true\n    type: integer\n  image_url:\n    format: url\n    type: string\n  name:\n    type: string\n  price:\n    format: decimal\n    pattern: ^\\d{0,18}(\\.\\d{0,2})?$\n    type: string\nrequired:\n- id\n- image_url\n- name\n- price\ntype: object\n
    """


@app.resource("schema://OrderStatusEnum")
def get_OrderStatusEnum_schema() -> str:
    """
    Get the OrderStatusEnum schema definition
    """
    return """
    enum:\n- delivered\n- return pending\n- returned\ntype: string\n
    """


@app.resource("schema://ProductQuantity")
def get_ProductQuantity_schema() -> str:
    """
    Get the ProductQuantity schema definition
    """
    return """
    properties:\n  product_id:\n    example: 1\n    type: integer\n  quantity:\n    example: 1\n    type: integer\nrequired:\n- product_id\n- quantity\ntype: object\n
    """


@app.resource("schema://Post")
def get_Post_schema() -> str:
    """
    Get the Post schema definition
    """
    return """
    example:\n  CreatedAt: '2021-09-16T01:46:32.432Z'\n  author:\n    created_at: '2021-09-16T01:46:32.432Z'\n    email: hacker@darkweb.com\n    nickname: Hacker\n    profile_pic_url: ''\n    vehicleid: abac4018-5a38-466c-ab7f-361908afeab6\n  authorid: 3\n  comments: []\n  content: Hello world 3\n  id: ConZLXacq3MqhbLQDrbNLf\n  title: Title 3\nproperties:\n  CreatedAt:\n    type: string\n  author:\n    $ref: '#/components/schemas/Author'\n  authorid:\n    format: int32\n    type: integer\n  comments:\n    description: ''\n    items:\n      type: string\n    type: array\n  content:\n    type: string\n  id:\n    type: string\n  title:\n    type: string\nrequired:\n- id\n- title\n- content\n- author\n- comments\n- authorid\n- CreatedAt\ntitle: Post\ntype: object\n
    """


@app.resource("schema://Author")
def get_Author_schema() -> str:
    """
    Get the Author schema definition
    """
    return """
    example:\n  created_at: '2021-09-16T01:46:32.432Z'\n  email: hacker@darkweb.com\n  nickname: Hacker\n  profile_pic_url: ''\n  vehicleid: 4bae9968-ec7f-4de3-a3a0-ba1b2ab5e5e5\nproperties:\n  created_at:\n    type: string\n  email:\n    type: string\n  nickname:\n    type: string\n  profile_pic_url:\n    type: string\n  vehicleid:\n    type: string\nrequired:\n- nickname\n- email\n- vehicleid\n- profile_pic_url\n- created_at\ntitle: Author\ntype: object\n
    """


@app.resource("schema://VideoForm")
def get_VideoForm_schema() -> str:
    """
    Get the VideoForm schema definition
    """
    return """
    properties:\n  conversion_params:\n    type: string\n  id:\n    format: int64\n    type: integer\n  videoName:\n    type: string\n  video_url:\n    type: string\ntype: object\n
    """


@app.resource("schema://CRAPIResponse")
def get_CRAPIResponse_schema() -> str:
    """
    Get the CRAPIResponse schema definition
    """
    return """
    properties:\n  message:\n    type: string\n  status:\n    format: int32\n    type: integer\ntype: object\n
    """


@app.resource("schema://OtpForm")
def get_OtpForm_schema() -> str:
    """
    Get the OtpForm schema definition
    """
    return """
    properties:\n  email:\n    example: Cristobal.Weissnat@example.com\n    maxLength: 30\n    minLength: 5\n    type: string\n  otp:\n    example: '9969'\n    maxLength: 4\n    minLength: 3\n    type: string\n  password:\n    example: 5hmb0gvyC__hVQg\n    maxLength: 30\n    minLength: 5\n    type: string\nrequired:\n- email\n- otp\n- password\ntype: object\n
    """


@app.resource("schema://JwtResponse")
def get_JwtResponse_schema() -> str:
    """
    Get the JwtResponse schema definition
    """
    return """
    properties:\n  message:\n    type: string\n  role:\n    enum:\n    - ROLE_UNDEFINED\n    - ROLE_USER\n    - ROLE_MECHANIC\n    - ROLE_ADMIN\n    type: string\n  token:\n    type: string\n  type:\n    type: string\ntype: object\n
    """


@app.resource("schema://LoginWithEmailToken")
def get_LoginWithEmailToken_schema() -> str:
    """
    Get the LoginWithEmailToken schema definition
    """
    return """
    properties:\n  email:\n    maxLength: 60\n    minLength: 3\n    type: string\n  token:\n    maxLength: 60\n    minLength: 3\n    type: string\nrequired:\n- email\n- token\ntype: object\n
    """


@app.resource("schema://ProfileVideo")
def get_ProfileVideo_schema() -> str:
    """
    Get the ProfileVideo schema definition
    """
    return """
    example:\n  conversion_params: -v codec h264\n  id: 1\n  profileVideo: data:image/jpeg;base64,aGFrZmhhcw==\n  video_name: abc.mp4\nproperties:\n  conversion_params:\n    type: string\n  id:\n    type: number\n  user:\n    $ref: '#/components/schemas/User'\n  video:\n    type: string\n  video_name:\n    type: string\nrequired:\n- id\n- video_name\n- converstion_params\n- video\n- user\ntype: object\n
    """


@app.resource("schema://ApplyCouponRequest")
def get_ApplyCouponRequest_schema() -> str:
    """
    Get the ApplyCouponRequest schema definition
    """
    return """
    example:\n  amount: 75\n  coupon_code: TRAC075\nproperties:\n  amount:\n    type: integer\n  coupon_code:\n    type: string\nrequired:\n- amount\n- coupon_code\ntype: object\n
    """


@app.resource("schema://ApplyCouponResponse")
def get_ApplyCouponResponse_schema() -> str:
    """
    Get the ApplyCouponResponse schema definition
    """
    return """
    example:\n  credit: 165\n  message: Coupon successfully applied!\nproperties:\n  credit:\n    type: integer\n  message:\n    type: string\nrequired:\n- credit\n- message\ntype: object\n
    """


@app.resource("schema://AddCouponRequest")
def get_AddCouponRequest_schema() -> str:
    """
    Get the AddCouponRequest schema definition
    """
    return """
    example:\n  amount: 75\n  coupon_code: TRAC075\nproperties:\n  amount:\n    type: integer\n  coupon_code:\n    type: string\nrequired:\n- coupon_code\n- amount\ntype: object\n
    """


@app.resource("schema://AddCouponResponse")
def get_AddCouponResponse_schema() -> str:
    """
    Get the AddCouponResponse schema definition
    """
    return """
    example:\n  CreatedAt: '2023-12-07T14:22:29.832Z'\n  amount: '75'\n  coupon_code: TRAC075\nproperties:\n  amount:\n    type: string\n  coupon_code:\n    type: string\n  createdAt:\n    type: string\nrequired:\n- amount\n- coupon_code\n- CreatedAt\ntype: object\n
    """


@app.resource("schema://ValidateCouponRequest")
def get_ValidateCouponRequest_schema() -> str:
    """
    Get the ValidateCouponRequest schema definition
    """
    return """
    example:\n  coupon_code: TRAC075\nproperties:\n  coupon_code:\n    type: string\nrequired:\n- coupon_code\ntype: object\n
    """


@app.resource("schema://ValidateCouponResponse")
def get_ValidateCouponResponse_schema() -> str:
    """
    Get the ValidateCouponResponse schema definition
    """
    return """
    example:\n  CreatedAt: '2023-12-07T14:22:29.832Z'\n  amount: '75'\n  coupon_code: TRAC075\nproperties:\n  amount:\n    type: string\n  coupon_code:\n    type: string\n  createdAt:\n    type: string\nrequired:\n- amount\n- coupon_code\n- CreatedAt\ntype: object\n
    """


@app.resource("schema://ServiceRequests")
def get_ServiceRequests_schema() -> str:
    """
    Get the ServiceRequests schema definition
    """
    return """
    properties:\n  service_requests:\n    items:\n      properties:\n        created_on:\n          format: date-time\n          type: string\n        id:\n          readOnly: true\n          type: integer\n        mechanic:\n          properties:\n            id:\n              readOnly: true\n              type: integer\n            mechanic_code:\n              type: string\n            user:\n              properties:\n                email:\n                  type: string\n                number:\n                  nullable: true\n                  type: string\n              required:\n              - email\n              type: object\n          required:\n          - id\n          - mechanic_code\n          - user\n          type: object\n        problem_details:\n          type: string\n        status:\n          enum:\n          - Pending\n          - Finished\n          type: string\n        vehicle:\n          properties:\n            id:\n              readOnly: true\n              type: integer\n            owner:\n              properties:\n                email:\n                  type: string\n                number:\n                  nullable: true\n                  type: string\n              required:\n              - email\n              type: object\n            vin:\n              type: string\n          required:\n          - id\n          - owner\n          - vin\n          type: object\n      required:\n      - created_on\n      - id\n      - mechanic\n      - vehicle\n      type: object\n    type: array\nrequired:\n- service_requests\ntitle: Service Requests\ntype: object\n
    """


def parse_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description="MCP Server for OWASP crAPI API")
    parser.add_argument(
        "--transport",
        choices=["stdio", "streamable-http"],
        default="streamable-http",
        help="Transport type (stdio or streamable-http)",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    logger.info(f"Starting MCP server with {args.transport} transport")
    app.settings.host = "0.0.0.0"
    app.settings.port = 5002
    app.run(transport=args.transport)
