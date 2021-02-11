Staging
---

Inside docker/httpd/sites-enabled:

Edit ServerName to match staging url in the following files
* 000-default.conf
* default-ssl.conf
* antweb_calacademy_org.conf


antweb_calacademy_org.conf:
* Edit the ServerName to match the staging url
* comment out the redirects in the virtualhost entries and uncomment the https upgrade

etc/AppResStageDocker.properties:
* set correct site.imgDomainApp and site.domain


Production
---
Set the ServerName to www.antweb.org in `default-ssl.conf` and `000-default.conf`