Antweb README.md file



See doc/fileMods.txt

See doc/releaseNotes.txt
     This contains all software release with detailed notes of features.

See doc/toDo.txt

See doc/install.txt
## 📚 Documentation

Complete technical documentation is available in the [/docs](docs/) directory.

New contributors should start with [docs/README.md](docs/README.md).
Configuration
---
###Logging levels
`logs/antwebInfo.log` is configured to show `INFO` and above in production, and `DEBUG` in development. 

The `JAVA_OPTS=-Dlogging.level` system property controls the level. If not provided, the level defaults to `INFO`.
It is set in `docker-compose.dev.yml` for development environments. 



Installation
---
[Docker Engine](https://docs.docker.com/engine/install/)

[Docker-Compose](https://docs.docker.com/compose/install/)


### Clone the repo

```bash
git clone git@github.com:calacademy-research/antweb.git
```


### (Option 1): Staging | using s3 server via VPN

* Install `s3fs` (if running on an IBSS server, use the ansible role)
* Deprecated. See below:  From the lastpass note "Minio Bucket Credentials" copy the line antweb:... and paste into a file `~/.s3fs_antweb_key`
* Deprecated. Switched from minio to digitalocean:  Mount the antweb minio bucket on the machine

```bash
# this can be anywhere that you have write permission to, but ~/volumes/antweb is an easy place to remember and find
export ANTWEB_BUCKET_PATH=$HOME/volumes/antweb 
export ANTWEB_BACKUP_PATH=$HOME/volumes/antweb_backup 
export ANTWEB_S3FS_KEY=$HOME/.s3fs_antweb_key
s3fs antweb "$ANTWEB_BUCKET_PATH" -o passwd_file="$ANTWEB_S3FS_KEY" -o url=https://sfo3.digitaloceanspaces.com -o allow_other -o use_path_request_style -o nonempty
s3fs antweb-dbarchive "$ANTWEB_BUCKET_PATH" -o passwd_file="$ANTWEB_S3FS_KEY" -o url=https://sfo3.digitaloceanspaces.com -o allow_other -o use_path_request_style


Deprecated:
s3fs antweb "$ANTWEB_BUCKET_PATH" -o passwd_file="$ANTWEB_S3FS_KEY" -o url=https://slevin.calacademy.org:9000 -o allow_other -o use_path_request_style
s3fs antweb-dbarchive "$ANTWEB_BUCKET_PATH" -o passwd_file="$ANTWEB_S3FS_KEY" -o url=https://slevin.calacademy.org:9000 -o allow_other -o use_path_request_style
```

* Add the bucket and key path to a `.env` file in the project root

```bash
echo ANTWEB_BUCKET_PATH=$(printenv ANTWEB_BUCKET_PATH) >> .env
echo ANTWEB_BACKUP_PATH=$(printenv ANTWEB_BACKUP_PATH) >> .env
echo ANTWEB_S3FS_KEY=$(printenv ANTWEB_S3FS_KEY) >> .env
```

### (Option 2): Development | downloading files to local machine

*Note: repeat this process every week to fetch latest changes*

Use `rclone` to copy and synchronize web data from the server while connected to the VPN.

If you're on Ubuntu 18.04, get rclone from the script on the site, not from apt.

To configure rclone, add the following to the file `~/.config/rclone/rclone.conf`.

Get the secret_access_key from the live server rclone.conf.
Was:
In the lastpass note "Minio Bucket Credentials", copy the 48 character key after "antweb:"
  

Replace `<redacted>` with the antweb key.

```
$ cat ~/.config/rclone/rclone.conf
[digitalocean]
type = s3
provider = DigitalOcean
access_key_id = PIM6E5G3RZ6ANNAAJGKB
secret_access_key = <redacted>
endpoint = sfo3.digitaloceanspaces.com
acl = public-read

Deprecated:

$ cat ~/.config/rclone/rclone.conf
[minio]
type = s3
provider = Minio
access_key_id = antweb
secret_access_key = <redacted>
endpoint = https://slevin.calacademy.org:9000
acl = public-read
```


#### Project documents (~10G) (required)


`rclone sync` will make the destination look identical to the source! (This includes deleting files in the destination directory).

If you've made changes to these directories, `rclone copy` instead to only download new files from the source.

```bash
cd antweb
mkdir -p data/web
rclone sync --size-only -P --exclude "upload/**" minio:antweb/web/ data/web/

```
If you want the upload history, use this to grab files created in the last 30 days. Without a filter, the upload files
are 200G total. 
```
rclone sync --size-only -P --max-age=30d minio:antweb/web/upload data/web/upload

```

#### Images (~1.6 TB) (optional)
```bash
cd antweb
mkdir -p data/images
rclone sync --size-only -P --checkers 32 --fast-list minio:antweb/images data/images
```


#### Mounting into docker-compose


```
Add to fstab file using:
sudo vifs

root@antweb:~# cat /etc/fstab
digitalocean:/antweb	/mnt/antweb	fuse.rclonefs	
config=/root/.config/rclone/rclone.conf,default-permissions,vfs-cache-mode=full,attr-timeout=10s,dir-cache-time=24h,vfs-cache-max-age=24h,daemon-timeout=10m,use-server-modtime,log-file=/root/.config/rclone/rclone.log,log-level=NOTICE	0 0
digitalocean:/antweb-dbarchive	/mnt/backup	fuse.rclonefs	config=/root/.config/rclone/rclone.conf,default-permissions,vfs-cache-mode=full,s3-acl=private	0 0

root@antweb:~# df -h
Filesystem                     Size  Used Avail Use% Mounted on
digitalocean:antweb            1.0P     0  1.0P   0% /mnt/antweb
digitalocean:antweb-dbarchive  1.0P     0  1.0P   0% /mnt/backup

ls /mnt/antweb
```


These files will be ignored by git and the docker build daemon. You will need to mount the directory into the container
using the `ANTWEB_BUCKET_PATH` environment variable. 

docker-compose will read the .env file in the project directory, mounting the directory automatically.

Inside the antweb directory:

```bash
cd antweb
export ANTWEB_BUCKET_PATH=$(pwd)/data
echo ANTWEB_BUCKET_PATH=$(printenv ANTWEB_BUCKET_PATH) >> .env
```

If you don't clone the images to your machine, be sure to set `site.imgDomain=www.antweb.org` 
in your `AntwebResources.properties` file.

### Download the (hopefully soon to be) sanitized database

Create a full database dump from antweb and copy it to your machine

```bash
ssh user@antweb
mysqldump -h 127.0.0.1 -u antweb -p --all-databases --routines --single-transaction --quick --column-statistics=0 | gzip > /tmp/ant-currentDump.sql.gz
# Enter password:

scp user@antweb:/tmp/ant-currentDump.sql.gz ./

#You can find the most recent database snapshot here:
scp root@antweb.org:/root/ant-currentDump.sql.gz .
```

Load the database into docker volume mounted by the mysql container

*Important: Bring down the antweb mysql container if it exists before continuing*

```bash
docker-compose down
docker volume rm antweb_database
docker volume create antweb_database

CID=$(docker run -d --rm \
	-e MYSQL_ALLOW_EMPTY_PASSWORD=1 \
	-e MYSQL_DATABASE=ant \
	--mount source=antweb_database,target=/var/lib/mysql \
	 mysql:5-debian --innodb-buffer-pool-size=4G --innodb-log-file-size=512M --innodb-buffer-pool-instances=4)
	
sleep 15	# Wait for the container to start up. If you get ERROR 2002 (HY000): Can't connect to local MySQL server, keep waiting
docker exec -it $CID mysql -uroot ant -e "create user antweb@'%' identified by 'f0rm1c6'; grant all on *.* to antweb@'%' with grant option"
 
 
gunzip -c ./ant-currentDump.sql.gz | docker exec -i $CID sh -c "exec mysql -uroot ant"

# Run an optimize to regenerate index
docker exec -it $CID sh -c "exec mysqlcheck --all-databases --analyze -uroot" && docker stop $CID

# If ant-currentDump.sql.gz is in the antweb directory, remove the dump to reduce docker daemon build time
rm ant-currentDump.sql.gz
```

This creates a temporary mysql container and loads the database dump into a docker-managed volume. This volume persists after the container is removed.  It'll take 5-10 minutes for the import to complete. 

\#### After sanitized database is available: create an admin antweb user


#### Create caddy persistent data volume

We use [Caddy](https://caddyserver.com/) to automatically manage tls certificates and https redirection.
An external docker volume prevents it from being removed unintentionally. From the docs: 
> Caddy stores TLS certificates, private keys, OCSP staples, and other necessary information to the data directory. It should not be purged without an understanding of the implications.

```bash
docker volume create antweb_caddy_data
```

Running
---

### Docker overrides for development, staging, or production environments


docker-compose automatically reads the override file `docker-compose.override.yml` file in addition to `docker-compose.yml`. 
Create a symlink pointing to the desired override (dev, staging, prod) to have docker 
automatically load the correct configuration for your environment.

dev: project is mounted into /antweb/deploy so changes in the IDE are synced immediately to the container (see more development instructions below)

staging: project is compiled at build-time

production: additional networking configuration

### Building the containers

This project uses [Docker BuildKit](https://docs.docker.com/develop/develop-images/build_enhancements/) for some non-backwards compatible features.

Before building the containers, set the following environment variables (you can put the lines in `~/.bashrc` 
to set them automatically):

```bash
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1
```


### For active development (modification of source code)

Create the symlink to the dev override:

```bash
ln -sf docker-compose.dev.yml docker-compose.override.yml
```


To start the services: `docker-compose up -d`



To stop the services: `docker-compose down`

If you make changes to the code requiring `ant deploy` to be run again:

```bash
docker-compose exec antweb ant deploy
```


Note: if you point image domain at antweb.org, can't test image uploading/progressing

### Staging / production
Take a look at [deployment.md](doc/deployment.md) for staging/production specific instructions

The antweb bucket is mounted in the antweb container at `/mnt/antweb`
The database archive is mounted in the antweb container at `/mnt/backup`

