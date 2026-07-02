"""Train the VF3 traffic-light detector (YOLOv11n) on the 4x4-tiled dataset.

Usage:
    python train.py

Expects the Roboflow v2 export extracted to ./dataset (train/ valid/ test/).
Rewrites ./dataset/data.yaml with an absolute path + the canonical class order
so Ultralytics resolves the splits correctly regardless of how the zip shipped.

See README.md for the full pipeline (dataset download, env, export, deploy).
"""
from pathlib import Path

import yaml
from ultralytics import YOLO

HERE = Path(__file__).resolve().parent
DATASET = HERE / "dataset"

# Class order is a contract with the Android app (TrafficLightDetector hard-codes
# these indices: Red=2, Red count=3). Do not reorder.
CLASS_NAMES = ["Green", "Green count", "Red", "Red count"]


def ensure_data_yaml() -> Path:
    cfg = {
        "path": str(DATASET),
        "train": "train/images",
        "val": "valid/images",
        "test": "test/images",
        "nc": len(CLASS_NAMES),
        "names": CLASS_NAMES,
    }
    out = DATASET / "data.yaml"
    out.write_text(yaml.safe_dump(cfg, sort_keys=False))
    return out


if __name__ == "__main__":
    if not DATASET.exists():
        raise SystemExit(f"Dataset not found at {DATASET} — see README.md (step 2).")

    data = ensure_data_yaml()
    model = YOLO("yolo11n.pt")  # nano backbone — matches the ~5 MB tflite budget
    model.train(
        data=str(data),
        epochs=100,
        imgsz=640,
        batch=64,
        device=0,        # first CUDA GPU; use "cpu" if no GPU
        patience=25,
        project=str(HERE / "runs"),
        name="tiled4x4",
        exist_ok=True,
    )
    print("TRAIN_DONE")
