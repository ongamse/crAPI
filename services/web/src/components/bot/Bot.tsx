/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React, { useState, useEffect } from "react";
import ChatBot from "react-chatbotify";
import MarkdownRenderer from "@rcb-plugins/markdown-renderer";
import { APIService } from "../../constants/APIConstant";
import { Row, Col } from "antd";
import {
  CloseSquareOutlined,
  ExpandAltOutlined,
  WechatWorkOutlined,
} from "@ant-design/icons";
import "./chatbot.css";

const superagent = require("superagent");

interface ChatMessage {
  id: number;
  role: string;
  content: string;
}

interface ChatBotState {
  openapiKey: string | null;
  initializing: boolean;
  initializationRequired: boolean;
  accessToken: string;
  isLoggedIn: boolean;
  role: string;
  messages: ChatMessage[];
}

interface ChatBotComponentProps {
  accessToken: string;
  isLoggedIn: boolean;
  role: string;
}

const ChatBotComponent: React.FC<ChatBotComponentProps> = (props) => {
  const [expanded, setExpanded] = useState<boolean>(false);

  const [chatbotState, setChatbotState] = useState<ChatBotState>({
    openapiKey: localStorage.getItem("openapi_key"),
    initializing: false,
    initializationRequired: false,
    accessToken: props.accessToken,
    isLoggedIn: props.isLoggedIn,
    role: props.role,
    messages: [],
  });

  // Handle user messages
  const handleUserMessage = async (message: string) => {
    try {
      const chatUrl = APIService.CHATBOT_SERVICE + "genai/ask";
      console.log("Sending message to:", chatUrl);
      console.log("Message:", message);
      
      const response = await superagent
        .post(chatUrl)
        .set("Accept", "application/json")
        .set("Content-Type", "application/json")
        .set("Authorization", `Bearer ${props.accessToken}`)
        .send({ message });

      console.log("API Response:", response.body);
      
      // Check different possible response formats
      let botResponse = '';
      if (response.body.response) {
        botResponse = response.body.response;
      } else if (response.body.answer) {
        botResponse = response.body.answer;
      } else if (response.body.reply) {
        botResponse = response.body.reply;
      } else if (response.body.message) {
        botResponse = response.body.message;
      } else if (typeof response.body === 'string') {
        botResponse = response.body;
      } else {
        console.log("Unexpected response format:", response.body);
        botResponse = "I received your message but couldn't process the response format. Please try again.";
      }
      
      console.log("Bot response to render:", botResponse);
      return botResponse;
    } catch (err) {
      console.error("Error in chat API:", err);
      console.error("Error details:", (err as any).response?.body || (err as any).message);
      return "Sorry, I encountered an error. Please try again.";
    }
  };

  // React Chatbotify flow configuration
  const flow = {
    start: {
      message: "Hi, How can I help?",
      function: async (params: any) => {
        const response = await handleUserMessage(params.userInput);
        await params.injectMessage(response);
      },
      path: "chat",
    },
    chat: {
      function: async (params: any) => {
        const response = await handleUserMessage(params.userInput);
        await params.injectMessage(response);
      },
      path: "chat", // Loop back to chat for continuous conversation
    },
  };

  // React Chatbotify settings
  const settings = {
    general: {
      primaryColor: "#8b5cf6",
      secondaryColor: "#a855f7",
      fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
    },
    chatHistory: {
      storageKey: "crapi_chat_history"
    },
    chatInput: {
      placeholder: "Type your message here...",
      enabledPlaceholderText: "Type your message here...",
      showCharacterCount: false,
      allowNewlines: true,
      sendButtonStyle: {
        background: "#10b981",
      }
    },
    header: {
      title: (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
          <span style={{ fontSize: '18px', fontWeight: '600', color: '#1f2937' }}>
            CrAPI Chatbot
          </span>
          <button
            className="expand-chatbot-btn"
            onClick={() => setExpanded((prev) => !prev)}
            aria-label={expanded ? "Collapse Chatbot" : "Expand Chatbot"}
            title={expanded ? "Collapse Chatbot" : "Expand Chatbot"}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = 'linear-gradient(135deg, #e5e7eb, #d1d5db)';
              e.currentTarget.style.transform = 'scale(1.05)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'linear-gradient(135deg, #f3f4f6, #e5e7eb)';
              e.currentTarget.style.transform = 'scale(1)';
            }}
          >
            <ExpandAltOutlined />
          </button>
        </div>
      ),
    },
    notification: {
      disabled: true,
    },
    audio: {
      disabled: true,
    },
    chatButton: {
      icon: "💬",
    },
    botBubble: {
      animate: true,
      showAvatar: true,
      avatar: "🤖",
    },
    userBubble: {
      animate: true,
      showAvatar: true,
      avatar: "👤",
    },
  };

  // Initialize chat history
  useEffect(() => {
    const fetchInit = async () => {
      try {
        const stateUrl = APIService.CHATBOT_SERVICE + "genai/state";
        const response = await superagent
          .get(stateUrl)
          .set("Accept", "application/json")
          .set("Content-Type", "application/json")
          .set("Authorization", `Bearer ${props.accessToken}`);

        const { init_required, chat_history } = response.body;
        
        setChatbotState({
          ...chatbotState,
          initializationRequired: init_required,
          messages: chat_history || [],
          initializing: false,
        });
      } catch (err) {
        console.error("Error fetching chat state:", err);
      }
    };

    if (props.accessToken && props.isLoggedIn) {
      fetchInit();
    }
  }, [props.accessToken, props.isLoggedIn]);

  return (
    <Row>
      <Col xs={10}>
        <div className={`app-chatbot-container${expanded ? " expanded" : ""}`}>
          <ChatBot
              flow={flow}
              plugins={[MarkdownRenderer()]}
              settings={settings}
              styles={{
                chatWindowStyle: {
                  width: expanded ? "max(50vw, 500px)" : "420px",
                  height: expanded ? "90vh" : "70vh",
                  borderRadius: "16px",
                  boxShadow: expanded 
                    ? "0 20px 60px rgba(0, 0, 0, 0.2)" 
                    : "0 20px 40px rgba(0, 0, 0, 0.1)",
                  border: "1px solid #e5e7eb",
                  background: "#ffffff",
                },
                chatInputAreaStyle: {
                  padding: "20px 24px",
                  background: "#ffffff",
                  borderTop: "1px solid #f3f4f6",
                },
                sendButtonStyle: {
                  background: "#10b981",
                  width: "44px",
                  height: "44px",
                  borderRadius: "50%",
                  marginLeft: "12px",
                  border: "none",
                  color: "#ffffff",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                },
                botBubbleStyle: {
                  background: "#f3f4f6",
                  color: "#374151",
                  borderRadius: "16px",
                  padding: "12px 16px",
                  margin: "8px 0",
                  fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
                  fontSize: "14px",
                  lineHeight: "1.4",
                },
                userBubbleStyle: {
                  background: "#8b5cf6",
                  color: "#ffffff",
                  borderRadius: "16px",
                  padding: "12px 16px",
                  margin: "8px 0",
                  fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
                  fontSize: "14px",
                  lineHeight: "1.4",
                },
                fileAttachmentButtonDisabledStyle: {
                  display: "none",
                },
              }}
            />
        </div>
      </Col>
    </Row>
  );
};

export default ChatBotComponent;