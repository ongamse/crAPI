import os
from urllib.parse import quote_plus

from sqlalchemy.engine import URL

MONGO_USER = os.environ.get("MONGO_DB_USER", "admin")
# The MongoDB password must be supplied at runtime (env / secrets manager);
# there is intentionally no hard-coded fallback.
MONGO_PASSWORD = os.environ.get("MONGO_DB_PASSWORD")
if not MONGO_PASSWORD:
    raise RuntimeError("MONGO_DB_PASSWORD environment variable must be set")
MONGO_HOST = os.environ.get("MONGO_DB_HOST", "mongodb")
MONGO_PORT = os.environ.get("MONGO_DB_PORT", "27017")
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME", "crapi")

MONGO_CONNECTION_URI = (
    "mongodb://"
    + quote_plus(MONGO_USER)
    + ":"
    + quote_plus(MONGO_PASSWORD)
    + "@"
    + MONGO_HOST
    + ":"
    + MONGO_PORT
    + "/?directConnection=true"
)

POSTGRES_HOST = os.environ.get("DB_HOST", "postgresdb")
POSTGRES_PORT = os.environ.get("DB_PORT", "5432")
POSTGRES_USER = os.environ.get("DB_USER", "admin")
# The Postgres password must be supplied at runtime (env / secrets manager);
# there is intentionally no hard-coded fallback.
POSTGRES_PASSWORD = os.environ.get("DB_PASSWORD")
if not POSTGRES_PASSWORD:
    raise RuntimeError("DB_PASSWORD environment variable must be set")
POSTGRES_DB = os.environ.get("DB_NAME", "crapi")

# Structured URL object (credentials are escaped by SQLAlchemy and are never
# rendered into a scheme://user:pass@host string).
POSTGRES_URL = URL.create(
    "postgresql",
    username=POSTGRES_USER,
    password=POSTGRES_PASSWORD,
    host=POSTGRES_HOST,
    port=int(POSTGRES_PORT),
    database=POSTGRES_DB,
    query={"sslmode": "disable"},
)

CHROMA_HOST = os.environ.get("CHROMA_HOST", "chromadb")
CHROMA_PORT = os.environ.get("CHROMA_PORT", "8000")
