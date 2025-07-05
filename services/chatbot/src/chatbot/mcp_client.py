import asyncio
import os

from langchain_mcp_adapters.client import MultiServerMCPClient

API_TOKEN = os.environ.get(
    "API_TOKEN",
    "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJHYXJmaWVsZC5MaW5kZ3JlbkBleGFtcGxlLmNvbSIsImlhdCI6MTc1MTU2ODczNiwiZXhwIjoxNzUyMTczNTM2LCJyb2xlIjoidXNlciJ9.ampuVX-YCNFPMwVycsz-wm9lESLozTeB7EAntV2WvBqd49XpwTBIY7FIk7tLEaEZ8PrZDEDIV1Dfoy235WBwTIznmT6frOE-z0tJoX45tRW6Elz3XO1XuWrA0RQWcClEJQ5hIsvlDvZjWHYfDrq7q_o5iKxn7Tdch19s_pSVKmfOaJ3p6-VIX1f_YnsMo4SZQaDspPvtMLlojwmeE1bfrsbA_lyt8YBovFCCFeI2WKGxk2-uNIThrCG1koP_cNoTS2TQkWDmc4-bwFybXTWgLG82InkWk4nqKfquRF6HVhTSztSq1AmIpUR3zF_tUkMPkT5b-Lps_PEXmJPC9-ObBg",
)
mcp_client = MultiServerMCPClient(
    {
        "crapi": {
            "transport": "streamable_http",
            "url": "http://localhost:5002/mcp/",
            "headers": {
                "Authorization": "Bearer " + API_TOKEN,
            },
        },
    }
)
