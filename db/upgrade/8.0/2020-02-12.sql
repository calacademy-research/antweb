
update artist set created = "2001-01-01 01:01:01" where id <= 112;


ALTER TABLE geolocale_taxon ENGINE=InnoDB;
ALTER TABLE museum_taxon ENGINE=InnoDB;
ALTER TABLE proj_taxon ENGINE=InnoDB;
ALTER TABLE bioregion_taxon ENGINE=InnoDB;

ALTER TABLE geolocale_taxon ENGINE=MyIsam;
ALTER TABLE museum_taxon ENGINE=MyIsam;
ALTER TABLE proj_taxon ENGINE=MyIsam;
ALTER TABLE bioregion_taxon ENGINE=MyIsam;


