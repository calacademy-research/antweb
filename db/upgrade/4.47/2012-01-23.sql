#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.47/2012-01-23.sql
#

delete from project where project_name = "projectsants";
delete from project where project_name = "globalants";
delete from login_project where project_name = "globalants" or project_name = "projectants";


# No good.  Need them.

insert into project (project_name, project_title, root) values ("projectsants", "Projects", "projects");
insert into project (project_name, project_title, root) values ("globalants", "Global", "global");

