from fastapi import FastAPI, Depends, HTTPException, UploadFile, File, BackgroundTasks
from sqlalchemy.orm import Session
from app import models, schemas
from app.database import get_db, engine
from passlib.context import CryptContext
from typing import List, Dict
import logging
from datetime import datetime
from app.whisper_utils import WhisperTranscriber
from pydantic import BaseModel
from openai import OpenAI
import os
from fastapi.responses import FileResponse
from app.vits2_service import VITS2Service
from app.rag_utils import RAGService
import soundfile as sf
from pathlib import Path
from fastapi.middleware.cors import CORSMiddleware

# 프로젝트 루트 디렉토리 설정
PROJECT_ROOT = Path(__file__).parent.parent
TEMP_DIR = PROJECT_ROOT / "temp"
TEMP_DIR.mkdir(exist_ok=True)

app = FastAPI()

# CORS 설정 추가
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 데밀번호 해싱 설정
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Whisper 모델 초기화 (전역 변수로 한 번만 로드)
whisper_transcriber = WhisperTranscriber()

# OpenAI 클라이언트 설정
client = OpenAI(
    api_key=os.environ["OPENAI_API_KEY"]
)

# VITS2 모델 초기화 (전역 변수로 한 번만 로드)
vits2_service = None

# RAG 서비스 변수 선언
rag_service = None

# TTS 요청 모델
class TTSRequest(BaseModel):
    text: str
    noise_scale: float = 0.667
    noise_scale_w: float = 0.8
    length_scale: float = 1.0

@app.on_event("startup")
async def startup_event():
    global vits2_service, rag_service
    try:
        vits2_service = VITS2Service()
        logger.info("VITS2 모델이 성공적으로 초기화되었습니다.")
        
        # RAG 서비스 초기화 추가
        rag_service = RAGService()
        logger.info("RAG 서비스가 성공적으로 초기화되었습니다.")
    except Exception as e:
        logger.error(f"모델 초기화 실패: {str(e)}")

# ChatGPT 요청/응답 모델 정의
class ChatRequest(BaseModel):
    message: str

class ChatResponse(BaseModel):
    response: str

# 루트 경로 핸들러
@app.get("/")
async def root():
    return {"message": "Welcome to the User Registration API"}

# 데이터베이스 연결 테스트 엔드포인트
@app.get("/test/")
def test_connection(db: Session = Depends(get_db)):
    try:
        db.execute("SELECT 1")
        return {"message": "데이터베이스 연결 성공"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"데이터베이스 연결 오류: {str(e)}")

@app.post("/signup/", response_model=schemas.User)
def signup(user: schemas.UserCreate, db: Session = Depends(get_db)):
    try:
        # 이메일 중복 체크
        db_user = db.query(models.User).filter(models.User.email == user.email).first()
        if db_user:
            raise HTTPException(status_code=400, detail="이미 등록된 이메일입니다")
        
        # 비밀번호 해싱
        hashed_password = pwd_context.hash(user.password)
        
        # 새 사용자 생성
        db_user = models.User(
            email=user.email,
            password=hashed_password,
            name=user.name
        )
        
        # 데이터베이스에 저장
        db.add(db_user)
        db.commit()
        db.refresh(db_user)
        
        return db_user
    except Exception as e:
        logger.error(f"회원가입 오류: {str(e)}")
        db.rollback()
        raise HTTPException(status_code=500, detail=f"회원가입 처리 중 오류가 발생했습니다: {str(e)}")

@app.post("/login/", response_model=schemas.UserResponse)
def login(user: schemas.UserLogin, db: Session = Depends(get_db)):
    try:
        # 사용자 확인
        db_user = db.query(models.User).filter(models.User.email == user.email).first()
        if not db_user:
            raise HTTPException(status_code=400, detail="등록되지 않은 이메일입니다")
            
        # 비밀번호 확인
        if not pwd_context.verify(user.password, db_user.password):
            raise HTTPException(status_code=400, detail="비밀번호가 일치하지 않습니다")
            
        return {
            "id": db_user.id,
            "email": db_user.email,
            "name": db_user.name
        }
    except Exception as e:
        logger.error(f"로그인 오류: {str(e)}")
        raise HTTPException(status_code=500, detail="로그인 처리 중 오류가 발생했습니다")

