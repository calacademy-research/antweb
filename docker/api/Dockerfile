FROM python:3.12

WORKDIR /api

RUN apt-get update && \
        apt-get install -y --no-install-recommends \
        mariadb-client \
        && rm -rf /var/lib/apt/lists/*

COPY api/v3.1/requirements.txt .
RUN pip install -r requirements.txt

COPY api/v3.1/ ./

# Requests are served as the user api (not root). gunicorn's master starts as root and loads
# the app before starting the workers (--preload), so it can read the database login in
# /antweb-etc/api-db.properties whatever that file's owner and mode on the host; the workers
# then run as api. Python never writes into /api (read-only container).
RUN groupadd -g 2002 api && useradd -u 2002 -g api -M -d /nonexistent -s /usr/sbin/nologin api
ENV PYTHONDONTWRITEBYTECODE=1

EXPOSE 5000

ENTRYPOINT ["gunicorn", "-b", "0.0.0.0:5000", \
    "--worker-tmp-dir", "/dev/shm", \
    "--workers=3", \
    "--threads=4", \
    "--worker-class=gthread", \
    "--preload", \
    "--user=api", "--group=api", \
    "api:application"]