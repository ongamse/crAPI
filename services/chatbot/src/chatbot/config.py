import os

from dotenv import load_dotenv

from .dbconnections import CHROMA_HOST, CHROMA_PORT

load_dotenv()


class Config:
    SECRET_KEY = os.getenv("SECRET_KEY", "super-secret")
    DEFAULT_MODEL_NAME = os.getenv("DEFAULT_MODEL", "gpt-4o-mini")
    CHROMA_HOST = CHROMA_HOST
    CHROMA_PORT = CHROMA_PORT
