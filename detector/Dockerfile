FROM python:3.12-slim

# OpenCV and the RTSP reader need these system libraries; the slim base
# image doesn't ship them.
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgl1 \
        libglib2.0-0 \
        ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .

# Install the CPU-only PyTorch build explicitly first. Left to itself, pip
# pulls the CUDA build (~4 GB of GPU libraries) which is useless here and
# makes the image enormous.
RUN pip install --no-cache-dir \
        torch torchvision --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r requirements.txt

# Bake the weights into the image so the container doesn't need to reach
# the internet on first start (and every restart is instant).
RUN python -c "from ultralytics import YOLO; YOLO('yolov8n.pt')"

COPY . .

# Unbuffered so detection logs stream to `docker compose logs` live
# instead of sitting in a buffer.
ENV PYTHONUNBUFFERED=1

CMD ["python", "main.py"]
