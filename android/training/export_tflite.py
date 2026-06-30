"""Export the trained best.pt to the float16 TFLite the Android app loads.

Usage:
    python export_tflite.py

Writes runs/tiled4x4/weights/best_saved_model/best_float16.tflite, which you
then copy to app/src/main/assets/traffic_light.tflite.

Equivalent CLI:
    yolo export model=runs/tiled4x4/weights/best.pt format=tflite half=True imgsz=640
"""
from pathlib import Path

from ultralytics import YOLO

HERE = Path(__file__).resolve().parent
BEST = HERE / "runs" / "tiled4x4" / "weights" / "best.pt"

if __name__ == "__main__":
    if not BEST.exists():
        raise SystemExit(f"{BEST} not found — train first (python train.py).")
    YOLO(str(BEST)).export(format="tflite", half=True, imgsz=640)
    out = BEST.parent / "best_saved_model" / "best_float16.tflite"
    print(f"EXPORT_DONE: {out}")
