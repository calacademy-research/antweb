
update specimen set decimal_longitude = null, decimal_latitude = null where code in ('xe00143','xe00144', 'xe00145', 'xe00147a', 'xe00147b','xe00150', 'xe00151', 'xe00152', 'xe00153', 'xe00156', 'xe00157', 'xe00159');

drop table if exists admin_alerts;
create table admin_alerts (
  id int(11)  NOT NULL auto_increment
, alert varchar(1000)
, acknowledged tinyint(4) default 0
, created timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
, primary key (id)
);


alter table geolocale add column rev int(11);

#update geolocale set is_valid = 0 where source = "Geonames";