@app.post("/chat/sessions/start", response_model=schemas.Session)
def start_chat_session(request: schemas.SessionCreate, db: Session = Depends(get_db)):
    try:
        # 사용자 존재 여부 확인
        user = db.query(models.User).filter(models.User.id == request.user_id).first()
        if not user:
            raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다")

        # 가장 최근 세션 ID 조회
        last_session = db.query(models.Session)\
            .order_by(models.Session.id.desc())\
            .first()
        
        next_id = 1
        if last_session:
            next_id = last_session.id + 1

        # 새 세션 생성
        session = models.Session(
            id=next_id,  # 다음 ID 명시적 지정
            user_id=request.user_id,
            start_time=datetime.now()
        )
        
        db.add(session)
        db.commit()
        db.refresh(session)
        
        return session
    except Exception as e:
        logger.error(f"세션 생성 오류: {str(e)}")
        db.rollback()
        raise HTTPException(status_code=500, detail="세션 생성 중 오류가 발생했습니다")

@app.put("/chat/sessions/{session_id}/update_times", response_model=schemas.Session)
def update_session_times(session_id: int, db: Session = Depends(get_db)):
    try:
        session = db.query(models.Session).filter(models.Session.id == session_id).first()
        if not session:
            raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다")
        
        # 시작/종료 시간 갱신
        current_time = datetime.now()
        session.start_time = current_time
        session.end_time = None
        
        db.commit()
        db.refresh(session)
        return session
    except Exception as e:
        logger.error(f"세션 시간 갱신 오류: {str(e)}")
        raise HTTPException(status_code=500, detail="세션 시간 갱신 중 오류가 발생했습니다")

@app.post("/chat/sessions/{session_id}/end", response_model=schemas.Session)
def end_chat_session(session_id: int, db: Session = Depends(get_db)):
    try:
        session = db.query(models.Session).filter(models.Session.id == session_id).first()
        if not session:
            raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다")
        
        if session.end_time is None:  # 아직 종료되지 않은 세션만 업데이트
            session.end_time = datetime.now()
            db.commit()
            db.refresh(session)
        
        return session
    except Exception as e:
        logger.error(f"세션 종료 오류: {str(e)}")
        db.rollback()
        raise HTTPException(status_code=500, detail="세션 종료 중 오류가 발생했습니다")

@app.post("/chat/{user_id}/", response_model=schemas.ChatMessage)
def create_chat_message(
    user_id: int,
    message: schemas.ChatMessageCreate,
    db: Session = Depends(get_db)
):
    try:
        # 사용자 존재 여부 확인
        user = db.query(models.User).filter(models.User.id == user_id).first()
        if not user:
            raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다")

        # 세션 ID가 제공되지 않은 경우 활성 세션 확인 또는 새 세션 생성
        if not message.session_id:
            active_session = db.query(models.Session)\
                .filter(models.Session.user_id == user_id, models.Session.end_time.is_(None))\
                .first()
            
            if not active_session:
                active_session = models.Session(user_id=user_id)
                db.add(active_session)
                db.commit()
                db.refresh(active_session)
            
            message.session_id = active_session.id

        # 채팅 메시지 생성
        db_message = models.ChatHistory(
            session_id=message.session_id,
            user_id=user_id,
            message=message.message,
            is_user=message.is_user
        )
        
        db.add(db_message)
        db.commit()
        db.refresh(db_message)
        
        return db_message
    except Exception as e:
        logger.error(f"채팅 메시지 저장 오류: {str(e)}")
        db.rollback()
        raise HTTPException(status_code=500, detail="메시지 저장 중 오류가 발생했습니다")

