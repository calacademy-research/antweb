FROM debian:buster

ARG DEBIAN_FRONTEND=noninteractive


RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    apache2 \
    libapache2-mod-jk \
    libapache2-mod-wsgi-py3 \
    libssl-dev \
    python3 \
    python3-pip \
    python3-venv \
    mariadb-client \
    && rm -rf /var/lib/apt/lists/*

# this is how to enable the virtualenv inside the dockerfile
ENV VIRTUAL_ENV=/var/www/script/venv
RUN python3 -m venv $VIRTUAL_ENV
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

WORKDIR /var/www/script/v3.1
# Install python api dependencies
COPY api/v3.1/requirements.txt .
RUN pip install -r requirements.txt


WORKDIR /etc/apache2

COPY docker/httpd/apache2.conf apache2.conf
COPY docker/httpd/ports.conf ports.conf

COPY docker/httpd/sites-enabled/ sites-available/
COPY docker/httpd/mods-enabled/ mods-available/
COPY docker/httpd/conf-enabled/ conf-available/
RUN mkdir -p shared-conf
COPY docker/httpd/shared-conf/ shared-conf/

COPY docker/httpd/workers.properties /etc/libapache2-mod-jk/workers.properties


WORKDIR /etc/ssl

COPY docker/httpd/ssl/dhparam.pem certs/
COPY docker/httpd/ssl/private/ private/
COPY docker/httpd/ssl/www_antweb_org.crt ./
COPY docker/httpd/ssl/wildcard_calacademy_org.pem ./



COPY api/v3.1/ /var/www/script/v3.1/


RUN a2enmod ssl headers wsgi proxy_http rewrite && \
    a2enconf ssl-params && \
    a2ensite default-ssl antweb_calacademy_org

RUN a2dismod mpm_event && \
    a2enmod mpm_prefork cgi

EXPOSE 80 443

STOPSIGNAL SIGWINCH

WORKDIR /var/log/apache2

CMD /usr/sbin/apache2ctl -D FOREGROUND