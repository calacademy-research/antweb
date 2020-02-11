# 3.3/2010-12-29.sql
#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/3.3/2010-12-29.sql
#
# Records that may be edited are stored in the description_edit table instead of description.
#   Description_edit does not have a proj_name column - they are only per taxon.
#
# Description_hist will record any modified records of description_edit.
#
# Description had 330002 records.  description_edit has 124237.

# Perform this once.  Commented out so as to not break script.
# rename Taxon table to taxon;
# alter table taxon add id int(10) NOT NULL auto_increment, add unique key (id);

drop table if exists description_edit;
create table description_edit select * from description
  where title in ("revdate","distribution","identification","biology","comments","notes","taxonomicnotes","taxonomichistory","taxonomictreatment","references" 
);
  
# Move them all of the taxonomicnotes to comments.  Curators can move them to taxanomicnotes as they like.  
# Problem with this.  The alter ignore key below would eliminate records for taxons that already have comments.
# Either use a comments2 or concatenate.  For now, leave notes as notes... undisplayed.
# update description_edit set title = "comments" where title = "notes";
  
alter table description_edit add edit_id int(10) NOT NULL auto_increment, add primary key (edit_id);

alter table description_edit drop column rankfocus;
alter table description_edit add column created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
alter table description_edit add column taxon_id int(10);
alter table description_edit drop column proj_name;

#alter table description_edit add constraint unique key (taxon_id, title);
# Note: null in the taxon_id subverts the constraint.  


# Should we deal with Bolton (proj_name = "worldants") before going forward?

alter ignore table description_edit add constraint unique key (taxon_name, title);

# How to get rid of duplicate records - no longer distinct by proj_name
# Perhaps ignore keyword useful?
# Question of how to retain maximum value remains.
# 331026 total records - 147747 remaining = 183279 duplicate records deleted.  

delete from description_edit where taxon_name = "";
# because of the above command (alter ignore) this will only delete 2 records (instead of 2352)

delete from description_edit where title = "proj_name";
# 23509 rows affected

update description_edit d_edit set d_edit.taxon_id = (select id from taxon where d_edit.taxon_name = taxon.taxon_name);  
# Populate description_edit taxon_id so that joins may be done via primary keys.  Should not be relied upon until
# taxon upload process is modified to never re-upload, instead to update.

drop table if exists description_hist;
Create table description_hist select * from description_edit where 1=0;
# Copy table structure but not data.  Data will be created
# Create a primary key on this table.
alter table description_hist add hist_id int(10) NOT NULL auto_increment, add primary key (hist_id);
alter table description_hist add access_group varchar(32) NOT NULL;
alter table description_hist modify column created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

# Remaining...  
# integrate taxon_id with process.  This could be hard.  Reuploaded authority files will delete and then
#   re-import creating new keys.  Could we change process to update if extant?
# What about storing the user that modified the record?
# User interface for description_hist?  Later.  Add user to description hist.

#Notes
# All proj_name titles from description_edit were first "ignored", then deleted.  Are we sure they are not used?
  # Is it OK to crunch down data using the ignore keyword above?  Could we be losing valuable data in doing so.
# How do records get uploaded in the first place?  These data flows will need to be modified.  
#  * Done.  UploadAction on Authority Files and Plazi.
# Modify upload and other processes to put records in description_edit.  Respect the proper functions.    
#   Done.
# delete description_edit in the case of empty string saved.
#   Done.

