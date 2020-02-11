
# This query attempts to find all of the taxons that are not specimens (determined by name) and not
# in the Bolton Catalog, that are in a project (other than Fossil Ants [or AllAntweb ants]).
select taxon_name from taxon   
 where taxon_name not in (select distinct taxon_name from proj_taxon  
                           where project_name = "worldants")
   and locate("0", taxon_name) = 0    
   and locate("1", taxon_name) = 0    
   and locate("2", taxon_name) = 0    
   and locate("3", taxon_name) = 0    
   and locate("4", taxon_name) = 0    
   and locate("5", taxon_name) = 0    
   and locate("6", taxon_name) = 0    
   and locate("7", taxon_name) = 0    
   and locate("8", taxon_name) = 0    
   and locate("9", taxon_name) = 0    
   and locate("-", taxon_name) = 0    
   and locate("undet", taxon_name) = 0    
   and taxon_name in (select distinct taxon_name from proj_taxon
                       where project_name != "allantwebants"
                         and project_name != "fossilants");






select t.taxon_name, pt.project_name from taxon t
  inner join proj_taxon pt on t.taxon_name = pt.taxon_name
  where pt.project_name != "worldants"
# where t.taxon_name not in (select distinct taxon_name from proj_taxon where project_name = "worldants") 
   and locate("0", t.taxon_name) = 0
   and locate("1", t.taxon_name) = 0
   and locate("2", t.taxon_name) = 0
   and locate("3", t.taxon_name) = 0
   and locate("4", t.taxon_name) = 0
   and locate("5", t.taxon_name) = 0
   and locate("6", t.taxon_name) = 0
   and locate("7", t.taxon_name) = 0
   and locate("8", t.taxon_name) = 0
   and locate("9", t.taxon_name) = 0
   and locate("-", t.taxon_name) = 0
   and locate("undet", t.taxon_name) = 0
 #  and taxon_name in (select distinct taxon_name from proj_taxon)
;