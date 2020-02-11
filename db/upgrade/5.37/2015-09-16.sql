#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.37/2015-09-16.sql

update project set project_name = "madagascarants" where project_name = "madants";
update proj_taxon set project_name = "madagascarants" where project_name = "madants";
update proj_taxon_log set project_name = "madagascarants" where project_name = "madants";
update proj_taxon_log_detail set project_name = "madagascarants" where project_name = "madants"; 
update antwiki_taxon_country set project_name = "madagascarants" where project_name = "madants";
update login_project set project_name = "madagascarants" where project_name = "madants";
update favorite_images set project_name = "madagascarants" where project_name = "madants";

# Run once: delete from project where project_name = "czechrepublicants";
update project set project_name = "czechrepublicants" where project_name = "czechants";
update proj_taxon set project_name = "czechrepublicants" where project_name = "czechants";
update proj_taxon_log set project_name = "czechrepublicants" where project_name = "czechants";
update proj_taxon_log_detail set project_name = "czechrepublicants" where project_name = "czechants"; 
update antwiki_taxon_country set project_name = "czechrepublicants" where project_name = "czechants";
update login_project set project_name = "czechrepublicants" where project_name = "czechants";
update favorite_images set project_name = "czechrepublicants" where project_name = "czechants";

update project set project_name = "californiaants" where project_name = "calants";
update proj_taxon set project_name = "californiaants" where project_name = "calants";
update proj_taxon_log set project_name = "californiaants" where project_name = "calants";
update proj_taxon_log_detail set project_name = "californiaants" where project_name = "calants"; 
update antwiki_taxon_country set project_name = "californiaants" where project_name = "calants";
update login_project set project_name = "californiaants" where project_name = "calants";
update favorite_images set project_name = "californiaants" where project_name = "calants";

# Run once: delete from project where project_name = "saudiarabiaants";
update project set project_name = "saudiarabiaants" where project_name = "saudiants";
update proj_taxon set project_name = "saudiarabiaants" where project_name = "saudiants";
update proj_taxon_log set project_name = "saudiarabiaants" where project_name = "saudiants";
update proj_taxon_log_detail set project_name = "saudiarabiaants" where project_name = "saudiants"; 
update antwiki_taxon_country set project_name = "saudiarabiaants" where project_name = "saudiants";
update login_project set project_name = "saudiarabiaants" where project_name = "saudiants";
update favorite_images set project_name = "saudiarabiaants" where project_name = "saudiants";

#run once: delete from project where project_name = "unitedarabemiratesants";
update project set project_name = "unitedarabemiratesants" where project_name = "uaeants";
update proj_taxon set project_name = "unitedarabemiratesants" where project_name = "uaeants";
update proj_taxon_log set project_name = "unitedarabemiratesants" where project_name = "uaeants";
update proj_taxon_log_detail set project_name = "unitedarabemiratesants" where project_name = "uaeants"; 
update antwiki_taxon_country set project_name = "unitedarabemiratesants" where project_name = "uaeants";
update login_project set project_name = "unitedarabemiratesants" where project_name = "uaeants";
update favorite_images set project_name = "unitedarabemiratesants" where project_name = "uaeants";

alter table proj_taxon drop column antwiki_rev;
alter table proj_taxon add column source varchar(30);
alter table proj_taxon add column rev int(11);

