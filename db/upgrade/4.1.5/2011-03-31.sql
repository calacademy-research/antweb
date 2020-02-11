# 3.5/2011-03-17.sql
#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.1.1/2011-03-17.sql
#

# Implement a group/login redesign so that properties associated with groups are associated with login.
 
alter table description_edit add column is_manual_entry tinyint not null;
update description_edit set is_manual_entry = 0;
update description_edit set is_manual_entry = 1 where title != "taxonomichistory" and title != "taxonomictreatment";