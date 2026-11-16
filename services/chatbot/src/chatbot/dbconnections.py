import os


def _require_env(name):
    # Credentials must be supplied by the environment / secrets manager.
    # The previously hard-coded default password remains in git history and
    # must be rotated.
    value = os.environ.get(name)
    if not value:
        raise RuntimeError("Required environment variable %s is not set" % name)
    return value


MONGO_USER = os.environ.get("MONGO_DB_USER", "admin")
MONGO_PASSWORD = _require_env("MONGO_DB_PASSWORD")
MONGO_HOST = os.environ.get("MONGO_DB_HOST", "mongodb")
MONGO_PORT = os.environ.get("MONGO_DB_PORT", "27017")
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME", "crapi")

MONGO_CONNECTION_URI = "mongodb://%s:%s@%s:%s/?directConnection=true" % (
    MONGO_USER,
    MONGO_PASSWORD,
    MONGO_HOST,
    MONGO_PORT,
)

MONGO_CONNECTION_URI_ATLAS = "mongodb+srv://%s:%s@%s?retryWrites=true&w=majority" % (
    MONGO_USER,
    MONGO_PASSWORD,
    MONGO_HOST,
)

POSTGRES_HOST = os.environ.get("DB_HOST", "postgresdb")
POSTGRES_PORT = os.environ.get("DB_PORT", "5432")
POSTGRES_USER = os.environ.get("DB_USER", "admin")
POSTGRES_PASSWORD = _require_env("DB_PASSWORD")
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