@app.get("/chat/{user_id}/", response_model=List[schemas.ChatMessage])
def get_chat_history(
    user_id: int,
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db)
):
    try:
        # 사용자 존재 여부 확인
        user = db.query(models.User).filter(models.User.id == user_id).first()
        if not user:
            raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다")

        # 채팅 기록 조회
        messages = db.query(models.ChatHistory)\
            .filter(models.ChatHistory.user_id == user_id)\
            .order_by(models.ChatHistory.timestamp.desc())\
            .offset(skip)\
            .limit(limit)\
            .all()
            
        return messages
    except Exception as e:
        logger.error(f"채팅 기록 조회 오류: {str(e)}")
        raise HTTPException(status_code=500, detail="채팅 기록 조회 중 오류가 발생했습니다")

@app.get("/chat/sessions/{user_id}", response_model=List[schemas.Session])
def get_user_sessions(user_id: int, db: Session = Depends(get_db)):
    try:
        # 사용자 존재 여부 확인
        user = db.query(models.User).filter(models.User.id == user_id).first()
        if not user:
            raise HTTPException(status_code=404, detail="사용자를 찾을 수 없습니다")

        # 사용자의 모든 세션 조회
        sessions = db.query(models.Session)\
            .filter(models.Session.user_id == user_id)\
            .order_by(models.Session.start_time.desc())\
            .all()
            
        return sessions
    except Exception as e:
        logger.error(f"세션 조회 오류: {str(e)}")
        raise HTTPException(status_code=500, detail="세션 조회 중 오류가 발생했습니다")

@app.get("/chat/sessions/{session_id}/messages", response_model=List[schemas.ChatMessage])
def get_session_messages(session_id: int, db: Session = Depends(get_db)):
    try:
        # 세션 존재 여부 확인
        session = db.query(models.Session).filter(models.Session.id == session_id).first()
        if not session:
            raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다")

        # 세션의 모든 메시지 조회
        messages = db.query(models.ChatHistory)\
            .filter(models.ChatHistory.session_id == session_id)\
            .order_by(models.ChatHistory.timestamp.asc())\
            .all()
            
        return messages
    except Exception as e:
        logger.error(f"메시지 조회 오류: {str(e)}")
        raise HTTPException(status_code=500, detail="메시지 조회 중 오류가 발생했습니다")

@app.put("/chat/sessions/{session_id}/update_times", response_model=schemas.Session)
def update_session_times(session_id: int, db: Session = Depends(get_db)):
    try:
        session = db.query(models.Session).filter(models.Session.id == session_id).first()
        if not session:
            raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다")
        
        # 시작/종료 시간 갱신
        session.start_time = datetime.now()
        session.end_time = None
        
        db.commit()
        db.refresh(session)
        return session
    except Exception as e:
        logger.error(f"세션 시간 갱신 오류: {str(e)}")
        raise HTTPException(status_code=500, detail="세션 시간 갱신 중 오류가 발생했습니다")

# @app.post("/chat/gpt", 
#     response_model=ChatResponse,
#     tags=["chat"],
#     summary="ChatGPT와 대화",
#     description="사용자 메시지를 받아 GPT-4 응답을 반환합니다",
#     responses={
#         200: {"description": "성공적인 응답"},
#         500: {"description": "서버 오류"}
#     }
# )
# async def chat_with_gpt(request: ChatRequest) -> Dict[str, str]:
#     try:
#         response = await get_gpt4_response(request.message)
#         return {"response": response}
#     except Exception as e:
#         logger.error(f"GPT 응답 생성 중 오류: {str(e)}")
#         raise HTTPException(status_code=500, detail=f"GPT 응답 생성 중 오류: {str(e)}")

# async def get_gpt4_response(user_input: str) -> str:
#     try:
#         response = client.chat.completions.create(
#             model="gpt-4",
#             messages=[{"role": "user", "content": user_input}],
#             max_tokens=150
#         )
#         return response.choices[0].message.content.strip()
#     except Exception as e:
#         logger.error(f"GPT API 오류: {str(e)}")
#         raise HTTPException(status_code=500, detail=f"GPT API 오류: {str(e)}")

