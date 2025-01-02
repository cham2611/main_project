import os
import sys
from pathlib import Path

# espeak 경로 설정 수정
ESPEAK_PATH = "C:/Program Files/eSpeak NG"  # eSpeak NG 경로
ESPEAK_BIN_PATH = os.path.join(ESPEAK_PATH, "bin")  # bin 폴더 추가

# 두 경로 모두 환경 변수에 추가
for path in [ESPEAK_PATH, ESPEAK_BIN_PATH]:
    if path not in os.environ['PATH']:
        os.environ['PATH'] = path + os.pathsep + os.environ['PATH']

# espeak 설치 확인
try:
    import subprocess
    result = subprocess.run(['espeak-ng', '--version'], 
                          capture_output=True, 
                          text=True)
    if result.returncode == 0:
        print("eSpeak NG가 정상적으로 감지되었습니다.")
except Exception as e:
    print(f"eSpeak NG 확인 중 오류 발생: {str(e)}")

# vits2_pytorch_base 경로를 Python 경로에 추가
VITS2_BASE_PATH = Path("C:/main_project/vits2_pytorch_base")
if str(VITS2_BASE_PATH) not in sys.path:
    sys.path.append(str(VITS2_BASE_PATH))

# 이제 vits2_pytorch_base의 모듈들을 임포트할 수 있습니다
import torch
import commons
import utils
from models import SynthesizerTrn
from text.symbols import symbols
from text import text_to_sequence
import numpy as np
import os

class VITS2Service:
    def __init__(self):
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.model = None
        self.hps = None
        self.base_path = VITS2_BASE_PATH  # 경로 수정
        self._load_model()

    def _load_model(self):
        try:
            # 모델 파일 경로 설정
            config_path = self.base_path / "logs" / "kss" / "config.json"
            model_path = self.base_path / "logs" / "kss" / "G_200000.pth"
            
            # 파일 존재 여부 확인
            if not config_path.exists():
                raise FileNotFoundError(f"설정 파일을 찾을 수 없습니다: {config_path}")
            if not model_path.exists():
                raise FileNotFoundError(f"모델 파일을 찾을 수 없습니다: {model_path}")
            
            # 설정 로드
            self.hps = utils.get_hparams_from_file(str(config_path))
            
            # posterior channels 설정
            if hasattr(self.hps.model, 'use_mel_posterior_encoder') and self.hps.model.use_mel_posterior_encoder:
                posterior_channels = 80
            else:
                posterior_channels = self.hps.data.filter_length // 2 + 1

            # 모델 초기화
            self.model = SynthesizerTrn(
                len(symbols),
                posterior_channels,
                self.hps.train.segment_size // self.hps.data.hop_length,
                **self.hps.model.__dict__
            ).to(self.device)
            
            # 모델을 평가 모드로 설정하고 가중치 로드
            self.model.eval()
            utils.load_checkpoint(str(model_path), self.model, None)
            
            print(f"모델이 성공적으로 로드되었습니다. 사용 중인 디바이스: {self.device}")
            
        except Exception as e:
            raise Exception(f"모델 로드 중 오류 발생: {str(e)}")

    def _get_text(self, text):
        try:
            text_norm = text_to_sequence(text, self.hps.data.text_cleaners)
            if self.hps.data.add_blank:
                text_norm = commons.intersperse(text_norm, 0)
            text_norm = torch.LongTensor(text_norm)
            return text_norm
        except Exception as e:
            raise Exception(f"텍스트 처리 중 오류 발생: {str(e)}")

    async def generate_speech(
        self,
        text: str,
        noise_scale: float = 0.667,
        noise_scale_w: float = 0.8,
        length_scale: float = 1.0
    ):
        try:
            if self.model is None:
                raise Exception("모델이 초기화되지 않았습니다")

            stn_tst = self._get_text(text)
            with torch.no_grad():
                x_tst = stn_tst.to(self.device).unsqueeze(0)
                x_tst_lengths = torch.LongTensor([stn_tst.size(0)]).to(self.device)
                
                audio = self.model.infer(
                    x_tst,
                    x_tst_lengths,
                    noise_scale=noise_scale,
                    noise_scale_w=noise_scale_w,
                    length_scale=length_scale
                )[0][0,0].data.cpu().float().numpy()

            return audio, self.hps.data.sampling_rate
            
        except Exception as e:
            raise Exception(f"음성 생성 중 오류 발생: {str(e)}")

    @property
    def is_loaded(self):
        """모델 로드 상태 확인"""
        return self.model is not None