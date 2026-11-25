import os

MONGO_USER = os.environ.get("MONGO_DB_USER", "admin")
# Supplied at runtime via the MONGO_DB_PASSWORD environment variable
# (docker-compose / k8s configmap / helm values); no password is hardcoded here.
MONGO_PASSWORD = os.environ.get("MONGO_DB_PASSWORD", "")
MONGO_HOST = os.environ.get("MONGO_DB_HOST", "mongodb")
MONGO_PORT = os.environ.get("MONGO_DB_PORT", "27017")
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME", "crapi")

# Credential-free URI; the username/password are passed to the Mongo client as
# separate keyword arguments (see MONGO_CLIENT_KWARGS) so no credentialed URL
# string is ever built.
MONGO_CONNECTION_URI = "mongodb://%s:%s/?directConnection=true" % (
    MONGO_HOST,
    MONGO_PORT,
)

MONGO_CLIENT_KWARGS = {
    "username": MONGO_USER,
    "password": MONGO_PASSWORD,
    "authSource": os.environ.get("MONGO_DB_AUTH_SOURCE", "admin"),
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
