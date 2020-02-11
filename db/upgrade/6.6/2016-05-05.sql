alter table geolocale add column alt_bioregion varchar(64);
 
update geolocale set alt_bioregion = 'Nearctic' where id = 89;
update geolocale set alt_bioregion = 'Palearctic' where id = 140;
update geolocale set alt_bioregion = 'Australasia' where id = 125;
update geolocale set alt_bioregion = 'Indomalaya' where id = 117;
update geolocale set alt_bioregion = 'Palearctic' where id = 136;
update geolocale set alt_bioregion = "Oceania" where name = "United States";
update geolocale set alt_bioregion = 'Palearctic' where name = "India";
 
update museum set name = "Natural History Museum, London, U. K." where code = "BMNH";
update museum set name = "California Academy of Sciences, San Francisco, CA, USA" where code = "CASC";
update museum set name = "John T. Longino Collection, University of Utah, Salt Lake City, Utah, USA." where code = "JTLC";
update museum set name = "Museum of Comparative Zoology, Harvard University, Cambridge, MA, USA" where code = "MCZC";
update museum set name = "Muséum d’Histoire Naturelle, Geneva, Switzerland" where code = "MHNG";
update museum set name = "Museo Civico de Historia Natural “Giacomo Doria”, Genoa, Italy" where code = "MSNG";
update museum set name = "Naturhistorisches Museum, Basel, Switzerland" where code = "NHMB";
update museum set name = "Naturhistorisches Museum, Vienna, Austria" where code = "NHMW";
update museum set name = "Philip S. Ward Collection, University of California, Davis, CA, USA" where code = "PSWC";
update museum set name = "Bohart Museum of Entomology, University of California at Davis, CA, USA" where code = "UCDC";
update museum set name = "National Museum of Natural History, Washington, DC, USA" where code = "USNM";
update museum set name = "Museum für Naturkunde der Humboldt-Universität, Berlin, Germany" where code = "ZMHB";

update geolocale set bioregion = "Palearctic" where name = "Jammu and Kashmir";


