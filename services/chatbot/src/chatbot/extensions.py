import asyncio

from langchain_community.utilities.sql_database import SQLDatabase
from motor.motor_asyncio import AsyncIOMotorClient
from sqlalchemy import create_engine

from .dbconnections import (
    MONGO_CLIENT_KWARGS,
    MONGO_CONNECTION_URI,
    POSTGRES_CONNECT_KWARGS,
    POSTGRES_URI,
)

mongo_client = AsyncIOMotorClient(MONGO_CONNECTION_URI, **MONGO_CLIENT_KWARGS)
db = mongo_client.chatgpt
# Credentials are passed to the driver as connect arguments, not embedded in the URL.
postgresdb = SQLDatabase(
    create_engine(POSTGRES_URI, connect_args=POSTGRES_CONNECT_KWARGS)
)


def init_mongo():
    # Create collections if they don't exist
    # db.create_collection("chat_sessions")
    # db.create_collection("sessions")
    pass
