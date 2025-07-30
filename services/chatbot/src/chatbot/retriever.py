from langchain.agents.agent_toolkits import create_retriever_tool
from .config import Config
import chromadb
from uuid import uuid4
from langchain_core.documents import Document
from chromadb.utils.embedding_functions import OpenAIEmbeddingFunction
from langchain_openai import OpenAIEmbeddings
from langchain_chroma import Chroma

async def get_collection(api_key):
    chroma_client = await chromadb.AsyncHttpClient(
        host=Config.CHROMA_HOST,
        port=Config.CHROMA_PORT,
    )
    embeddings = OpenAIEmbeddingFunction(api_key=api_key, model_name="text-embedding-3-large")
    collection = await chroma_client.get_or_create_collection(
        name="chats",
        embedding_function=embeddings,
    )
    return collection

async def update_collection(api_key, session_id, new_messages):
    docs = []
    ids = []
    for role, content in new_messages.items():
        if content:
            doc_id = str(uuid4())
            doc = Document(
                page_content=content,
                metadata={"session_id": session_id, "role": role}
            )
            docs.append(doc)
            ids.append(doc_id)

    if docs:
        collection = await get_collection(api_key)
        await collection.add(
            documents=[d.page_content for d in docs],
            metadatas=[d.metadata for d in docs],
            ids=ids
        )

def get_retriever_tool(api_key):
    embeddings = OpenAIEmbeddings(api_key=api_key, model="text-embedding-3-large")
    chroma_client = chromadb.HttpClient(
        host=Config.CHROMA_HOST,
        port=Config.CHROMA_PORT,
    )
    collection = Chroma(
        collection_name="chats",
        embedding_function=embeddings,
        client=chroma_client,
    )
    retriever = collection.as_retriever(search_type="similarity", search_kwargs={"k": 5})
    return create_retriever_tool(
        retriever=retriever,
        name="retriever_tool",
        description="""
        Answer questions based on user chat history (summarized and semantically indexed). 
        Use this when the user asks about prior chats, what they asked earlier, or wants a summary of past conversations.    
        Answer questions based on the user's prior chat history.

        Use this tool when the user refers to anything mentioned before, asks for a summary of previous messages or sessions, 
        or references phrases like 'what I said earlier', 'things we discussed', 'my earlier question', 'until now', 'till date', 'all my conversations' or 'previously mentioned'.
        The chat history is semantically indexed and summarized using vector search.
        """,
    )
