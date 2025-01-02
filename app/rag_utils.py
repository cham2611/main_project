from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.vectorstores import FAISS
from langchain.chains import ConversationalRetrievalChain
from langchain.memory import ConversationBufferMemory
from langchain.prompts import PromptTemplate
import pandas as pd
import logging
import os
from pathlib import Path

logger = logging.getLogger(__name__)

PROMPT_TEMPLATE = """
당신은 따뜻하고 공감적인 진로상담 선생님입니다.
답변의 말투는 오은영 선생님의 말투로 해주세요.
예를 들어, '하세요.' 하는 단정적 표현 보다는 '~하면 좋을거 같아요.' 하는 권유적 표현으로 대답해주세요.

상담 시 주의사항:
1. 대화 맥락 유지
2. 탐색적 대화
3. 공감과 인정을 먼저

상담 데이터: {context}
학생의 이야기: {question}
진로상담 선생님의 답변:"""

class RAGService:
    def __init__(self):
        self.vectorstore = None
        self.chat_chain = None
        self.initialize_rag()

    def load_csv_files(self):
        """CSV 파일들을 로드하고 구조화된 형태로 변환"""
        documents = []
        csv_files = ['training_data.csv', 'validation_data.csv']
        
        for file in csv_files:
            file_path = Path("app/data") / file
            if file_path.exists():
                df = pd.read_csv(file_path)
                for _, row in df.iterrows():
                    text = ""
                    for column in df.columns:
                        text += f"{column}: {row[column]}\n"
                    documents.append(text)
            else:
                logger.warning(f"파일을 찾을 수 없음: {file}")
        
        return documents

    def create_vectorstore(self):
        """벡터 저장소 생성"""
        try:
            embeddings = OpenAIEmbeddings(
                model="text-embedding-3-large",
                dimensions=1024
            )
            
            documents = self.load_csv_files()
            
            text_splitter = RecursiveCharacterTextSplitter(
                chunk_size=1000,
                chunk_overlap=200,
                length_function=len,
                separators=["\n\n", "\n", " ", ""]
            )
            
            split_docs = text_splitter.split_documents(documents)
            
            self.vectorstore = FAISS.from_documents(
                split_docs,
                embeddings,
                distance_strategy="cosine"
            )
            
            # 저장
            index_path = Path("app/data/faiss_index")
            index_path.parent.mkdir(parents=True, exist_ok=True)
            self.vectorstore.save_local(str(index_path))
            
        except Exception as e:
            logger.error(f"Vector store creation error: {str(e)}")
            raise

    def initialize_rag(self):
        try:
            embeddings = OpenAIEmbeddings()
            index_path = Path("app/data/faiss_index")
            
            if index_path.exists():
                self.vectorstore = FAISS.load_local(
                    str(index_path),
                    embeddings,
                    allow_dangerous_deserialization=True
                )
                logger.info("Vector store loaded successfully")
            else:
                logger.info("Creating new vector store...")
                self.create_vectorstore()
                
            self.create_chat_chain()
            
        except Exception as e:
            logger.error(f"RAG initialization error: {str(e)}")
            raise

    def create_chat_chain(self):
        try:
            llm = ChatOpenAI(
                temperature=0.7,
                model_name="gpt-4",
                verbose=True
            )
            
            memory = ConversationBufferMemory(
                memory_key="chat_history",
                return_messages=True,
                output_key="answer"
            )
            
            self.chat_chain = ConversationalRetrievalChain.from_llm(
                llm=llm,
                retriever=self.vectorstore.as_retriever(
                    search_kwargs={"k": 3}
                ),
                memory=memory,
                verbose=True,
                combine_docs_chain_kwargs={
                    "prompt": PromptTemplate(
                        input_variables=["context", "question"],
                        template=PROMPT_TEMPLATE
                    )
                }
            )
            
        except Exception as e:
            logger.error(f"Chat chain creation error: {str(e)}")
            raise

    async def get_response(self, message: str) -> str:
        try:
            if not self.chat_chain:
                raise ValueError("Chat chain not initialized")
                
            response = self.chat_chain.invoke({
                "question": message
            })
            
            return response['answer']
            
        except Exception as e:
            logger.error(f"Response generation error: {str(e)}")
            raise 