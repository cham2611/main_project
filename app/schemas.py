from pydantic import BaseModel, EmailStr
from datetime import datetime
from typing import List, Optional

# 사용자 관련 스키마
class UserCreate(BaseModel):
    email: EmailStr
    password: str
    name: str

class User(BaseModel):
    id: int
    email: str
    name: str
    created_at: datetime

    class Config:
        from_attributes = True

class UserLogin(BaseModel):
    email: EmailStr
    password: str

class UserResponse(BaseModel):
    id: int
    email: str
    name: str

    class Config:
        from_attributes = True

# 세션 관련 스키마
class SessionCreate(BaseModel):
    user_id: int

class SessionUpdate(BaseModel):
    end_time: datetime

class Session(BaseModel):
    id: int
    user_id: int
    start_time: datetime
    end_time: Optional[datetime] = None

    class Config:
        from_attributes = True

# 채팅 메시지 관련 스키마
class ChatMessageCreate(BaseModel):
    session_id: int
    message: str
    is_user: bool

class ChatMessage(BaseModel):
    id: int
    session_id: int
    user_id: int
    message: str
    is_user: bool
    timestamp: datetime

    class Config:
        from_attributes = True

class ChatHistory(BaseModel):
    messages: List[ChatMessage]