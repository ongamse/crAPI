import os

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
