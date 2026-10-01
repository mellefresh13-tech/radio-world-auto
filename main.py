from __future__ import annotations

import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "api" / "src"))

import uvicorn  # noqa: E402


if __name__ == "__main__":
    uvicorn.run(
        "radio_api.app:app",
        host="0.0.0.0",
        port=int(os.getenv("PORT", "8000")),
    )
