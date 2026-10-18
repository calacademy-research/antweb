FROM tomcat:11-jdk21

ENV TZ="America/Los_Angeles" \
    CATALINA_OPTS="-Xms1024M -Xmx16384M -server -XX:+UseParallelGC" \
    JAVA_OPTS='-Djava.awt.headless=true -Djava.security.egd=file:/dev/./urandom'

# Fixed ids so the host can grant them access (doc/tomcat-nonroot-runbook.md):
# antweb group 2000 (host: secrets chgrp, rclone --gid), tomcat user 2001
# (rclone --uid). Tomcat runs as tomcat with primary group antweb.
RUN groupadd -g 2000 antweb && \
    useradd -u 2000 -g antweb -m -s /bin/bash antweb && \
    groupadd -g 2001 tomcat && \
    useradd -u 2001 -g antweb -G tomcat -d /usr/local/tomcat -M -s /usr/sbin/nologin tomcat


RUN mkdir -p /mnt/antweb && \
    chown -R antweb:antweb /mnt/antweb


RUN mkdir -p /antweb/deploy && \
    chown -R antweb:antweb /antweb


RUN apt-get update && apt-get install -y --no-install-recommends \
ant \
ant-optional \
cron \
imagemagick \
logrotate \
util-linux \
default-mysql-client \
php \
python3 \
unzip \
zip \
&& rm -rf /var/lib/apt/lists/* \
&& setpriv --version

# ImageMagick (used to make the image sizes of uploaded TIFF/JPEG/PNG files) may only
# read and write ordinary image formats, within resource limits. Checked in CI
# (tests/container/imagemagick_policy_check.sh).
COPY --chmod=0644 docker/antweb/imagemagick-policy.xml /etc/ImageMagick-6/policy.xml

RUN ln -fs /usr/share/zoneinfo/"${TZ}" /etc/localtime && \
    dpkg-reconfigure --frontend noninteractive tzdata

# Root-owned: these run as root, so tomcat must not be able to change them.
COPY --chmod=0755 docker/antweb/startup.sh /startup.sh
COPY --chmod=0755 docker/antweb/fix-perms.sh /usr/local/bin/antweb-fix-perms
COPY --chmod=0644 docker/antweb/crontab.txt /etc/cron.d/antwebcrontab
# Rotation of the growing logs in web/log and log/ (run by that crontab as tomcat).
COPY --chmod=0644 docker/antweb/logrotate.conf /etc/antweb-logrotate.conf


COPY --chown=tomcat docker/antweb/tomcat/conf/ /usr/local/tomcat/conf/

WORKDIR /usr/local/tomcat


RUN chown -R tomcat:antweb /usr/local/tomcat && \
    chmod -R g+r conf && \
    chmod g+x conf && \
    mkdir /opt/tomcat && \
    ln -s /usr/local/tomcat /opt/tomcat/latest
    #TODO get rid of references to /opt/tomcat/latest, use only /usr/local/tomcat

USER tomcat


# Tomcat's manager and host-manager apps are not installed (removed 2026-10-15):
# nothing uses them, and they would let anyone with the password deploy code.


WORKDIR /usr/local/tomcat/conf


# Container must be built from base antweb dir, or have context set to base antweb dir
WORKDIR /antweb/deploy

COPY --chown=antweb --chmod=755 build.xml ./
COPY --chown=antweb --chmod=755 bin ./bin
COPY --chown=antweb --chmod=755 doc ./doc
COPY --chown=antweb --chmod=755 etc ./etc
COPY --chown=antweb --chmod=755 META-INF ./META-INF
COPY --chown=antweb --chmod=755 src ./src
COPY --chown=antweb --chmod=755 test ./test
COPY --chown=antweb --chmod=755 web ./web
COPY --chown=antweb --chmod=755 WEB-INF ./WEB-INF


USER antweb

# Specifies which resources.properties file we copy at buildtime
ARG ENV=PROD

WORKDIR /antweb/deploy
RUN etc/configDocker.sh


RUN mkdir -p /antweb/links
WORKDIR /antweb/links
RUN sh ../deploy/bin/links.sh


USER root
RUN mkdir /data && \
    chmod 755 /data && \
    chown antweb:antweb /data

RUN mkdir -pm 777 /usr/local/antweb && chown antweb:antweb /usr/local/antweb

USER antweb
RUN ln -s /usr/local/antweb /data/antweb

RUN mkdir -p /usr/local/antweb/web


WORKDIR /usr/local/antweb/web
RUN mkdir log -pm 777 && \
    mkdir data -pm 777 && \
    mkdir genInc -pm 777 && \
    touch genInc/statistics.jsp

#RUN mkdir -p -m 777 /data/antweb/images && \
#    mkdir -pm 777 /data/antweb/images/temp


# make links to minio bucket for data storage. it's unfortunate that data is intermixed with the web pages and logs
# bucket is mounted at /mnt/antweb

RUN ln -s /mnt/antweb/web/bak bak && \
    ln -s /mnt/antweb/web/curator curator && \
    ln -s /mnt/antweb/web/documentation documentation && \
    ln -s /mnt/antweb/web/speciesList speciesList && \
    ln -s /mnt/antweb/web/upload upload && \
    ln -s /mnt/antweb/web/workingdir workingdir && \
    ln -s /mnt/antweb/web/log/specimen log/specimen && \
    ln -s /mnt/antweb/web/log/worldants log/worldants && \
    ln -s /mnt/antweb/web/log/detail log/detail && \
    ln -s /mnt/antweb/web/log/upload log/upload && \
    ln -s /mnt/backup /data/antweb/backup && \
    ln -s /mnt/antweb/images /data/antweb/images


#    mkdir -p /data/antweb/images/temp      # Dash: temp folder should already be in bucket

COPY --chown=antweb docker/antweb/platform.properties /data/antweb/WEB-INF/classes/

WORKDIR /antweb/deploy

USER root

# Build as root, then hand the deployed app (and /antweb/deploy, where later
# `ant deploy` runs create build/) to tomcat, which runs Tomcat (startup.sh).
# Nothing in the app needs to be world-writable.
RUN ant deploy && ant clean && \
    chown tomcat:antweb /antweb/deploy && \
    chown -R tomcat:antweb /usr/local/antweb && \
    chmod -R o-w /usr/local/antweb

ENTRYPOINT ["/startup.sh"]
