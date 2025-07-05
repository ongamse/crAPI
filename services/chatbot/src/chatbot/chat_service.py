import asyncio

from langchain_openai import ChatOpenAI
from langgraph.graph.message import Messages

from .extensions import db
from .langgraph_agent import build_langgraph_agent, execute_langgraph_agent


async def get_chat_history(session_id):
    doc = await db.chat_sessions.find_one({"session_id": session_id})
    return doc["messages"] if doc else []


async def update_chat_history(session_id, messages):
    await db.chat_sessions.update_one(
        {"session_id": session_id}, {"$set": {"messages": messages}}, upsert=True
    )


async def delete_chat_history(session_id):
    await db.chat_sessions.delete_one({"session_id": session_id})


async def process_user_message(session_id, user_message, api_key):
    history = await get_chat_history(session_id)
    history.append({"role": "user", "content": user_message})
    # Run LangGraph agent
    response = await execute_langgraph_agent(api_key, user_message, history, session_id)
    print("Response", response)
    reply: Messages = response.get("messages", [{}])[-1]
    print("Reply", reply.content)
    history.append({"role": "assistant", "content": reply.content})
    await update_chat_history(session_id, history)
    return reply.content
