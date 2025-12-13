"""FastAPI service that powers the ClinicSystem preliminary diagnosis feature.

The service loads the trained RandomForest model and exposes a /predict endpoint
that receives binary symptoms, estimates the likelihood of several diseases, and
recommends whether the user should schedule an appointment.
"""

from __future__ import annotations

import logging
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

from pathlib import Path
from typing import Any, Dict, List

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException, Request
import json

from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator, model_validator

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / "diagnosis_model.pkl"
LABEL_ENCODER_PATH = BASE_DIR / "label_encoder.pkl"
FEATURE_NAMES_PATH = BASE_DIR / "feature_names.pkl"

logger = logging.getLogger("diagnosis_api")


app = FastAPI(
    title="ClinicSystem Diagnosis Assistant",
    description=(
        "Dịch vụ AI gợi ý bệnh dựa trên các triệu chứng dạng 0/1. "
        "Gợi ý chỉ mang tính tham khảo, không thay thế bác sĩ chuyên môn."
    ),
)


@app.middleware("http")
async def log_raw_request(request: Request, call_next):
    body = await request.body()
    logger.info("Incoming %s %s headers=%s body=%s", request.method, request.url, dict(request.headers), body)

    async def receive():
        return {"type": "http.request", "body": body, "more_body": False}

    request._receive = receive  # type: ignore[attr-defined]
    response = await call_next(request)
    return response


def load_artifact(path: Path):
    if not path.exists():
        raise RuntimeError(
            f"Required artifact '{path.name}' was not found. "
            "Please run train_diagnosis_model.py before starting the API."
        )
    return joblib.load(path)


MODEL = load_artifact(MODEL_PATH)
LABEL_ENCODER = load_artifact(LABEL_ENCODER_PATH)
FEATURE_NAMES: List[str] = load_artifact(FEATURE_NAMES_PATH)
FEATURE_INDEX = {name.lower(): idx for idx, name in enumerate(FEATURE_NAMES)}

SEVERITY_HIGH = {
    "myocardial infarction",
    "heart failure",
    "stroke",
    "pulmonary embolism",
    "sepsis",
    "pulmonary edema",
    "severe asthma",
    "gastric hemorrhage",
}

SEVERITY_MEDIUM = {
    "pneumonia",
    "appendicitis",
    "kidney stones",
    "kidney infection",
    "bronchitis",
    "dengue",
    "diabetes type 2",
    "hypertension",
}


class DiagnosisRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    symptoms: List[str] = Field(
        default_factory=list,
        description="Tên triệu chứng trùng với các cột trong tập dữ liệu.",
    )
    top_k: int = Field(
        default=5,
        ge=1,
        le=25,
        description="Số lượng gợi ý tối đa muốn nhận.",
    )

    @model_validator(mode="before")
    @classmethod
    def support_topk_alias(cls, data):
        if isinstance(data, dict) and "topK" in data and "top_k" not in data:
            data["top_k"] = data.pop("topK")
        return data

    @field_validator("symptoms", mode="after")
    @classmethod
    def normalize_symptoms(cls, value: List[str]) -> List[str]:
        return [item.strip() for item in value if isinstance(item, str) and item.strip()]


class DiseasePrediction(BaseModel):
    disease: str
    probability: float
    severity: str
    should_book_appointment: bool


class DiagnosisResponse(BaseModel):
    predictions: List[DiseasePrediction]


def build_feature_vector(symptoms: List[str]) -> pd.DataFrame:
    """Build feature vector as DataFrame to match training format."""
    vector = np.zeros(len(FEATURE_NAMES), dtype=np.float32)
    for symptom in symptoms:
        idx = FEATURE_INDEX.get(symptom.lower())
        if idx is not None:
            vector[idx] = 1.0
    # Return as DataFrame with column names to match training
    return pd.DataFrame([vector], columns=FEATURE_NAMES)


def determine_severity(disease: str, probability: float) -> str:
    key = disease.lower()
    if key in SEVERITY_HIGH:
        return "nặng"
    if key in SEVERITY_MEDIUM:
        return "trung bình"
    # Heuristic: elevate default severity when probability is high
    return "trung bình" if probability >= 0.45 else "nhẹ"


def should_book_visit(severity: str, probability: float) -> bool:
    if severity == "nặng" and probability >= 0.3:
        return True
    if severity == "trung bình" and probability >= 0.6:
        return True
    return False


from disease_mapping import DISEASE_MAPPING

