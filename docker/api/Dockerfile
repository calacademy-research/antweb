FROM python:3.7-slim

WORKDIR /api

RUN apt-get update && \
        apt-get install -y --no-install-recommends \
        mariadb-client \
        && rm -rf /var/lib/apt/lists/*

COPY api/v3.1/requirements.txt .
RUN pip install -r requirements.txt

COPY api/v3.1/ ./

EXPOSE 5000

CMD ["gunicorn", "-b", "0.0.0.0:5000", \
    "--worker-tmp-dir", "/dev/shm", \
    "--workers=3", \
    "--threads=4", \
    "--worker-class=gthread", \
    "api:application"]