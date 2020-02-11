#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.88/2012-09-05.sql
#

# To correct for a field museum specimen file upload that caused links to break on the site...
# This specimen http://antweb.org/specimen.do?name=FMNH-INS45042 is now fixed

update specimen set taxon_name = "cerapachyinaecerapachys indet", subfamily = "cerapachyinae" where code = "FMNH-INS45042"; 

delete from taxon where taxon_name = "myrmicinaecerapachys indet";

alter table long_request add column cache_millis int(11), add column request_info varchar(300);

alter table long_request modify column request_info varchar(1000);

