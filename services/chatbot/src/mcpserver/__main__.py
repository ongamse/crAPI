import logging
import os

from .mcp_server import app

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

if __name__ == "__main__":
    logger.info("Starting MCP server...")
    app.settings.host = "0.0.0.0"
    app.settings.port = 5002
    app.settings.debug = os.getenv("DEBUG", "False").lower() in (
        "true",
        "1",
        "yes",
    )
    app.run(transport="streamable-http")
