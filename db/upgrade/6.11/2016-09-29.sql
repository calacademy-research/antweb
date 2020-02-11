
# create index access_group_idx on specimen (access_group);
alter table specimen drop access_group_idx;

# create index county_idx ON specimen (county);
alter table specimen drop county_idx;


# create index col_code_idx on specimen (collectioncode);
alter table specimen drop col_code_idx;

# create index col_museum_idx on specimen (museum);
alter table specimen drop index col_museum_idx;
 
 
alter table specimen drop index subfamily; 


# New. 6.13

#alter table object_edit charset=utf8;
ALTER TABLE object_edit CONVERT TO CHARACTER SET utf8;