#alter table geolocale add column map varchar(40);
#alter table geolocale add column specimenImage1 varchar(100);
#alter table geolocale add column specimenImage2 varchar(100);
#alter table geolocale add column specimenImage3 varchar(100);
#alter table geolocale add column specimenImage1Link varchar(160);
#alter table geolocale add column specimenImage2Link varchar(160);
#alter table geolocale add column specimenImage3Link varchar(160);
#alter table geolocale add column author varchar(128);
#alter table geolocale add column authorImage varchar(100);
#alter table geolocale add column authorbio text;
#alter table geolocale add column species_list_mappable tinyint(4);

#create table bak_project as select * from project;

# Here

alter table project drop column contents;


#alter table project drop column ... all of them listed above.  They are desc edit now.
alter table project drop column adm1;
alter table project drop column country;

drop table if exists login_country;
create table login_country as select distinct l.id login_id, p.country from login l, login_project lp, bak_project_db.bak_project p, geolocale g where l.id = lp.login_id and lp.project_name = p.project_name and p.country = g.name union select distinct l.id, g.parent from login l, login_project lp, bak_project_db.bak_project p, geolocale g where l.id = lp.login_id and lp.project_name = p.project_name and p.adm1 = g.name and g.parent != "Western Asia";

drop table if exists geolocale_taxon_log;
CREATE TABLE geolocale_taxon_log (
  log_id int(11) NOT NULL AUTO_INCREMENT,
  geolocale_id int(11) NOT NULL,
  curator_id int(11) NOT NULL,
  created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_current tinyint(4) NOT NULL,
  PRIMARY KEY (log_id)
);
  
