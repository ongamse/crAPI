import os

from sqlalchemy.engine import URL

MONGO_USER = os.environ.get("MONGO_DB_USER", "admin")
# The MongoDB password must be supplied via the environment (no hard-coded default).
MONGO_PASSWORD = os.environ["MONGO_DB_PASSWORD"]
MONGO_HOST = os.environ.get("MONGO_DB_HOST", "mongodb")
MONGO_PORT = os.environ.get("MONGO_DB_PORT", "27017")
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME", "crapi")

# Credentials are passed as discrete client options (no user:password@host URI).
MONGO_CLIENT_KWARGS = {
    "host": MONGO_HOST,
    "port": int(MONGO_PORT),
    "username": MONGO_USER,
    "password": MONGO_PASSWORD,
    "directConnection": True,
}

POSTGRES_HOST = os.environ.get("DB_HOST", "postgresdb")
POSTGRES_PORT = os.environ.get("DB_PORT", "5432")
POSTGRES_USER = os.environ.get("DB_USER", "admin")
# The Postgres password must be supplied via the environment (no hard-coded default).
POSTGRES_PASSWORD = os.environ["DB_PASSWORD"]
POSTGRES_DB = os.environ.get("DB_NAME", "crapi")

# Built from discrete fields; SQLAlchemy handles escaping of the password.
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
