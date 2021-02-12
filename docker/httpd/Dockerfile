FROM debian:buster

ARG DEBIAN_FRONTEND=noninteractive


RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    apache2 \
    libapache2-mod-jk \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*


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


RUN a2enmod ssl headers proxy_http rewrite && \
    a2enconf ssl-params && \
    a2ensite default-ssl antweb_calacademy_org

RUN a2dismod mpm_event && \
    a2enmod mpm_prefork cgi

EXPOSE 80 443

STOPSIGNAL SIGWINCH

WORKDIR /var/log/apache2

CMD /usr/sbin/apache2ctl -D FOREGROUND