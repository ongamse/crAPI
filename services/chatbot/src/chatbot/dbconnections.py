import os
from urllib.parse import quote_plus

MONGO_USER = os.environ.get("MONGO_DB_USER", "admin")
# No hard-coded default: MONGO_DB_PASSWORD must be supplied via the environment.
# The previous default credential remains in git history and must be rotated.
MONGO_PASSWORD = os.environ["MONGO_DB_PASSWORD"]
MONGO_HOST = os.environ.get("MONGO_DB_HOST", "mongodb")
MONGO_PORT = os.environ.get("MONGO_DB_PORT", "27017")
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME", "crapi")

_MONGO_USERINFO = quote_plus(MONGO_USER) + ":" + quote_plus(MONGO_PASSWORD)
MONGO_CONNECTION_URI = "".join(
    [
        "mongodb://",
        _MONGO_USERINFO,
        "@",
        MONGO_HOST,
        ":",
        MONGO_PORT,
        "/?directConnection=true",
    ]
)

MONGO_CONNECTION_URI_ATLAS = "mongodb+srv://%s:%s@%s?retryWrites=true&w=majority" % (
    MONGO_USER,
    MONGO_PASSWORD,
    MONGO_HOST,
)

POSTGRES_HOST = os.environ.get("DB_HOST", "postgresdb")
POSTGRES_PORT = os.environ.get("DB_PORT", "5432")
POSTGRES_USER = os.environ.get("DB_USER", "admin")
# No hard-coded default: DB_PASSWORD must be supplied via the environment.
POSTGRES_PASSWORD = os.environ["DB_PASSWORD"]
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
