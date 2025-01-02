import torch
from transformers import AutoModelForSpeechSeq2Seq, AutoProcessor, pipeline
from pathlib import Path
import tempfile
import os
import logging
import subprocess

logger = logging.getLogger(__name__)

class WhisperTranscriber:
    def __init__(self):
        try:
            # FFmpeg 경로를 직접 지정 (bin 폴더까지만)
            ffmpeg_path = r"C:\ffmpeg-2024-12-19-git-494c961379-essentials_build\ffmpeg-2024-12-19-git-494c961379-essentials_build\bin"
            if not os.path.exists(ffmpeg_path):
                raise RuntimeError("FFmpeg 경로를 찾을 수 없습니다.")
            
            # 환경 변수에 FFmpeg 경로 추가
            if ffmpeg_path not in os.environ["PATH"]:
                os.environ["PATH"] = ffmpeg_path + os.pathsep + os.environ["PATH"]
            
            self.device = "cuda:0" if torch.cuda.is_available() else "cpu"
            self.torch_dtype = torch.float16 if torch.cuda.is_available() else torch.float32
            
            # 모델 초기화
            self.model_id = "openai/whisper-large-v3-turbo"
            
            self.model = AutoModelForSpeechSeq2Seq.from_pretrained(
                self.model_id, 
                torch_dtype=self.torch_dtype, 
                low_cpu_mem_usage=True, 
                use_safetensors=True
            )
            self.model.to(self.device)
            
            self.processor = AutoProcessor.from_pretrained(self.model_id)
            
            self.pipe = pipeline(
                "automatic-speech-recognition",
                model=self.model,
                tokenizer=self.processor.tokenizer,
                feature_extractor=self.processor.feature_extractor,
                torch_dtype=self.torch_dtype,
                device=self.device,
            )
            
            logger.info("Whisper 모델이 성공적으로 로드되었습니다.")
        except Exception as e:
            logger.error(f"초기화 오류: {str(e)}")
            raise

    def _get_ffmpeg_path(self):
        try:
            # FFmpeg 경로를 직접 지정
            ffmpeg_path = r"C:\ffmpeg-2024-12-19-git-494c961379-essentials_build\ffmpeg-2024-12-19-git-494c961379-essentials_build\bin"  # 실제 FFmpeg 설치 경로로 수정하세요
            if os.path.exists(ffmpeg_path):
                return ffmpeg_path
            
            # 기존 코드는 그대로 유지
            if os.name == 'nt':
                result = subprocess.run(['where', 'ffmpeg'], 
                                     capture_output=True, 
                                     text=True)
                if result.returncode == 0:
                    return result.stdout.strip().split('\n')[0]
            else:
                result = subprocess.run(['which', 'ffmpeg'], 
                                     capture_output=True, 
                                     text=True)
                if result.returncode == 0:
                    return result.stdout.strip()
            return None
        except Exception as e:
            logger.error(f"FFmpeg 경로 찾기 실패: {str(e)}")
            return None

    def transcribe_audio(self, audio_path: str) -> dict:
        """오디오 파일을 텍스트로 변환"""
        try:
            # 음성 인식 실행
            result = self.pipe(audio_path)
            
            if not result or "text" not in result:
                raise ValueError("음성 인식 결과가 유효하지 않습니다")
                
            return {"text": result["text"].strip()}
            
        except Exception as e:
            logger.error(f"음성 인식 처리 중 오류: {str(e)}")
            raise RuntimeError(f"음성 인식 처리 중 오류 발생: {str(e)}")