drop table if exists geolocale_taxon_dispute;
CREATE TABLE geolocale_taxon_dispute (
  geolocale_id int(11) NOT NULL,
  taxon_name varchar(128) NOT NULL,
  source varchar(150) DEFAULT NULL,
  rev int(11) DEFAULT NULL,
  curator_id int(11) DEFAULT NULL,
  created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

drop table if exists geolocale_taxon_log_detail;
CREATE TABLE geolocale_taxon_log_detail (
  geolocale_id int(11) NOT NULL,
  taxon_name varchar(128) CHARACTER SET utf8 NOT NULL,
  created timestamp NOT NULL,
  subfamily_count int(11) DEFAULT 0,
  genus_count int(11) DEFAULT 0,
  species_count int(11) DEFAULT 0,
  specimen_count int(11) DEFAULT 0,
  image_count int(11) DEFAULT 0,
  log_id int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (log_id, geolocale_id, taxon_name)
);
  
  
alter table geolocale_taxon add column source varchar(30);
alter table geolocale_taxon add column rev int(11);
alter table geolocale_taxon add column is_introduced tinyint(4);

# These are to be dropped after successful deployment of 6.9
# alter table proj_taxon add column is_introduced tinyint(4);
# alter table project add column contents text;
# alter table project add column country varchar(64);
# alter table project add column adm1 varchar(128);

alter table proj_taxon drop column is_introduced;
update geolocale set is_live = 1 where id in (select geolocale_id from bak_project where is_live = 1) and georank = "country";

#alter table favorite_images add column geolocale_id int(11);

# Done to the live site...
# insert into project (project_name, project_title, is_live, source, bioregion, coords, taxon_subfamily_dist_json, specimen_subfamily_dist_json, species_list_mappable) values ("guianashieldants", "Guiana Shield", 1, "manual", "PROJECT", "(5.143333, -60.7624999999", "", "", 1);

alter table geolocale add column endemic_species_count int(11);

alter table proj_taxon drop index project_name;

alter table taxon add column default_specimen varchar(128);


 update project set is_live = 0 where geolocale_id is not null;



insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaeadelomyrmex sc04", null
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"dolichoderinaedorymyrmex (indet)", "antwiki"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"ponerinaemesoponera melanaria macra", null                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"formicinaeplagiolepis puncta", "antwiki"                                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"proceratiinaeproceratium sc01", null                                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"ponerinaeanochetus madagascarensis", "speciesListTool"                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"proceratiinaeproceratium scm01", null                                                                      );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"proceratiinaeproceratium scm02", null                                                                      );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaecalyptomyrmex (indet)", "antwiki"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaecarebara (indet)", "antwiki"                                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaeeurhopalothrix (indet)", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaeterataner (indet)", "antwiki"                                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"pseudomyrmecinaetetraponera (indet)", "antwiki"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (9,"formicinaecamponotus cylindricus", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (9,"formicinaecamponotus sericeus", null                                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"myrmicinaecarebara (indet)", "antwiki"                                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"pseudomyrmecinaetetraponera (indet)", "antwiki"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (12,"myrmicinaecardiocondyla (indet)", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (12,"dolichoderinaeochetellus (indet)", "antwiki"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"ponerinaeanochetus pattersoni", "antwiki"                                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"ponerinaeanochetus pubescens", "specimen"                                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"ponerinaeeuponera kipyatkovi", "antwiki"                                                                      );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaeaphaenogaster occidentalis", "specimen"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaecrematogaster senegalensis", "antwiki"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"ponerinaecryptopone testacea", "antwiki"                                                                      );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaemonomorium salomonis", "antwiki"                                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaepheidole nemoralis?", "specimen"                                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaepheidole nemoralispetax?", "specimen"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaestrumigenys hoplites", "specimen"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaestrumigenys laticeps", "specimen"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"dolichoderinaetapinoma aberrans", "specimen"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaetetramorium karthala", "antwiki"                                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (164,"ponerinaehypoponera eduardi", "antwiki"                                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaeaphaenogaster hesperia", "specimen"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"formicinaecamponotus atlantis hesperius", "specimen"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"formicinaecamponotus rufoglaucus feae", "specimen"                                                          );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"formicinaecataglyphis viatica", "antwiki"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"ponerinaehypoponera nivariana", "specimen"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaemonomorium hesperium", "specimen"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaemonomorium wilsoni", "specimen"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetemnothorax gracilicornis", "specimen"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetemnothorax gracilicornis nivarianus", "specimen"                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetemnothorax hesperius", "specimen"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaecrematogaster alluaudi", "specimen"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaecrematogaster alluaudi noualhieri", "specimen"                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"formicinaecamponotus guanchus", "specimen"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"ponerinaeafropone oculata", "speciesListTool"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"leptanillinaeleptanilla ortunoi", "antwiki"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaepheidole teneriffana", "specimen"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"formicinaecataglyphis cana", "specimen"                                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"formicinaecataglyphis viatica hispanica", "specimen"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (198,"formicinaecataglyphis viatica hispanica", "specimen"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"formicinaeplagiolepis pallescens maura", "specimen"                                                         );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"formicinaeplagiolepis schmitzii canariensis", "specimen"                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (198,"formicinaeformica fusca tombeuri", "specimen"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaeaphaenogaster strioloides", "antwiki"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaeaphaenogaster testaceopilosa", "specimen"                                                         );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaecrematogaster laestrygon canariensis", "antwiki"                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaemessor minor hesperius", "specimen"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaemonomorium medinae", "specimen"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaemyrmica curvithorax", "specimen"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaeoxyopomyrmex insularis", "specimen"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaeoxyopomyrmex santschii", "antwiki"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaesolenopsis canariensis", "specimen"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaestrongylognathus huberi dalmaticus", "speciesListTool"                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetemnothorax bimbache", "specimen"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetemnothorax cabrerae", "specimen"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetemnothorax risii", "specimen"                                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetetramorium caespitum fortunatarum", "specimen"                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetetramorium depressum", "specimen"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetrichomyrmex destructor", "antwiki"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetrichomyrmex mayri", "antwiki"                                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetemnothorax birgitae", "specimen"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (202,"myrmicinaetemnothorax canescens", "specimen"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (113,"formicinaerossomyrmex proformicarum", "antwiki"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"dorylinaeaenictus gracilis", "speciesListUpload"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaecalyptomyrmex vedda", "specimen"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaecamponotus megalonyx", "speciesListUpload"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaecamponotus misturus", "speciesListUpload"                                                         );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaecamponotus reticulatus", "speciesListUpload"                                                      );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaecardiocondyla nuda", "speciesListUpload"                                                          );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"dorylinaecerapachys keralensis", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaeformica gravelyi", "speciesListUpload"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"ponerinaehypoponera confinis aitkenii", "specimen"                                                          );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"ponerinaehypoponera confinis wroughtonii", "specimen"                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaelasius fuliginosus", "speciesListUpload"                                                          );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaelasius hirsutus", "speciesListUpload"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaelasius talpa", "speciesListUpload"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaelepisiota semenovi", "specimen"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"ponerinaeleptogenys hodgsoni", "speciesListUpload"                                                          );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"ponerinaeleptogenys minchinii", "specimen"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaeleptothorax acervorum", "speciesListUpload"                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemonomorium bidentata", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemonomorium wroughtoni", "specimen"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"ponerinaeodontomachus haematodus", "speciesListUpload"                                                      );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaepheidole flavens", "specimen"                                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaepheidole lammelinoda", "specimen"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaepheidole longipes", "antwiki"                                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaepheidole sepulchralis", "speciesListUpload"                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaepheidole striaticeps", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaepolyrhachis mindanaensis", "specimen"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaepolyrhachis villipes", "speciesListUpload"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"dolichoderinaetechnomyrmex incisus", "antwiki"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaetetramorium caespitum", "specimen"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaetetramorium curvispinosum_nr", "speciesListUpload"                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaetetramorium hb01", "speciesListUpload"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaetetramorium hb02", "speciesListUpload"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaetetramorium solomonensis", "specimen"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"pseudomyrmecinaetetraponera carbonaria", "speciesListUpload"                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"pseudomyrmecinaetetraponera difficilis", "speciesListUpload"                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaevollenhovia oblonga", "speciesListUpload"                                                         );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"dorylinaecerapachys para_sc01", "speciesListTool"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"proceratiinaediscothyrea sc01", "speciesListTool"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaemonomorium monomorium", "speciesListTool"                                                          );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"formicinaenylanderia amblyops", "speciesListTool"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"formicinaenylanderia madagascarensis", "speciesListTool"                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"formicinaenylanderia madagascarensis rufescens", "speciesListTool"                                           );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"ponerinaeodontomachus troglodytes", "speciesListTool"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"formicinaeparaparatrechina albipes", "speciesListTool"                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaepheidole mg150", "speciesListTool"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaepheidole punctulata", "speciesListTool"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaepheidole scm01", "speciesListTool"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaepheidole teneriffana", "speciesListTool"                                                           );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"formicinaeplagiolepis exigua", "speciesListTool"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"ponerinaeponera exotica", "speciesListTool"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"ponerinaeponera incerta", "speciesListTool"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaesolenopsis papuana", "speciesListTool"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"dolichoderinaetechnomyrmex mayri", "speciesListTool"                                                         );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"dolichoderinaetechnomyrmex pallipes", "speciesListTool"                                                      );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"amblyoponinaeprionopelta descarpentriesi", "speciesListTool"                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaenesomyrmex angulatus", "speciesListTool"                                                           );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaepheidole dodo", "speciesListTool"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaestrumigenys membranifera", "speciesListTool"                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (462,"amblyoponinaestigmatomma orizabanum", "adm1Specimen"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (462,"myrmicinaepheidole subdentata", "adm1Specimen"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (462,"pompilinaeaporus niger", "adm1Specimen"                                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (462,"sceliphrinaechalybion californicum", "adm1Specimen"                                                         );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (462,"brachycistidinaebrachycistis sp", "adm1Specimen"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaecardiocondyla sc01", "speciesListUpload"                                                           );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaeechinopla lineata", "speciesListUpload"                                                           );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaecephalotes eduarduli", "speciesListTool"                                                           );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"formicinaecamponotus darwinii", "speciesListTool"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"formicinaecamponotus hova boivini", "speciesListTool"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"formicinaecamponotus hova mixtellus", "speciesListTool"                                                      );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"formicinaecamponotus reticulatus", "speciesListTool"                                                         );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (14,"myrmicinaecardiocondyla nuda", "speciesListTool"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (9,"myrmicinaepheidole komori", "specimen"                                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (9,"myrmicinaemonomorium elongatum", "speciesListUpload"                                                          );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (9,"formicinaecamponotus niveosetosus madagascarensis", "specimen"                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (12,"myrmicinaepristomyrmex browni", "antwiki"                                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (12,"myrmicinaetetramorium sikorae", "antwiki"                                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (12,"myrmicinaetetramorium insolens", "speciesListUpload"                                                         );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (12,"ponerinaeponera exotica", "speciesListUpload"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (12,"formicinaenylanderia obscura bismarckensis", "antwiki"                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (12,"myrmicinaemonomorium pharaonis", "speciesListUpload"                                                         );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (12,"formicinaelepisiota frauenfeldi", "speciesListUpload"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (12,"ponerinaehypoponera johannae", "speciesListUpload"                                                           );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (12,"myrmicinaecardiocondyla obscurior", "speciesListUpload"                                                      );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"myrmicinaeterataner yt01", "speciesListUpload"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"myrmicinaeterataner mg10", "speciesListUpload"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"myrmicinaestrumigenys mandibularis", "antwiki"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"ponerinaeponera exotica", "speciesListUpload"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"myrmicinaepheidole teneriffana", "specimen"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"myrmicinaepheidole punctulata", "antwiki"                                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"formicinaenylanderia madagascarensis ellisii", "antwiki"                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"formicinaenylanderia bourbonica ngasiyana", "speciesListUpload"                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"myrmicinaenesomyrmex madecassus", "speciesListUpload"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"ponerinaeleptogenys truncatirostris", "antwiki"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"myrmicinaecrematogaster ranavalonae", "antwiki"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"myrmicinaecrematogaster castanea", "antwiki"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"formicinaecamponotus auropubens", "specimen"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (10,"myrmicinaecalyptomyrmex km01", "speciesListUpload"                                                           );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaemonomorium kmm01", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaecardiocondyla obscurior", "speciesListUpload"                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"formicinaenylanderia bourbonica ngasiyana", "speciesListUpload"                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"formicinaecamponotus hova fulvus", "specimen"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"dorylinaecerapachys mg10", "speciesListUpload"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaetetramorium naganum", "antwiki"                                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaetetramorium cognatum", "antwiki"                                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaestrumigenys mandibularis", "antwiki"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"ponerinaeponera exotica", "speciesListUpload"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaepheidole teneriffana", "specimen"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"formicinaenylanderia km01", "speciesListUpload"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaenesomyrmex madecassus", "speciesListUpload"                                                         );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaecrematogaster sewellii", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (2,"myrmicinaecrematogaster castanea", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaepheidole antillana", "speciesListTool"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaeterataner fhg-tran", "speciesListUpload"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaeterataner fhg-provo", "speciesListUpload"                                                           );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaeterataner fhg-hopl", "speciesListUpload"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaeterataner fhg-are", "speciesListUpload"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaestrumigenys mg06", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaepheidole teneriffana", "specimen"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaepheidole spinosa", "specimen"                                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"myrmicinaecrematogaster brunneola", "specimen"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"ponerinaeleptogenys mg01", "speciesListUpload"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaelepisiota capensis", "antwiki"                                                                      );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus sibreei", "speciesListUpload"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus mg130", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus mg087", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus mg086", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus mg084", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (9,"ponerinaebrachyponera luteipes", "specimen"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (140,"formicinaeprenolepis fisheri", "specimen"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (9,"myrmicinaecrematogaster castanea", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (9,"dolichoderinaedorymyrmex bicolor", "specimen"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (9,"myrmicinaepheidole teneriffana", "specimen"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (9,"ponerinaeponera mu01", "speciesListUpload"                                                                    );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"dolichoderinaetapinoma madeirense", "antwiki"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"formicinaecamponotus fallax", "antwiki"                                                                     );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"formicinaecamponotus herculeanus", "antwiki"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"formicinaecamponotus truncatus", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"formicinaeformica candida", "antwiki"                                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"formicinaeformica cinerea", "antwiki"                                                                       );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"formicinaeformica clara", "antwiki"                                                                         );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"formicinaeformica lemani", "antwiki"                                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"formicinaelasius myops", "antwiki"                                                                          );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"formicinaepolyrhachis relucens", "speciesListUpload"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"myrmicinaeharpagoxenus sublaevis", "antwiki"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"myrmicinaemyrmica bessarabicus", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"myrmicinaetemnothorax interruptus", "antwiki"                                                               );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"myrmicinaetemnothorax nigriceps", "antwiki"                                                                 );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (211,"myrmicinaetemnothorax tuberum", "antwiki"                                                                   );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus edmondi ernesti", "specimen"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus mg002", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus mg007", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus mg041", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus mg042", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus mg044", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (7,"formicinaecamponotus mg083", "speciesListUpload"                                                              );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"ponerinaeodontoponera transversa", "speciesListUpload"                                                      );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemyrmica vittata", "speciesListUpload"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemyrmica villosa", "speciesListUpload"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemyrmica tenuispina", "speciesListUpload"                                                          );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemyrmica rigatoi", "speciesListUpload"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemyrmica pseudorugosa", "antwiki"                                                                  );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemyrmica martensi", "speciesListUpload"                                                            );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemyrmica collingwoodi", "speciesListUpload"                                                        );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemyrmica brancuccii", "speciesListUpload"                                                          );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemyrmica boltoni", "speciesListUpload"                                                             );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemyrmica afghanica", "speciesListUpload"                                                           );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemeranoplus nepalensis", "specimen"                                                                );
insert into geolocale_taxon_dispute(geolocale_id, taxon_name, source) values (137,"myrmicinaemeranoplus hb01", "speciesListUpload"                                                             ); 

update geolocale set name = "Hong Kong (adm1)" where id = 472;
update geolocale set parent = "Eastern Asia", is_live = 1, region = "Asia", bioregion = "Palearctic" where id = 252; 

delete from project where is_live = 0;  // No need.  Why bother?

# Not on live site yet...

delete from proj_taxon where project_name in (select project_name from project where is_live = 0) or project_name not in (select project_name from project);
 

# finally ?
alter table geolocale_taxon add column is_endemic tinyint(4);
# alter table project drop column endemic_species_count;