@app.post("/tts/generate",
    summary="텍스트를 음성으로 변환",
    description="입력된 텍스트를 VITS2 모델을 사용하여 음성으로 변환합니다",
    responses={
        200: {"description": "음성 파일 생성 성공"},
        500: {"description": "서버 오류"}
    }
)
async def generate_speech(request: TTSRequest, background_tasks: BackgroundTasks):
    try:
        if not vits2_service or not vits2_service.is_loaded:
            raise HTTPException(status_code=500, detail="VITS2 모델이 초기화되지 않았습니다")

        logger.info(f"TTS Request: {request.text}")
        
        # 음성 생성
        audio, sampling_rate = await vits2_service.generate_speech(
            text=request.text,
            noise_scale=request.noise_scale,
            noise_scale_w=request.noise_scale_w,
            length_scale=request.length_scale
        )

        # 임시 파일로 저장
        output_path = TEMP_DIR / f"tts_output_{os.urandom(8).hex()}.wav"
        sf.write(str(output_path), audio, sampling_rate)
        
        logger.info(f"Generated audio file: {output_path}")

        # 파일 삭제를 background task로 등록
        def cleanup_file(path: Path):
            try:
                if path.exists():
                    path.unlink()
                    logger.info(f"Cleaned up file: {path}")
            except Exception as e:
                logger.error(f"Error cleaning up file: {e}")

        background_tasks.add_task(cleanup_file, output_path)

        return FileResponse(
            path=output_path,
            media_type="audio/wav",
            filename="generated_speech.wav"
        )

    except Exception as e:
        logger.error(f"TTS error: {str(e)}")
        if 'output_path' in locals() and output_path.exists():
            try:
                output_path.unlink()
            except:
                pass
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/tts/status")
async def get_tts_status():
    """TTS 서비스 상태 확인"""
    return {
        "status": "active" if vits2_service and vits2_service.is_loaded else "inactive",
        "device": vits2_service.device if vits2_service else "not initialized"
    }

# 음성 인식 엔드포인트
@app.post("/transcribe/")
async def transcribe_audio(file: UploadFile = File(...)):
    try:
        # 파일 유효성 검사
        if not file.filename.endswith(('.mp3', '.wav', '.mp4', '.m4a')):
            raise HTTPException(status_code=400, detail="지원되지 않는 파일 형식입니다")

        # 임시 디렉토리 확인
        TEMP_DIR.mkdir(exist_ok=True)
        temp_file = TEMP_DIR / f"temp_audio_{os.urandom(8).hex()}.mp4"

        try:
            # 파일 저장
            content = await file.read()
            with temp_file.open("wb") as buffer:
                buffer.write(content)

            logger.info(f"Received audio file: {temp_file}, size: {os.path.getsize(temp_file)}")

            # Whisper로 음성 인식 (동기 함수로 호출)
            result = whisper_transcriber.transcribe_audio(str(temp_file))
            logger.info(f"Transcribed text: {result['text']}")
            
            return result

        except Exception as e:
            logger.error(f"Transcription processing error: {str(e)}")
            raise HTTPException(status_code=500, detail=f"음성 처리 중 오류: {str(e)}")

        finally:
            # 임시 파일 정리
            if temp_file.exists():
                temp_file.unlink()

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Transcription error: {str(e)}")
        raise HTTPException(status_code=500, detail=f"음성 인식 중 오류: {str(e)}")

@app.post("/chat/rag")
async def chat_with_rag(request: ChatRequest):
    try:
        if not rag_service:
            raise HTTPException(status_code=500, detail="RAG service not initialized")
            
        response = await rag_service.get_response(request.message)
        return {"response": response}
        
    except Exception as e:
        logger.error(f"RAG chat error: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e)) 