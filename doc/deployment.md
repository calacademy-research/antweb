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
