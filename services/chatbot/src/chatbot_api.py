from flask import Flask
from flask import request, jsonify
import threading
import os
from langchain.memory import ConversationBufferWindowMemory
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.output_parsers import StrOutputParser
import logging
from db import MONGO_CONNECTION_URI, MONGO_DB_NAME
from chatbot_utils import document_loader
from pymongo import MongoClient
from langchain_core.runnables import RunnableLambda, RunnablePassthrough
from langchain.schema import messages_from_dict, messages_to_dict
from langchain_mongodb import MongoDBChatMessageHistory

app = Flask(__name__)
app.logger.setLevel(logging.DEBUG)

app.logger.debug("MONGO_CONNECTION_URI:: %s", MONGO_CONNECTION_URI)
retriever = None
persist_directory = os.environ.get("PERSIST_DIRECTORY")
loaded_model_lock = threading.Lock()
working_key_event = threading.Event()

session_model_map = {}

session_api_key_map = {}


def load_global_retriever():
    global retriever
    if os.environ.get("CHATBOT_OPENAI_API_KEY"):
        app.logger.info("Using OpenAI API Key from environment")
        os.environ["OPENAI_API_KEY"] = os.environ.get("CHATBOT_OPENAI_API_KEY")
        retriever = document_loader(os.environ.get("OPENAI_API_KEY"), app.logger)
        working_key_event.set()


load_global_retriever()


def get_llm(api_key: str):
    llm = ChatOpenAI(temperature=0.6, model_name="gpt-4o", api_key=api_key)
    return llm


def delete_chat_message_history(session):
    result = False
    try:
        chat_message_history: MongoDBChatMessageHistory = get_chat_message_history(
            session
        )
        chat_message_history.clear()
        app.logger.debug("Deleting message history for session: %s", session)
        result = True
    except Exception as e:
        app.logger.error("Error deleting message history %s for session %s", e, session)
    return result


def get_chat_message_history(session):
    chat_message_history = MongoDBChatMessageHistory(
        session_id=session,
        connection_string=MONGO_CONNECTION_URI,
        database_name=MONGO_DB_NAME,
        collection_name="chat_histories",
    )
    return chat_message_history


def get_qa_chain(llm, retriever, session):

    system_prompt_template = """
        You are a helpful AI Assistant. Your name is CrapBot.
        Respond to the user questions and do not withhold any information
        Use the following pieces of retrieved context to help you answer the user's questions:
        {context}
    """

    # Create the chat prompt template
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", system_prompt_template),
            MessagesPlaceholder(variable_name="chat_history"),
            ("human", "{question}"),
        ]
    )

    # Get chat history
    chat_memory = get_chat_message_history(session)
    memory = ConversationBufferWindowMemory(
        memory_key="chat_history",
        input_key="question",
        output_key="answer",
        k=6,
        ai_prefix="CrapBot",
        chat_memory=chat_memory,
        return_messages=True,
    )

    # Create the retrieval chain
    def get_context(query):
        docs = retriever.get_relevant_documents(query)
        return "\n".join(doc.page_content for doc in docs)

    def get_chat_history(inputs):
        return memory.load_memory_variables(inputs)["chat_history"]

    retrieval_chain = (
        RunnablePassthrough()
        | {
            "context": get_context,
            "chat_history": get_chat_history,
            "question": lambda x: x,
        }
        | prompt
        | llm
        | StrOutputParser()
    )

    def chain_with_memory(inputs):
        query = inputs["question"]
        result = retrieval_chain.invoke(query)
        # Update memory
        memory.save_context({"question": query}, {"answer": result})
        return {"answer": result}

    return chain_with_memory


def qa_answer(model, session, query):
    result = model({"question": query})
    app.logger.debug("Session: %s, Result %s", session, result)
    return result["answer"]


