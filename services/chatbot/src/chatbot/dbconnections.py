import os

MONGO_USER = os.environ.get("MONGO_DB_USER", "admin")
# The MongoDB password must be supplied via the environment (no in-code default).
# NOTE: the previously hard-coded default credential remains in git history and
# must be rotated.
MONGO_PASSWORD = os.environ.get("MONGO_DB_PASSWORD")
if not MONGO_PASSWORD:
    raise RuntimeError("MONGO_DB_PASSWORD environment variable must be set")
MONGO_HOST = os.environ.get("MONGO_DB_HOST", "mongodb")
MONGO_PORT = os.environ.get("MONGO_DB_PORT", "27017")
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME", "crapi")

# Connection URIs carry no credentials; the credentials are handed to the
# driver separately through MONGO_CLIENT_KWARGS (e.g.
# AsyncIOMotorClient(MONGO_CONNECTION_URI, **MONGO_CLIENT_KWARGS)).
MONGO_CONNECTION_URI = "mongodb://%s:%s/?directConnection=true" % (
    MONGO_HOST,
    MONGO_PORT,
)

MONGO_CONNECTION_URI_ATLAS = "mongodb+srv://%s/?retryWrites=true&w=majority" % (
    MONGO_HOST,
)

MONGO_CLIENT_KWARGS = {
    "username": MONGO_USER,
    "password": MONGO_PASSWORD,
}

POSTGRES_HOST = os.environ.get("DB_HOST", "postgresdb")
POSTGRES_PORT = os.environ.get("DB_PORT", "5432")
POSTGRES_USER = os.environ.get("DB_USER", "admin")
# The PostgreSQL password must be supplied via the environment (no in-code default).
# NOTE: the previously hard-coded default credential remains in git history and
# must be rotated.
POSTGRES_PASSWORD = os.environ.get("DB_PASSWORD")
if not POSTGRES_PASSWORD:
    raise RuntimeError("DB_PASSWORD environment variable must be set")
POSTGRES_DB = os.environ.get("DB_NAME", "crapi")

# No credential-bearing URI is built; extensions.py assembles the connection
# with sqlalchemy.engine.URL.create(), which keeps the password out of any string.
POSTGRES_URL_PARTS = {
    "drivername": "postgresql",
    "username": POSTGRES_USER,
    "password": POSTGRES_PASSWORD,
    "host": POSTGRES_HOST,
    "port": int(POSTGRES_PORT),
    "database": POSTGRES_DB,
    "query": {"sslmode": "disable"},
}

CHROMA_HOST = os.environ.get("CHROMA_HOST", "chromadb")
CHROMA_PORT = os.environ.get("CHROMA_PORT", "8000")
