# AI Diagnosis Service

Microservice cung cấp tính năng tư vấn triệu chứng sơ bộ cho ClinicSystem. Service này đọc dữ liệu `Final_Augmented_dataset_Diseases_and_Symptoms.csv`, huấn luyện mô hình RandomForest đơn giản và phục vụ API FastAPI `/predict`.

## Cấu trúc

- `requirements.txt`: danh sách thư viện Python cần thiết.
- `train_diagnosis_model.py`: script huấn luyện mô hình và sinh các file `diagnosis_model.pkl`, `label_encoder.pkl`, `feature_names.pkl`.
- `diagnosis_api.py`: FastAPI app load mô hình để inference.
- `data/`: chứa dataset CSV do bạn cung cấp.

## Thiết lập & chạy

```bash
python -m venv .venv
.venv\Scripts\activate  # hoặc source .venv/bin/activate trên macOS/Linux
pip install -r requirements.txt
```

Huấn luyện và chạy API:

```bash
# Bước 1: Huấn luyện và sinh mô hình
python train_diagnosis_model.py

# Bước 2: Khởi động API
uvicorn diagnosis_api:app --host 0.0.0.0 --port 8001
```

Sau khi chạy, endpoint `POST /predict` nhận payload:

```json
{
  "symptoms": ["fever", "cough", "shortness of breath"],
  "top_k": 5
}
```

và trả về danh sách bệnh cùng xác suất, mức độ và khuyến nghị đặt lịch.

## Lưu ý đạo đức

- Dự đoán của mô hình chỉ mang tính GỢI Ý và có thể sai lệch.
- Không thay thế chẩn đoán hay phác đồ điều trị của bác sĩ.
- Nếu triệu chứng nặng, bất thường hoặc kéo dài, người bệnh nên đặt lịch khám ngay tại cơ sở y tế.