@app.post("/predict", response_model=DiagnosisResponse)
async def predict(request: Request) -> DiagnosisResponse:
    body_bytes = await request.body()
    # print("RAW BODY:", body_bytes) # Reduced logging
    if not body_bytes:
        raise HTTPException(
            status_code=400,
            detail="Payload không hợp lệ, vui lòng gửi JSON đúng định dạng.",
        )
    # logger.info("Raw request body bytes: %s", body_bytes)
    try:
        raw_payload: Any = json.loads(body_bytes)
    except Exception as exc:
        try:
            raw_text = body_bytes.decode("utf-8", errors="ignore")
            raw_payload = json.loads(raw_text)
        except Exception:
            raise HTTPException(
                status_code=400,
                detail="Payload không hợp lệ, vui lòng gửi JSON đúng định dạng.",
            ) from exc

    if isinstance(raw_payload, list):
        if len(raw_payload) == 1 and isinstance(raw_payload[0], dict):
            raw_payload = raw_payload[0]
        else:
            raise HTTPException(
                status_code=400,
                detail="Payload không hợp lệ, vui lòng gửi JSON object thay vì array.",
            )

    if isinstance(raw_payload, dict) and "topK" in raw_payload and "top_k" not in raw_payload:
        raw_payload["top_k"] = raw_payload.pop("topK")

    try:
        diagnosis_req = DiagnosisRequest(**raw_payload)
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail=exc.errors()) from exc

    if not diagnosis_req.symptoms:
        raise HTTPException(
            status_code=400,
            detail="Vui lòng cung cấp ít nhất một triệu chứng để hệ thống có dữ liệu phân tích.",
        )

    feature_vector = build_feature_vector(diagnosis_req.symptoms)
    # Check if DataFrame contains any non-zero values
    if not feature_vector.values.any():
        raise HTTPException(
            status_code=400,
            detail=(
                "Không nhận diện được triệu chứng nào trùng với dữ liệu mô hình. "
                "Vui lòng thử lại với các triệu chứng khác."
            ),
        )

    try:
        # feature_vector is already a DataFrame with shape (1, 164) and column names
        probabilities = MODEL.predict_proba(feature_vector)[0]
    except Exception as exc:
        raise HTTPException(status_code=500, detail="Không thể chạy mô hình AI, vui lòng thử lại sau.") from exc

    # Lấy nhiều hơn top_k một chút để phòng trường hợp gộp nhóm bị giảm số lượng
    fetch_k = min(diagnosis_req.top_k * 2, len(probabilities))
    top_indices = np.argsort(probabilities)[::-1][:fetch_k]
    
    # Debug: Log top 10 predictions before filtering
    logger.info("Top 10 raw predictions:")
    for i, idx in enumerate(top_indices[:10], 1):
        disease = LABEL_ENCODER.inverse_transform([idx])[0]
        prob = float(probabilities[idx])
        logger.info(f"  {i}. {disease}: {prob:.4f}")

    final_predictions: List[Dict[str, object]] = []
    seen_groups = set()

    for idx in top_indices:
        original_name = LABEL_ENCODER.inverse_transform([idx])[0]
        prob = float(probabilities[idx])
        
        # Tra cứu trong từ điển (bao gồm cả dịch và gom nhóm)
        key = original_name.lower()
        mapping_info = DISEASE_MAPPING.get(key)
        
        if mapping_info:
            display_name = mapping_info.get("name_vi", original_name)
            # Ưu tiên severity trong map, nếu không thì tính toán lại
            severity = mapping_info.get("severity") or determine_severity(original_name, prob)
            warning = mapping_info.get("warning")
        else:
            # Fallback: Giữ nguyên tên gốc (Title Case)
            display_name = original_name.title()
            severity = determine_severity(original_name, prob)
            warning = None
        
        # Deduplication (Gộp trùng dựa trên tên hiển thị)
        if display_name in seen_groups:
            continue
            
        seen_groups.add(display_name)
        
        # Cảnh báo độ tin cậy thấp - giảm xuống 1% để có kết quả hữu ích
        if prob < 0.01:
             continue

        pred_entry = {
            "disease": display_name,
            "probability": round(prob, 6),
            "severity": severity,
            "should_book_appointment": should_book_visit(severity, prob),
        }
        
        # Nếu có cảnh báo đặc thù, nối vào tên bệnh hoặc trả về trường riêng (tuỳ FE)
        # Ở đây mình nối vào tên bệnh để hiển thị ngay
        if warning:
             pred_entry["disease"] = f"{display_name} ({warning})"
             
        final_predictions.append(pred_entry)
        
        if len(final_predictions) >= diagnosis_req.top_k:
            break

    logger.info(f"Returning {len(final_predictions)} predictions to client")
    return DiagnosisResponse(predictions=final_predictions)


@app.get("/health")
def health() -> Dict[str, str]:
    return {"status": "ok"}


@app.get("/symptoms")
def list_symptoms() -> List[str]:
    """Return list of symptom names supported by the model."""
    try:
        return FEATURE_NAMES
    except Exception as exc:  # pragma: no cover - defensive
        logger.error("Cannot load feature names: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail="Không thể đọc danh sách triệu chứng từ mô hình.") from exc


# Quick start:
# 1. python train_diagnosis_model.py
# 2. uvicorn diagnosis_api:app --host 0.0.0.0 --port 8001
