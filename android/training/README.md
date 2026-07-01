# Traffic-Light Model — Training & Configuration

How `app/src/main/assets/traffic_light.tflite` is produced, configured, and
consumed, plus the runnable pipeline to reproduce it. The model is a custom
**YOLOv11n** (Ultralytics) detector exported to TensorFlow Lite, used by
[`TrafficLightDetector`](../app/src/main/kotlin/com/daotranbang/vfsmart/vision/TrafficLightDetector.kt) /
[`TrafficLightAnalyzer`](../app/src/main/kotlin/com/daotranbang/vfsmart/vision/TrafficLightAnalyzer.kt).

## Goal

Detect a **red** traffic light (and its red countdown number) reliably, including
far-away lights in dashcam frames. Green is detected by the model but **ignored**
downstream — the app reports only `RED` / `NONE` (see [Inference](#inference-app-side)).

## Files here

| File | Purpose |
|------|---------|
| `train.py` | train YOLOv11n on the 4×4-tiled dataset |
| `export_tflite.py` | export `best.pt` → float16 `.tflite` |
| `requirements.txt` | Python deps (torch installed separately, see below) |
| `dataset/`, `runs/` | **git-ignored** — dataset export and training outputs |

## Dataset (Roboflow)

- **Workspace / project:** `bang-dao` / `vietnam-traffic-light-f0zoa`
  (Roboflow Universe). Object-detection.
- **Source images:** ~464, from the Roboflow "vietnam-traffic-light" set plus
  Imou-camera footage.
- **Classes (exact `data.yaml` order — do not reorder, the app hard-codes these
  indices):**

  | Index | Label         | Used by app? |
  |-------|---------------|--------------|
  | 0     | `Green`       | ignored      |
  | 1     | `Green count` | ignored      |
  | 2     | `Red`         | **yes** (`CLS_RED`) |
  | 3     | `Red count`   | **yes** (`CLS_RED_COUNT`) |

| Version | Images | Preprocessing | Notes |
|---------|--------|---------------|-------|
| v1 | 512 | (baseline) | original, no tiling |
| **v2** | **7,424** (train 7,024 / valid 272 / test 128) | **4×4 tiling**, resize 640×640 (stretch), auto-orient | current — the deployed model is trained on this |

### Why 4×4 tiling

A far light is ~40 px in a 2304×1296 frame. Resizing the whole frame to the
model's 640×640 input shrinks it to ~11 px — below the detector's confidence
floor, so it's missed. Splitting the frame into a 4×4 grid and resizing **each
tile** to 640 makes that light ~4× larger relative to its tile, so it's
detectable. **Tiling is a contract:** because the model is trained on tiles,
inference must tile too (see [Inference](#inference-app-side)).

## Reproduce

### 1. Environment

Python 3.12 + a CUDA GPU (the deployed model was trained on an RTX 5080, ~36 min;
`device="cpu"` works but is slow).

```bash
python -m venv venv && source venv/bin/activate
# torch from the CUDA index matching your GPU (cu128 = Blackwell / RTX 50xx):
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128
pip install -r requirements.txt
```

### 2. Get the dataset

The deployed model uses **version 2** (4×4 tiling, 640×640 resize). Either:

- Download the **YOLOv11** export of v2 from the Roboflow UI and unzip into
  `./dataset/` (so you have `dataset/train`, `dataset/valid`, `dataset/test`), or
- Generate a new version via the API, then download it:
  ```bash
  KEY=...   # local.properties: ROBOTFLOW_API_KEY — never commit it
  curl -X POST "https://api.roboflow.com/bang-dao/vietnam-traffic-light-f0zoa/generate?api_key=$KEY" \
    -H "Content-Type: application/json" \
    -d '{"preprocessing":{"auto-orient":true,
                          "resize":{"width":640,"height":640,"format":"Stretch to"},
                          "tile":{"rows":4,"columns":4}},
         "augmentation":{}}'
  # then GET .../<version>/yolov11?api_key=$KEY for the download link
  ```

`train.py` rewrites `dataset/data.yaml` with the correct absolute path and the
canonical class order on each run, so you don't have to fix it by hand.

### 3. Train

```bash
python train.py
```

Config: `yolo11n.pt` (nano — matches the ~5 MB tflite size/speed budget),
100 epochs (early-stop `patience=25`), `imgsz=640`, `batch=64`, `device=0`.
Outputs land in `runs/tiled4x4/weights/best.pt`.

**Validation metrics** (`best.pt`, val split — small, so noisy):

| Class | Instances | P | R | mAP50 |
|-------|-----------|------|------|-------|
| Red | 4 | 0.92 | 0.50 | 0.53 |
| Red count | 13 | 0.73 | 0.63 | 0.75 |
| Green / Green count | 2 / 3 | — | — | low (ignored) |

> The val split has only ~22 light instances (4×4 tiling produces many empty
> background tiles), so treat these as indicative, not precise.

### 4. Export + deploy

```bash
python export_tflite.py    # = yolo export model=.../best.pt format=tflite half=True imgsz=640
cp runs/tiled4x4/weights/best_saved_model/best_float16.tflite \
   ../app/src/main/assets/traffic_light.tflite
```

Produces a ~5.1 MB fp16 `.tflite`. The class order is preserved, so it's drop-in
compatible. If you change the **tile grid** or **class order**, also update
`TILE_ROWS`/`TILE_COLS` and the `CLS_*` indices in `TrafficLightDetector.kt`.

**On-frame check** (`original.jpg`, run per-tile): old model → nothing /
Red-count 0.17; new model → **Red 0.69**, Red count 0.47.

## Inference (app side)

[`TrafficLightDetector`](../app/src/main/kotlin/com/daotranbang/vfsmart/vision/TrafficLightDetector.kt):

- **Tiling:** `detect()` splits each frame into `TILE_ROWS`×`TILE_COLS` = **4×4**,
  runs the model per tile, maps boxes back to full-frame coords, then global NMS.
  This must match the training tiling.
- **Red-only:** `summarise()` keeps only `Red` / `Red count` boxes; `State` is
  `RED` or `NONE`. `hasRedCount` flags a red countdown box; the digits are OCR'd
  separately (see `TrafficLightAnalyzer` + `RedLightDetector`).
- **Backend:** a single cached **CPU** interpreter, `numThreads = cores`.
  The **GPU delegate is intentionally not used** — it hangs on the Android
  emulator (inference parks on a GL fence). One interpreter (≈75 MB arena);
  a pool of interpreters was tried and reverted (≈4× memory, no speedup —
  `numThreads` already uses all cores).
- **Thresholds:** `CONF_THRESHOLD = 0.40`, `IOU_THRESHOLD = 0.45`.

**Capture resolution:** [`RtspTrafficLightView`](../app/src/main/kotlin/com/daotranbang/vfsmart/ui/components/RtspTrafficLightView.kt)
samples frames at **1920×1080** (`ANALYSIS_W/H`) — tiling is pointless on a small
capture, and the benefit is ultimately capped by the RTSP stream's own resolution.
Because `detect()` does 16 inferences/frame, the live view samples slowly (~1 fps).

## Known limitations

- **Speed:** 16 inferences/frame is heavy. The real lever is *less work* (fewer
  tiles, smaller `imgsz`) — more threads/interpreters don't help (CPU-bound).
- **Stream resolution caps the gain:** low-res RTSP substreams limit how small a
  light tiling can recover.
- **Green is not reported** by design; re-enable in `summarise()` if ever needed.
