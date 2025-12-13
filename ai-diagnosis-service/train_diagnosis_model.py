"""
Training script for the ClinicSystem AI diagnosis helper.

This script loads the reduced diseases/symptoms dataset
(136 real-world diseases + ~200 patient-friendly symptoms),
trains a RandomForest model, evaluates it with multiple metrics
(Accuracy, Precision, Recall, F1, Top-K, Cross-Validation),
and persists the artifacts for the FastAPI microservice.
"""

from __future__ import annotations

from pathlib import Path
from typing import List

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    precision_recall_fscore_support,
    top_k_accuracy_score,
)
from sklearn.model_selection import train_test_split, StratifiedKFold, cross_val_score
from sklearn.preprocessing import LabelEncoder

# =============================
# Paths & constants
# =============================

BASE_DIR = Path(__file__).resolve().parent

# ⚠️ Đảm bảo bạn copy file CSV này vào thư mục:
# ai-diagnosis-service/data/Reduced_Dataset_136diseases_200symptoms.csv
DATA_PATH = BASE_DIR / "data" / "Specialized_Training_Dataset.csv"

MODEL_PATH = BASE_DIR / "diagnosis_model.pkl"
LABEL_ENCODER_PATH = BASE_DIR / "label_encoder.pkl"
FEATURE_NAMES_PATH = BASE_DIR / "feature_names.pkl"

TARGET_COLUMN = "diseases"
REMOVED_FEATURES = {"diseases", TARGET_COLUMN}  # "diseases" không có cũng không sao
RANDOM_SEED = 42


def load_dataset() -> tuple[pd.DataFrame, pd.Series, List[str]]:
    """
    Load the CSV dataset and return:
      - features DataFrame
      - label Series
      - list of feature column names
    """
    if not DATA_PATH.exists():
        raise FileNotFoundError(
            f"Dataset not found at {DATA_PATH}. "
            f"Please place the CSV file before training."
        )

    df = pd.read_csv(DATA_PATH)

    if TARGET_COLUMN not in df.columns:
        raise ValueError(f"Dataset must contain '{TARGET_COLUMN}' column.")

    feature_columns = [
        col for col in df.columns
        if col not in REMOVED_FEATURES
    ]

    X = df[feature_columns]
    y = df[TARGET_COLUMN]

    print(f"Loaded dataset from: {DATA_PATH}")
    print(f"Num samples: {len(df)}")
    print(f"Num features (symptoms): {len(feature_columns)}")
    print(f"Num diseases (classes): {y.nunique()}")
    print()

    return X, y, feature_columns


def train_model() -> None:
    """
    Train the RandomForest model on the reduced dataset,
    evaluate it, and persist artifacts.
    """
    # 1) Load data
    X, y_raw, feature_names = load_dataset()

    # 2) Encode labels
    label_encoder = LabelEncoder()
    y = label_encoder.fit_transform(y_raw)

    # 3) Train/Test split
    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=0.2,
        stratify=y,
        random_state=RANDOM_SEED,
    )

    # 4) Define model (tối ưu hơn nhưng vẫn vừa sức với 16GB RAM)
    model = RandomForestClassifier(
        n_estimators=300,              # nhiều cây hơn -> ổn định hơn
        max_depth=25,                 # đủ sâu nhưng không quá overfit
        min_samples_leaf=2,           # giảm noise
        n_jobs=-1,                    # dùng full CPU
        random_state=RANDOM_SEED,
        class_weight="balanced_subsample",
    )

    # 5) Cross-Validation trên toàn bộ dữ liệu (trước khi fit chính)
    print("=== 5-Fold Stratified Cross-Validation (Accuracy) ===")
    skf = StratifiedKFold(
        n_splits=5, shuffle=True, random_state=RANDOM_SEED
    )
    cv_scores = cross_val_score(
        model,
        X,
        y,
        cv=skf,
        scoring="accuracy",
        n_jobs=-1,
    )
    print(f"CV accuracy mean: {cv_scores.mean():.4f} (+/- {cv_scores.std():.4f})")
    print()

    # 6) Train trên train set
    model.fit(X_train, y_train)

    # 7) Đánh giá trên train set (để xem overfitting)
    y_train_pred = model.predict(X_train)
    train_acc = accuracy_score(y_train, y_train_pred)
    print(f"Train accuracy: {train_acc:.4f}")

    # 8) Đánh giá trên test set
    y_test_pred = model.predict(X_test)
    test_acc = accuracy_score(y_test, y_test_pred)
    print(f"Test accuracy:  {test_acc:.4f}")

    # Precision / Recall / F1 (macro & weighted)
    prec_macro, rec_macro, f1_macro, _ = precision_recall_fscore_support(
        y_test, y_test_pred, average="macro", zero_division=0
    )
    prec_weighted, rec_weighted, f1_weighted, _ = precision_recall_fscore_support(
        y_test, y_test_pred, average="weighted", zero_division=0
    )

    print("\n=== Macro-averaged metrics (treat all classes equally) ===")
    print(f"Precision (macro): {prec_macro:.4f}")
    print(f"Recall    (macro): {rec_macro:.4f}")
    print(f"F1-score  (macro): {f1_macro:.4f}")

    print("\n=== Weighted-averaged metrics (theo tần suất lớp) ===")
    print(f"Precision (weighted): {prec_weighted:.4f}")
    print(f"Recall    (weighted): {rec_weighted:.4f}")
    print(f"F1-score  (weighted): {f1_weighted:.4f}")

    # 9) Top-K accuracy (rất quan trọng với bài toán gợi ý chẩn đoán)
    if hasattr(model, "predict_proba"):
        y_proba = model.predict_proba(X_test)
        # Top-3
        top3_acc = top_k_accuracy_score(y_test, y_proba, k=3)
        # Top-5 (nếu số lớp >= 5)
        k_for_top5 = min(5, y_proba.shape[1])
        top5_acc = top_k_accuracy_score(y_test, y_proba, k=k_for_top5)

        print("\n=== Top-K accuracy ===")
        print(f"Top-1 accuracy: {test_acc:.4f}")
        print(f"Top-3 accuracy: {top3_acc:.4f}")
        print(f"Top-{k_for_top5} accuracy: {top5_acc:.4f}")
    else:
        print("\nModel does not support predict_proba; skip Top-K metrics.")

    # 10) Báo cáo chi tiết theo từng lớp (có thể dài, dùng cho phân tích luận văn)
    print("\n=== Classification report (per disease) ===")
    print(
        classification_report(
            y_test,
            y_test_pred,
            target_names=label_encoder.classes_,
            zero_division=0,
        )
    )

    # 11) Lưu model + encoder + feature names
    joblib.dump(model, MODEL_PATH)
    joblib.dump(label_encoder, LABEL_ENCODER_PATH)
    joblib.dump(feature_names, FEATURE_NAMES_PATH)

    print(f"Saved model to {MODEL_PATH}")
    print(f"Saved label encoder to {LABEL_ENCODER_PATH}")
    print(f"Saved feature names to {FEATURE_NAMES_PATH}")


if __name__ == "__main__":
    train_model()
