#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.74/2012-05-25.sql
#

update description_edit set content = "Dlussky &amp; Perfilieva, 2003" where title = "revdate" and taxon_name = "myrmeciinaearchimyrmex smekali";
update description_edit set content = "Dlussky &amp; Perfilieva, 2003" where title = "revdate" and taxon_name = "myrmeciinaearchimyrmex piatnitzkyi";
update description_edit set content = "Zhang, Sun &amp; Zhang, 1994" where title = "revdate" and taxon_name = "myrmicinaeaphaenogaster dumetora";
update description_edit set content = "Dlussky &amp; Rasnitsyn, 2009; Heterick &amp; Shattuck, 2011" where title = "revdate" and taxon_name = "dolichoderinaeeldermyrmex oblongiceps";
update description_edit set content = "Archibald, Cover &amp; Moreau, 2006." where title = "revdate" and taxon_name = "incertae_sedisafropone orapa";
update description_edit set content = "Archibald, Cover &amp; Moreau, 2006" where title = "revdate" and taxon_name = "incertae_sedisafropone oculata";
update description_edit set content = "Archibald, Cover &amp; Moreau, 2006" where title = "revdate" and taxon_name = "incertae_sedisafromyrma petrosa";
update description_edit set content = "26 January 2012" where title = "revdate" and taxon_name = "myrmicinaeacromyrmex octospinosus";
update description_edit set content = "Dlussky, 1997; Dlussky &amp; Rasnitsyn, 2009" where title = "revdate" and taxon_name = "formicinaedrymomyrmex fuscipennis";
update description_edit set content = "Dlussky, Wappler &amp; Wedmann, 2009<br>" where title = "revdate" and taxon_name = "incertae_sedisincertae_sedis miegi";
update description_edit set content = "Brown, 1958; Dlussky, 2009" where title = "revdate" and taxon_name = "ectatomminaegnamptogenys europaea";
update description_edit set content = "Wild &amp; Cuezzo, 2006" where title = "revdate" and taxon_name = "dolichoderinaegracilidris humiloides";
update description_edit set content = "Hong, Wu &amp; Ren, 2001" where title = "revdate" and taxon_name = "formicinaelasius inflatus";
update description_edit set content = "Heterick &amp; Shattuck, 2011" where title = "revdate" and taxon_name = "dolichoderinaeliometopum bogdassarovi";
update description_edit set content = "De Andrade &amp; Baroni Urbani, 1999" where title = "revdate" and taxon_name = "myrmicinaecephalotes alveolatus";



mysql> select taxon_name, content from description_edit where title = "revdate" and content like '%;%';
+---------------------------------------+--------------------------------------------------------------+
| taxon_name                            | content                                                      |
+---------------------------------------+--------------------------------------------------------------+
| myrmeciinaearchimyrmex smekali        | Dlussky &amp; Perfilieva, 2003                               | 
| myrmeciinaearchimyrmex piatnitzkyi    | Dlussky &amp; Perfilieva, 2003                               | 
| myrmicinaeaphaenogaster dumetora      | Zhang, Sun &amp; Zhang, 1994                                 | 
| dolichoderinaeeldermyrmex oblongiceps | Dlussky &amp; Rasnitsyn, 2009; Heterick &amp; Shattuck, 2011 | 
| incertae_sedisafropone orapa          | Archibald, Cover &amp; Moreau, 2006</span>.                  | 
| incertae_sedisafropone oculata        | Archibald, Cover &amp; Moreau, 2006                          | 
| incertae_sedisafromyrma petrosa       | Archibald, Cover &amp; Moreau, 2006                          | 
| myrmicinaeacromyrmex octospinosus     | 26&nbsp;January 2012                                         | 
| formicinaedrymomyrmex fuscipennis     | Dlussky, 1997; Dlussky &amp; Rasnitsyn, 2009                 | 
| incertae_sedisincertae_sedis miegi    | Dlussky, Wappler &amp; Wedmann, 2009<br>                     | 
| ectatomminaegnamptogenys europaea     | Brown, 1958; Dlussky, 2009                                   | 
| dolichoderinaegracilidris humiloides  | Wild &amp; Cuezzo, 2006                                      | 
| formicinaelasius inflatus             | Hong, Wu &amp; Ren, 2001                                     | 
| dolichoderinaeliometopum bogdassarovi | Heterick &amp; Shattuck, 2011                                | 
| myrmicinaecephalotes alveolatus       | De Andrade &amp; Baroni Urbani, 1999                         | 
+---------------------------------------+--------------------------------------------------------------+
15 rows in set (0.01 sec)


