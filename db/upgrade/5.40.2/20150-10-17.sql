#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.40.2/2015-10-17.sql
#

delete from login_project where login_id in (select id from login where name in ('testLogin', 'MEsposito@calacademy.org', 'mark', 'antweb', 'mwilden', 'luke'));

delete from login where name = "mwilden";


create table server (is_down_time tinyint(4));
insert into server (is_down_time) values (0);

