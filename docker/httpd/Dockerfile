FROM debian:buster

ARG DEBIAN_FRONTEND=noninteractive


RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    apache2 \
    libapache2-mod-jk \
    && rm -rf /var/lib/apt/lists/*


WORKDIR /etc/apache2

COPY docker/httpd/apache2.conf apache2.conf
COPY docker/httpd/ports.conf ports.conf

COPY docker/httpd/sites-enabled/ sites-available/
COPY docker/httpd/mods-enabled/ mods-available/

COPY docker/httpd/workers.properties /etc/libapache2-mod-jk/workers.properties


RUN a2enmod headers proxy_http rewrite

RUN a2dismod mpm_event && \
    a2enmod mpm_prefork cgi

EXPOSE 80

STOPSIGNAL SIGWINCH

WORKDIR /var/log/apache2

CMD /usr/sbin/apache2ctl -D FOREGROUND