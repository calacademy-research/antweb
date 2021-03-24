
Install rclone
```bash
curl https://rclone.org/install.sh | sudo bash
```

Create ~/.config/rclone/rclone.conf. For a new machine, generate a new key/secret in digitalocean,
otherwise pull the config from a configured machine

```
[digitalocean]
type = s3
provider = DigitalOcean
access_key_id = ACCESS_KEY
secret_access_key = SECRET_KEY
endpoint = sfo3.digitaloceanspaces.com
acl = public-read
```

Rclone systemd mounting service. Derived from https://github.com/animosity22/homescripts/blob/master/systemd/rclone.service
and https://github.com/rclone/rclone/wiki/Systemd-rclone-mount

Create this service at `/etc/systemd/system/rclone.service`
```
cat <<'EOF' > /etc/systemd/system/rclone.service
[Unit]
Description=rclone mount for antweb:/mnt/antweb bucket
Requires=systemd-networkd.service
Wants=network-online.target
After=network-online.target

[Service]
Type=notify
Environment=RCLONE_CONFIG=/root/.config/rclone/rclone.conf
KillMode=none
ExecStart=/usr/bin/rclone mount digitalocean:/antweb /mnt/antweb \
--default-permissions \
--s3-acl public-read \
--dir-cache-time 24h \
# To log to syslog as well
--syslog \
# This is used for caching files to local disk
--vfs-cache-mode full \
# This limits the cache size to the value below (about half the drive)
--vfs-cache-max-size 80G \
# This adds a little buffer for read ahead
--vfs-read-ahead 256M \
# This limits the age in the cache if the size is reached and it removes the oldest files first
--vfs-cache-max-age 1000h  \
# Keep attributes in kernel for longer
--attr-timeout 10s \
# Use server upload as modification time (since upload occurs on change)
--use-server-modtime \
# Don't kill process if taking a while to read folders
--daemon-timeout 10m

ExecStop=/bin/fusermount -uz /mnt/antweb
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
```

And create a service for the achival bucket

```
cat <<'EOF' > /etc/systemd/system/rclone-dbarchive.service
[Unit]
Description=rclone mount for antweb:/mnt/antweb bucket
Requires=systemd-networkd.service
Wants=network-online.target
After=network-online.target

[Service]
Type=notify
Environment=RCLONE_CONFIG=/root/.config/rclone/rclone.conf
KillMode=none
ExecStart=/usr/bin/rclone mount digitalocean:/antweb-dbarchive /mnt/backup \
--default-permissions \
--vfs-cache-mode full \
--syslog \
--use-server-modtime \
--s3-acl private

ExecStop=/bin/fusermount -uz /mnt/backup
Restart=always
RestartSec=5


[Install]
WantedBy=multi-user.target
EOF
```

Add/uncomment `user_allow_other` in /etc/fuse.conf

Create bucket directories
```bash
mkdir -p /mnt/antweb
mkdir -p /mnt/backup
```

Start rclone service and mount buckets
```bash
systemd start rclone
systemd enable rclone

systemd start rclone-dbarchive
systemd enable rclone-dbarchive
```

Staging
---

Inside docker/httpd/sites-enabled:

If the server url is not antweb.calacademy.org, update ServerName in the following files

* 000-default.conf
* default-ssl.conf
* antweb_calacademy_org.conf


etc/AppResStageDocker.properties:
* set correct site.imgDomainApp and site.domain


Production
---

Create a symlink to latest database dump
```bash
ln -s /mnt/backup/db/ant-currentDump.sql.gz ant-currentDump.sql.gz
```

Make sure  `docker-compose.prod.yml` antweb.extra_hosts points to the correct private IP for antcat-export
