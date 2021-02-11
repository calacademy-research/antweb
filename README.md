Antweb README.md file



See doc/fileMods.txt

See doc/releaseNotes.txt
     This contains all software release with detailed notes of features.

See doc/toDo.txt

See doc/install.txt

Prerequisites
---

### (Option 1): Staging | using s3 server via VPN

* Install `s3fs` (if running on an IBSS server, use the ansible role)
* From the lastpass note "Minio Bucket Credentials" copy the line antweb:... and paste into a file `~/.s3fs_antweb_key`
* Mount the antweb minio bucket on the machine

```bash
# this can be anywhere that you have write permission to, but ~/volumes/antweb is an easy place to remember and find
export ANTWEB_BUCKET_PATH=$HOME/volumes/antweb 
export ANTWEB_S3FS_KEY=$HOME/.s3fs_antweb_key
s3fs antweb "$ANTWEB_BUCKET_PATH" -o passwd_file="$ANTWEB_S3FS_KEY" -o url=https://slevin.calacademy.org:9000 -o allow_other -o use_path_request_style
```

* Add the bucket and key path to a `.env` file in the project root

```bash
echo ANTWEB_BUCKET_PATH=$(printenv ANTWEB_BUCKET_PATH) >> .env
echo ANTWEB_S3FS_KEY=$(printenv ANTWEB_S3FS_KEY) >> .env
```

### (Option 2): Development | downloading files to local machine



Use `rsync` to copy and synchronize web data from the server while connected to the VPN

*Note: repeat this process every week to fetch latest changes*

#### Project documents (~10G) (required)
```bash
rsync -ah --info=progress2 --exclude={'log/*', 'upload/*'} user@antweb:/mnt/antweb/web data/web
```

#### Images (~1.6 TB) (optional)
```bash
rsync -ah --info=progress2 user@antweb:/mnt/antweb/images data/images
```


#### Mounting into docker-compose

These files will be ignored by git and the docker build daemon. You will need to mount the directory into the container
using the `ANTWEB_BUCKET_PATH` environment variable. 

docker-compose will read the .env file in the project directory, mounting the directory automatically.

```bash
export ANTWEB_BUCKET_PATH=$(pwd)/data
echo ANTWEB_BUCKET_PATH=$(printenv ANTWEB_BUCKET_PATH) >> .env
```

If you don't clone the images to your machine, be sure to set `site.imgDomain=www.antweb.org` 
in your `AntwebResources.properties` file.

### Download the (hopefully soon to be) sanitized database

Download and unzip the database dump from antweb

```bash
scp user@antweb:/data/antweb/backup/db/ant-currentDump.sql.gz ./
gunzip ant-currentDump.sql.gz
```

Load the database into a data directory mounted by the mysql container

```bash
mkdir -p database
CID=$(docker run -d --rm \
	-e MYSQL_ALLOW_EMPTY_PASSWORD=1 \
	-e MYSQL_DATABASE=antweb \
	--mount type=bind,source="$(pwd)"/database,target=/var/lib/mysql \
	 mysql:5)
	
sleep 15	# Wait for the container to start up. If you get ERROR 2002 (HY000): Can't connect to local MySQL server, keep waiting
docker exec -i $CID sh -c "exec mysql -uroot antweb" < ./ant-currentDump.sql && docker stop $CID
```

This creates a temporary mysql container and generates the mysql data directory from the data in a folder on the machine. It'll take 5-10 minutes for the import to complete. Once it's done, you should see a `database/` directory inside antweb that's about 2.5GB.

\#### After sanitized database is available: create an admin antweb user


Running
---

### Docker overrides for development, staging, or production environments


docker-compose automatically reads the override file `docker-compose.override.yml` file in addition to `docker-compose.yml`. 
Create a symlink pointing to the desired override (dev, staging, prod) to have docker 
automatically load the correct configuration for your environment.

dev: project is mounted into /antweb/deploy so changes in the IDE are synced immediately to the container (see more development instructions below)

staging: project is compiled at build-time

production: additional networking configuration


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
