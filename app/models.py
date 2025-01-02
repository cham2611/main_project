from sqlalchemy import Column, Integer, String, TIMESTAMP, Text, Boolean, ForeignKey, text
from sqlalchemy.orm import relationship
from app.database import Base

class User(Base):
    __tablename__ = "user"

    id = Column(Integer, primary_key=True, autoincrement=True)
    email = Column(String(100), nullable=False)
    password = Column(String(100), nullable=False)
    name = Column(String(50), nullable=False)
    created_at = Column(TIMESTAMP, nullable=False, 
                       server_default=text('CURRENT_TIMESTAMP'))
    
    # 관계 설정
    sessions = relationship("Session", back_populates="user")
    chat_histories = relationship("ChatHistory", back_populates="user")

class Session(Base):
    __tablename__ = "session"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey('user.id', ondelete='CASCADE'), nullable=False)
    start_time = Column(TIMESTAMP, nullable=False,
                       server_default=text('CURRENT_TIMESTAMP'))
    end_time = Column(TIMESTAMP, nullable=True)
    
    # 관계 설정
    user = relationship("User", back_populates="sessions")
    chat_histories = relationship("ChatHistory", back_populates="session")

class ChatHistory(Base):
    __tablename__ = "chat_history"

    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(Integer, ForeignKey('session.id', ondelete='CASCADE'), nullable=False)
    user_id = Column(Integer, ForeignKey('user.id', ondelete='CASCADE'), nullable=False)
    message = Column(Text, nullable=False)
    is_user = Column(Boolean, nullable=False)
    timestamp = Column(TIMESTAMP, nullable=False,
                      server_default=text('CURRENT_TIMESTAMP'))
    
    # 관계 설정
    session = relationship("Session", back_populates="chat_histories")
    user = relationship("User", back_populates="chat_histories")