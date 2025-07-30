import os
import chromadb
from langchain_community.embeddings import OpenAIEmbeddings
from langchain.prompts import PromptTemplate
from chatbot.extensions import db
from .config import Config
from langchain.chains import RetrievalQA
from langchain_openai import ChatOpenAI
from langchain_chroma import Chroma


async def get_any_api_key():
    if os.environ.get("CHATBOT_OPENAI_API_KEY"):
        return os.environ.get("CHATBOT_OPENAI_API_KEY")
    doc = await db.sessions.find_one(
        {"openai_api_key": {"$exists": True, "$ne": None}}, {"openai_api_key": 1}
    )
    if doc and "openai_api_key" in doc:
        return doc["openai_api_key"]
    return None


def build_retrieverQA(api_key: str):
    prompt_template = PromptTemplate.from_template(
        """You are an assistant that summarizes chat history across sessions.

        Given the following chat excerpts:
        {context}
        Answer the user's question: {question}

        If the user asks for a summary, provide a coherent, high-level summary of the conversations in natural language.
        If the user asks a specific question, extract and answer it from the chats.
        Be detailed, accurate, and neutral."""
    )

    chroma_client = chromadb.HttpClient(
        host=Config.CHROMA_HOST,
        port=Config.CHROMA_PORT,
    )
    embeddings = OpenAIEmbeddings(api_key=api_key, model="text-embedding-3-large")
    vectorstore = Chroma(
        collection_name="chats",
        embedding_function=embeddings,
        client=chroma_client,
    )
    retriever = vectorstore.as_retriever(search_type="similarity", search_kwargs={"k": 5})
    return RetrievalQA.from_chain_type(
        llm=ChatOpenAI(api_key=api_key, model="gpt-4o"),
        retriever=retriever,
        chain_type="stuff",
        chain_type_kwargs={"prompt": prompt_template, "document_variable_name": "context"},
        return_source_documents=False,
    )