
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
acl = private
```

Create rclone mounting script at `/usr/local/bin/rclonefs`. See 
[rclone mount helper script](https://github.com/rclone/rclone/wiki/rclone-mount-helper-script) for extra details.


```bash
touch /usr/local/bin/rclonefs
chmod +x /usr/local/bin/rclonefs

cat <<'EOF' > /usr/local/bin/rclonefs
#!/bin/bash
remote=$1
mountpoint=$2
shift 2

# Process -o parameters
while getopts :o: opts; do
    case $opts in
        o)
            params=${OPTARG//,/ }
            for param in $params; do
                if [ "$param" == "rw"   ]; then continue; fi
                if [ "$param" == "ro"   ]; then continue; fi
                if [ "$param" == "dev"  ]; then continue; fi
                if [ "$param" == "suid" ]; then continue; fi
                if [ "$param" == "exec" ]; then continue; fi
                if [ "$param" == "auto" ]; then continue; fi
                if [ "$param" == "nodev" ]; then continue; fi
                if [ "$param" == "nosuid" ]; then continue; fi
                if [ "$param" == "noexec" ]; then continue; fi
                if [ "$param" == "noauto" ]; then continue; fi
                if [[ $param == x-systemd.* ]]; then continue; fi
                trans="$trans --$param"
            done
            ;;
        \?)
            echo "Invalid option: -$OPTARG"
            ;;
    esac
done

# exec rclone
trans="$trans $remote $mountpoint"
# NOTE: do not try "mount --daemon" here, it does not play well with systemd automount, use '&'!
# NOTE: mount is suid and ignores pre-set PATHs -> specify explicitely
PATH=$PATH rclone mount $trans </dev/null >/dev/null 2>/dev/null &

# WARNING: this will loop forever if remote is actually empty!
until [ "`ls -l $mountpoint`" != 'total 0' ]; do
    sleep 1
done
EOF
```


Add rclone mount to fstab
```
digitalocean:/antweb	/mnt/antweb	fuse.rclonefs	config=/root/.config/rclone/rclone.conf,default-permissions,vfs-cache-mode=full		0 0
```

Add/uncomment `user_allow_other` in /etc/fuse.conf

Create bucket directory 
```bash
mkdir -p /mnt/antweb
```

Mount rclone volume
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
