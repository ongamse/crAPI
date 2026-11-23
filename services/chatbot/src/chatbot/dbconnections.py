import os

from sqlalchemy.engine import URL

MONGO_USER = os.environ.get("MONGO_DB_USER", "admin")
# Required: no hard-coded default. The previous default credential remains in
# git history and must be rotated.
MONGO_PASSWORD = os.environ["MONGO_DB_PASSWORD"]
MONGO_HOST = os.environ.get("MONGO_DB_HOST", "mongodb")
MONGO_PORT = os.environ.get("MONGO_DB_PORT", "27017")
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME", "crapi")

# The URIs carry no credentials; they are supplied to the client separately
# through MONGO_CLIENT_KWARGS (username/password client options).
MONGO_CONNECTION_URI = "mongodb://%s:%s/?directConnection=true" % (
    MONGO_HOST,
    MONGO_PORT,
)

MONGO_CONNECTION_URI_ATLAS = "mongodb+srv://%s?retryWrites=true&w=majority" % (
    MONGO_HOST,
)

MONGO_CLIENT_KWARGS = {
    "username": MONGO_USER,
    "password": MONGO_PASSWORD,
}

POSTGRES_HOST = os.environ.get("DB_HOST", "postgresdb")
POSTGRES_PORT = os.environ.get("DB_PORT", "5432")
POSTGRES_USER = os.environ.get("DB_USER", "admin")
# Required: no hard-coded default. The previous default credential remains in
# git history and must be rotated.
POSTGRES_PASSWORD = os.environ["DB_PASSWORD"]
POSTGRES_DB = os.environ.get("DB_NAME", "crapi")

# Built from separate parameters (SQLAlchemy URL object) so the password is
# never interpolated into a URI string literal and is escaped correctly.
POSTGRES_URI = URL.create(
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
