import os
from urllib.parse import quote_plus, urlunsplit

MONGO_USER = os.environ.get("MONGO_DB_USER", "admin")
# Required: no hard-coded fallback. Supplied via env / secrets manager.
# The previously committed default must be rotated (it remains in git history).
MONGO_PASSWORD = os.environ["MONGO_DB_PASSWORD"]
MONGO_HOST = os.environ.get("MONGO_DB_HOST", "mongodb")
MONGO_PORT = os.environ.get("MONGO_DB_PORT", "27017")
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME", "crapi")



def _mongo_uri(scheme, host, path, query):
    """Assemble a Mongo URI from env-supplied parts, percent-encoding credentials."""
    netloc = "%s@%s" % (
        ":".join((quote_plus(MONGO_USER), quote_plus(MONGO_PASSWORD))),
        host,
    )
    return urlunsplit((scheme, netloc, path, query, ""))


MONGO_CONNECTION_URI = _mongo_uri(
    "mongodb",
    "%s:%s" % (MONGO_HOST, MONGO_PORT),
    "/",
    "directConnection=true",
)

MONGO_CONNECTION_URI_ATLAS = _mongo_uri(
    "mongodb+srv",
    MONGO_HOST,
    "",
    "retryWrites=true&w=majority",
)

POSTGRES_HOST = os.environ.get("DB_HOST", "postgresdb")
POSTGRES_PORT = os.environ.get("DB_PORT", "5432")
POSTGRES_USER = os.environ.get("DB_USER", "admin")
POSTGRES_PASSWORD = os.environ.get("DB_PASSWORD", "crapisecretpassword")
POSTGRES_DB = os.environ.get("DB_NAME", "crapi")

POSTGRES_URI = "postgresql://%s:%s@%s:%s/%s?sslmode=disable" % (
    POSTGRES_USER,
    POSTGRES_PASSWORD,
    POSTGRES_HOST,
    POSTGRES_PORT,
    POSTGRES_DB,
)

CHROMA_HOST = os.environ.get("CHROMA_HOST", "chromadb")
CHROMA_PORT = os.environ.get("CHROMA_PORT", "8000")
