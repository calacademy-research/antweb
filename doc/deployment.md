Create ~/.passwd-s3fs, put `ACCESS_KEY:SECRET_KEY` inside

Install s3fs
```bash
sudo apt install s3fs
```

Add s3fs bucket to fstab
```
s3fs#antweb /mnt/antweb 	fuse 	_netdev,url=https://sfo3.digitaloceanspaces.com,use_cache=/tmp,allow_other,use_path_request_style,ensure_diskfree=20000 0 0
```

Add/uncomment `user_allow_other` in /etc/fuse.conf

Create bucket directory 
```bash
mkdir -p /mnt/antweb
```

Mount s3fs bucket
```bash
mount /mnt/antweb
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
ln -s /mnt/antweb/backup/db/ant-currentDump.sql.gz ant-currentDump.sql.gz
```

Make sure  `docker-compose.prod.yml` antweb.extra_hosts points to the correct private IP for antcat-export
