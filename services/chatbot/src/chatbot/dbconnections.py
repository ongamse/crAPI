import os

from sqlalchemy.engine import URL

MONGO_USER = os.environ.get("MONGO_DB_USER", "admin")
# No hard-coded default: MONGO_DB_PASSWORD must be supplied via the environment.
# The previous default credential remains in git history and must be rotated.
MONGO_PASSWORD = os.environ["MONGO_DB_PASSWORD"]
MONGO_HOST = os.environ.get("MONGO_DB_HOST", "mongodb")
MONGO_PORT = os.environ.get("MONGO_DB_PORT", "27017")
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME", "crapi")

# Credential-free URIs. Credentials are passed to the driver as separate
# keyword arguments (see MONGO_CLIENT_KWARGS) so they are never embedded in a
# URI string and need no manual percent-encoding.
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
# No hard-coded default: DB_PASSWORD must be supplied via the environment.
POSTGRES_PASSWORD = os.environ["DB_PASSWORD"]
POSTGRES_DB = os.environ.get("DB_NAME", "crapi")

# SQLAlchemy URL object: credentials are held as separate fields and escaped
# by SQLAlchemy when the engine is created.
POSTGRES_URL = URL.create(
    drivername="postgresql",
    username=POSTGRES_USER,
    password=POSTGRES_PASSWORD,
    host=POSTGRES_HOST,
    port=int(POSTGRES_PORT),
    database=POSTGRES_DB,
    query={"sslmode": "disable"},
)

CHROMA_HOST = os.environ.get("CHROMA_HOST", "chromadb")
CHROMA_PORT = os.environ.get("CHROMA_PORT", "8000")
