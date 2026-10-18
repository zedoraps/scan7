"""Train the card detector with Ultralytics (Apple MPS by default; --device cpu on mini).

Example:
    uv run python scripts/train.py --data out/synth/dataset.yaml --name synth_v1
    uv run python scripts/train.py --data out/mix/dataset.yaml --model runs/synth_v1/weights/best.pt --name ft_v1
    # continue an interrupted run (optimizer, LR schedule and epoch count from the checkpoint) into runs/<name>:
    python scripts/train.py --resume runs/ft_v3/weights/last.pt --data out/mix3/dataset.yaml --device cpu --name ft_v3m
"""
import argparse
import os
from pathlib import Path

os.environ.setdefault("PYTORCH_ENABLE_MPS_FALLBACK", "1")

from ultralytics import YOLO  # noqa: E402

ML = Path(__file__).resolve().parent.parent


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=str(ML / "out/synth/dataset.yaml"))
    ap.add_argument("--model", default=str(ML / "models/yolo26n.pt"), help="pretrained weights or a previous run's best.pt")
    ap.add_argument("--name", default="synth_v1")
    ap.add_argument("--epochs", type=int, default=60)
    ap.add_argument("--imgsz", type=int, default=640)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--patience", type=int, default=15)
    ap.add_argument("--device", default="mps", help="mps (Mac), cpu, xpu, 0 (CUDA) ...")
    ap.add_argument("--resume", metavar="LAST_PT", help="resume this checkpoint; keeps its stored hyperparameters and "
                    "only takes --data, --device, --workers and --name (output dir) from the command line")
    args = ap.parse_args()

    if args.resume:
        # Ultralytics resume: everything else (epochs, imgsz, batch, augmentation, lr) comes from the checkpoint.
        YOLO(args.resume).train(
            resume=args.resume,
            data=args.data,
            device=args.device,
            workers=args.workers,
            save_dir=str(ML / "runs" / args.name),
        )
        return

    model = YOLO(args.model)
    model.train(
        data=args.data,
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        workers=args.workers,
        patience=args.patience,
        device=args.device,
        project=str(ML / "runs"),
        name=args.name,
        exist_ok=False,
        # The synthetic generator already does rotation, perspective, lighting and blur.
        # No horizontal/vertical flips: a mirrored card is not a real card.
        fliplr=0.0,
        flipud=0.0,
        degrees=0.0,
        hsv_h=0.01,
        plots=True,
    )


if __name__ == "__main__":
    main()
