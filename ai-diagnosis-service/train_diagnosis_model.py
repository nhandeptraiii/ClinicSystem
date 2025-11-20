"""Training script for the ClinicSystem AI diagnosis helper.

This small utility loads the augmented diseases/symptoms dataset,
fits a RandomForest model, and persists the artifacts that will
be consumed by the FastAPI microservice.
"""

from __future__ import annotations

from pathlib import Path
from typing import List

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder

BASE_DIR = Path(__file__).resolve().parent
DATA_PATH = BASE_DIR / "data" / "Final_Augmented_dataset_clean_for_training.csv"
MODEL_PATH = BASE_DIR / "diagnosis_model.pkl"
LABEL_ENCODER_PATH = BASE_DIR / "label_encoder.pkl"
FEATURE_NAMES_PATH = BASE_DIR / "feature_names.pkl"

TARGET_COLUMN = "diseases_clean"
REMOVED_FEATURES = {"diseases", TARGET_COLUMN}
RANDOM_SEED = 42


def load_dataset() -> tuple[pd.DataFrame, pd.Series, List[str]]:
    """Load the CSV dataset and return features, labels, and column names."""
    if not DATA_PATH.exists():
        raise FileNotFoundError(
            f"Dataset not found at {DATA_PATH}. Please place the CSV file before training."
        )

    dataframe = pd.read_csv(DATA_PATH)
    if TARGET_COLUMN not in dataframe.columns:
        raise ValueError(f"Dataset must contain '{TARGET_COLUMN}' column.")

    feature_columns = [
    col for col in dataframe.columns if col not in REMOVED_FEATURES
]
    features = dataframe[feature_columns]
    labels = dataframe[TARGET_COLUMN]
    return features, labels, feature_columns


def train_model() -> None:
    """Train the RandomForest model and persist all artifacts."""
    features, labels, feature_names = load_dataset()

    label_encoder = LabelEncoder()
    encoded_labels = label_encoder.fit_transform(labels)

    X_train, X_test, y_train, y_test = train_test_split(
        features,
        encoded_labels,
        test_size=0.2,
        stratify=encoded_labels,
        random_state=RANDOM_SEED,
    )

    model = RandomForestClassifier(
    n_estimators=120,
    max_depth=25,
    n_jobs=4,
    random_state=RANDOM_SEED,
    class_weight="balanced_subsample",
)
    model.fit(X_train, y_train)

    train_accuracy = model.score(X_train, y_train)
    test_predictions = model.predict(X_test)
    test_accuracy = accuracy_score(y_test, test_predictions)

    print(f"Train accuracy: {train_accuracy:.4f}")
    print(f"Test accuracy:  {test_accuracy:.4f}")

    joblib.dump(model, MODEL_PATH)
    joblib.dump(label_encoder, LABEL_ENCODER_PATH)
    joblib.dump(feature_names, FEATURE_NAMES_PATH)

    print(f"Saved model to {MODEL_PATH}")
    print(f"Saved label encoder to {LABEL_ENCODER_PATH}")
    print(f"Saved feature names to {FEATURE_NAMES_PATH}")


if __name__ == "__main__":
    train_model()