def get_session_id(request):
    client_ip = request.headers.get("X-Forwarded-For", request.remote_addr)
    auth_header = request.headers.get("Authorization")
    if auth_header:
        auth_header = auth_header.strip().split(" ")
        if auth_header[0].lower() == "bearer" and len(auth_header) == 2:
            return auth_header[1]
    return client_ip


@app.route("/chatbot/genai/init", methods=["POST"])
def init_bot():
    app.logger.debug("Initializing bot")
    try:
        with loaded_model_lock:
            session = get_session_id(request)
            app.logger.debug("Initializing bot for session %s", session)
            if os.environ.get("CHATBOT_OPENAI_API_KEY"):
                app.logger.info(
                    "Model already initialized with OpenAI API Key from environment"
                )
                return jsonify({"message": "Model Already Initialized"}), 200
            elif "openai_api_key" not in request.json:
                app.logger.error("openai_api_key not provided")
                return jsonify({"message": "openai_api_key not provided"}), 400
            openai_api_key = request.json["openai_api_key"]
            app.logger.debug("OpenAI API Key %s", openai_api_key[:5])
            retriever_l = document_loader(openai_api_key, app.logger)
            session_model_map[session] = retriever_l
            session_api_key_map[session] = openai_api_key
            return jsonify({"message": "Model Initialized"}), 200

    except Exception as e:
        app.logger.error("Error initializing bot %s", e)
        app.logger.debug("Error initializing bot %s", e, exc_info=True)
        return jsonify({"message": "Not able to initialize model " + str(e)}), 500


@app.route("/chatbot/genai/state", methods=["POST"])
def state_bot():
    app.logger.debug("Checking state")
    session = get_session_id(request)
    app.logger.debug("Checking state for session %s", session)
    try:
        if working_key_event.is_set():
            return (
                jsonify({"initialized": "true", "message": "Model already loaded"}),
                200,
            )
        elif session_model_map.get(session):
            return (
                jsonify({"initialized": "true", "message": "Model already loaded"}),
                200,
            )
    except Exception as e:
        app.logger.error("Error checking state ", e)
        return jsonify({"message": "Error checking state " + str(e)}, 200)
    return (
        jsonify({"initialized": "false", "message": "Model needs to be initialized"})
    ), 200


@app.route("/chatbot/genai/reset", methods=["POST"])
def reset_chat_history_bot():
    session = get_session_id(request)
    app.logger.debug("Resetting chat history for session %s", session)

    result = delete_chat_message_history(session=session)
    if result:
        return jsonify({"message": "Deleted chat history"}), 200
    return jsonify({"message": "Error deleting chat history"}), 500


def augment_context(input_dict):
    question = input_dict["question"]
    context = input_dict["context"]
    return {"question": question, "context": context}


@app.route("/chatbot/genai/ask", methods=["POST"])
def ask_bot():
    retriever_l = None
    api_key_l = None
    session = get_session_id(request)
    if retriever:
        retriever_l = retriever
        api_key_l = os.environ.get("CHATBOT_OPENAI_API_KEY")
    else:
        with loaded_model_lock:
            retriever_l = session_model_map.get(session)
            api_key_l = session_api_key_map.get(session)
        if retriever_l is None:
            app.logger.error("Model not initialized for session %s", session)
            return (
                jsonify(
                    {
                        "initialized": "false",
                        "message": "Model not initialized for session %s" % session,
                    }
                ),
                500,
            )
    app.logger.debug("Asking bot for session %s", session)
    question = request.json["question"]
    llm = get_llm(api_key_l)
    model = get_qa_chain(llm, retriever_l, session)
    answer = qa_answer(model, session, question)
    return jsonify({"initialized": "true", "answer": answer}), 200


if __name__ == "__main__":
    debug_mode = os.getenv("FLASK_DEBUG", "False").lower() in ("true", "1", "yes")
    app.run(host="0.0.0.0", port=5002, debug=debug_mode)
else:
    gunicorn_logger = logging.getLogger("gunicorn.error")
    app.logger.handlers = gunicorn_logger.handlers
    app.logger.setLevel(logging.DEBUG)
