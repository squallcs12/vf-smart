"""Train the VF3 traffic-light detector (YOLOv11n) on the 4x4-tiled dataset.

Usage:
    python train.py

Expects the Roboflow export extracted to ./dataset (train/ valid/ test/). Green
and Green-count were removed from the Roboflow project, so the export is now
**2-class** (Red, Red count). This script fixes data.yaml's path (the Roboflow
zip ships a relative `path` that resolves wrong) while preserving the export's
own class names/order, and prints them so you can confirm they match the app's
indices in TrafficLightDetector (Red=0, Red count=1).

See README.md for the full pipeline (dataset download, env, export, deploy).
"""
from pathlib import Path

import yaml
from ultralytics import YOLO

HERE = Path(__file__).resolve().parent
DATASET = HERE / "dataset"

# Fallback only — normally the names come from the downloaded data.yaml.
DEFAULT_NAMES = ["Red", "Red count"]


def ensure_data_yaml() -> Path:
    out = DATASET / "data.yaml"
    cfg = yaml.safe_load(out.read_text()) if out.exists() else {}
    cfg = cfg or {}
    names = cfg.get("names") or DEFAULT_NAMES
    cfg.update(
        path=str(DATASET),
        train="train/images",
        val="valid/images",
        test="test/images",
        nc=len(names),
        names=names,
    )
    out.write_text(yaml.safe_dump(cfg, sort_keys=False))
    print(f"classes ({cfg['nc']}): {names}  "
          "— must match TrafficLightDetector CLS_* indices")
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
