import os
from urllib.parse import quote_plus, urlunsplit

MONGO_USER = os.environ.get("MONGO_DB_USER", "admin")
# Required: no hard-coded fallback. Supplied via env / secrets manager.
# The previously committed default must be rotated (it remains in git history).
MONGO_PASSWORD = os.environ["MONGO_DB_PASSWORD"]
MONGO_HOST = os.environ.get("MONGO_DB_HOST", "mongodb")
MONGO_PORT = os.environ.get("MONGO_DB_PORT", "27017")
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME", "crapi")



def _build_uri(scheme, user, password, host, path, query):
    """Assemble a URI from env-supplied parts, percent-encoding credentials."""
    netloc = "%s@%s" % (
        ":".join((quote_plus(user), quote_plus(password))),
        host,
    )
    return urlunsplit((scheme, netloc, path, query, ""))


MONGO_CONNECTION_URI = _build_uri(
    "mongodb",
    MONGO_USER,
    MONGO_PASSWORD,
    "%s:%s" % (MONGO_HOST, MONGO_PORT),
    "/",
    "directConnection=true",
)

MONGO_CONNECTION_URI_ATLAS = _build_uri(
    "mongodb+srv",
    MONGO_USER,
    MONGO_PASSWORD,
    MONGO_HOST,
    "",
    "retryWrites=true&w=majority",
)

POSTGRES_HOST = os.environ.get("DB_HOST", "postgresdb")
POSTGRES_PORT = os.environ.get("DB_PORT", "5432")
POSTGRES_USER = os.environ.get("DB_USER", "admin")
# Required: no hard-coded fallback. Supplied via env / secrets manager.
# The previously committed default must be rotated (it remains in git history).
POSTGRES_PASSWORD = os.environ["DB_PASSWORD"]
POSTGRES_DB = os.environ.get("DB_NAME", "crapi")

POSTGRES_URI = _build_uri(
    "postgresql",
    POSTGRES_USER,
    POSTGRES_PASSWORD,
    "%s:%s" % (POSTGRES_HOST, POSTGRES_PORT),
    "/%s" % POSTGRES_DB,
    "sslmode=disable",
)

CHROMA_HOST = os.environ.get("CHROMA_HOST", "chromadb")
CHROMA_PORT = os.environ.get("CHROMA_PORT", "8000")
