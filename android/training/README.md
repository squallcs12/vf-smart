# Traffic-Light Model — Training Pipeline

Reproduces `app/src/main/assets/traffic_light.tflite`. For the *what/why*
(dataset, classes, tiling rationale, app-side inference) see
[`TRAFFIC_LIGHT_MODEL.md`](TRAFFIC_LIGHT_MODEL.md).

Files here:

| File | Purpose |
|------|---------|
| `train.py` | train YOLOv11n on the 4×4-tiled dataset |
| `export_tflite.py` | export `best.pt` → float16 `.tflite` |
| `requirements.txt` | Python deps (torch installed separately, see below) |
| `dataset/`, `runs/` | **git-ignored** — dataset export and training outputs |

## 1. Environment

Python 3.12 + a CUDA GPU (the deployed model was trained on an RTX 5080, ~36 min;
`device="cpu"` works but is slow).

```bash
python -m venv venv && source venv/bin/activate
# torch from the CUDA index matching your GPU (cu128 = Blackwell / RTX 50xx):
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128
pip install -r requirements.txt
```

## 2. Get the dataset

The dataset lives on Roboflow (`bang-dao/vietnam-traffic-light-f0zoa`). The
deployed model uses **version 2** (4×4 tiling, 640×640 resize). Either:

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

## 3. Train

```bash
python train.py
```

Config: `yolo11n.pt`, 100 epochs (early-stop `patience=25`), `imgsz=640`,
`batch=64`. Outputs land in `runs/tiled4x4/weights/best.pt`.

## 4. Export + deploy

```bash
python export_tflite.py
cp runs/tiled4x4/weights/best_saved_model/best_float16.tflite \
   ../app/src/main/assets/traffic_light.tflite
```

The class order is preserved, so the new file is drop-in compatible. If you
change the **tile grid** or **class order**, update `TILE_ROWS`/`TILE_COLS` and
the `CLS_*` indices in `TrafficLightDetector.kt` to match.
