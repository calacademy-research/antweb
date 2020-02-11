# 3.3.1/2011-02-09.sql
#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/3.3.1/2011-02-09.sql
#

# This will display unmatched description_edit records
select taxon_name, created from description_edit where created > '2011-01-27' and taxon_name not in (select taxon_name from taxon) order by created desc limit 140;

# or all
select taxon_name, created from description_edit where taxon_name not in (select taxon_name from taxon) order by created desc;


update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype5' where taxon_name like '%rematogaster hova-complex_morphotype5';
update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype4' where taxon_name like '%rematogaster hova-complex_morphotype4';
update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype3' where taxon_name like '%rematogaster hova-complex_morphotype3';
update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype2' where taxon_name like '%rematogaster hova-complex_morphotype2';
update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype1';

update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype5' where taxon_name like '%rematogaster hova-complex_morphotype5';
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype4' where taxon_name like '%rematogaster hova-complex_morphotype4';
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype3' where taxon_name like '%rematogaster hova-complex_morphotype3';
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype2' where taxon_name like '%rematogaster hova-complex_morphotype2';
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype1';






mysql> 
mysql> update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype5';
type1' where taxon_name like '%rematogaster hova-complex_morphotype4';
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype3';
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype2';
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype1';
Query OK, 8 rows affected (0.14 sec)
Rows matched: 8  Changed: 8  Warnings: 0

mysql> update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype4';
ERROR 1062 (23000): Duplicate entry 'myrmicinaecrematogaster hova-complex_morphotype1-taxanomicnotes' for key 2
mysql> update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype3';
ERROR 1062 (23000): Duplicate entry 'myrmicinaecrematogaster hova-complex_morphotype1-taxanomicnotes' for key 2
mysql> update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype2';
ERROR 1062 (23000): Duplicate entry 'myrmicinaecrematogaster hova-complex_morphotype1-taxanomicnotes' for key 2
mysql> update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype1';
ERROR 1062 (23000): Duplicate entry 'myrmicinaecrematogaster hova-complex_morphotype1-taxanomicnotes' for key 2
mysql> 
mysql> update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype5';
Query OK, 7 rows affected (0.00 sec)
Rows matched: 7  Changed: 7  Warnings: 0

mysql> update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype4';
Query OK, 5 rows affected (0.00 sec)
Rows matched: 5  Changed: 5  Warnings: 0

mysql> update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype3';
Query OK, 5 rows affected (0.00 sec)
Rows matched: 5  Changed: 5  Warnings: 0

mysql> update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype2';
Query OK, 7 rows affected (0.00 sec)
Rows matched: 7  Changed: 7  Warnings: 0

mysql> update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype1' where taxon_name like '%rematogaster hova-complex_morphotype1';
Query OK, 8 rows affected (0.00 sec)
Rows matched: 32  Changed: 8  Warnings: 0


#To fix.  Oops.  Those were all made into 'myrmicinaecrematogaster hova-complex_morphotype1'
# In description_edit 8 records total.  4 correct.  4 fixed.
update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype5' where edit_id = 59139;
update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype5' where edit_id = 59140;
update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype5' where edit_id = 59141;
update description_edit set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype5' where edit_id = 59154;

#in description_hist
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype2' where hist_id = 104;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype3' where hist_id = 103;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype4' where hist_id = 102;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype5' where hist_id = 101;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype5' where hist_id = 100;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype4' where hist_id = 99;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype3' where hist_id = 98;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype2' where hist_id = 97;

# select e.edit_id, e.taxon_name, h.hist_id, h.taxon_name from description_edit e join description_hist h on e.edit_id = h.edit_id where e.taxon_name like '%rematogaster hova-complex_morphotype%';

+---------+--------------------------------------------------+---------+--------------------------------------------------+
| edit_id | taxon_name                                       | hist_id | taxon_name                                       |
+---------+--------------------------------------------------+---------+--------------------------------------------------+
|   59135 | Crematogaster hova-complex_morphotype1           |      93 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59135 | Crematogaster hova-complex_morphotype1           |      94 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59135 | Crematogaster hova-complex_morphotype1           |      95 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59135 | Crematogaster hova-complex_morphotype1           |      96 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59136 | Crematogaster hova-complex_morphotype2           |      97 | myrmicinaecrematogaster hova-complex_morphotype2 | 
|   59137 | Crematogaster hova-complex_morphotype3           |      98 | myrmicinaecrematogaster hova-complex_morphotype3 | 
|   59138 | Crematogaster hova-complex_morphotype4           |      99 | myrmicinaecrematogaster hova-complex_morphotype4 | 
|   59139 | myrmicinaecrematogaster hova-complex_morphotype5 |     100 | myrmicinaecrematogaster hova-complex_morphotype5 | 
|   59139 | myrmicinaecrematogaster hova-complex_morphotype5 |     101 | myrmicinaecrematogaster hova-complex_morphotype5 | 
|   59138 | Crematogaster hova-complex_morphotype4           |     102 | myrmicinaecrematogaster hova-complex_morphotype4 | 
|   59137 | Crematogaster hova-complex_morphotype3           |     103 | myrmicinaecrematogaster hova-complex_morphotype3 | 
|   59136 | Crematogaster hova-complex_morphotype2           |     104 | myrmicinaecrematogaster hova-complex_morphotype2 | 
|   59135 | Crematogaster hova-complex_morphotype1           |     105 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59163 | myrmicinaecrematogaster hova-complex_morphotype1 |     111 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59164 | myrmicinaecrematogaster hova-complex_morphotype1 |     112 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59165 | myrmicinaecrematogaster hova-complex_morphotype1 |     113 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59141 | myrmicinaecrematogaster hova-complex_morphotype5 |     114 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59162 | crematogaster hova-complex_morphotype4           |     115 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59161 | crematogaster hova-complex_morphotype3           |     116 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59160 | crematogaster hova-complex_morphotype2           |     117 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59160 | crematogaster hova-complex_morphotype2           |     118 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59159 | crematogaster hova-complex_morphotype1           |     119 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59143 | Crematogaster hova-complex_morphotype4           |     120 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59145 | Crematogaster hova-complex_morphotype3           |     121 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59147 | Crematogaster hova-complex_morphotype2           |     122 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59176 | crematogaster hova-complex_morphotype2           |     123 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59149 | Crematogaster hova-complex_morphotype1           |     124 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59161 | crematogaster hova-complex_morphotype3           |     135 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59162 | crematogaster hova-complex_morphotype4           |     136 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59160 | crematogaster hova-complex_morphotype2           |     137 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59163 | myrmicinaecrematogaster hova-complex_morphotype1 |     138 | myrmicinaecrematogaster hova-complex_morphotype1 | 
|   59159 | crematogaster hova-complex_morphotype1           |     139 | myrmicinaecrematogaster hova-complex_morphotype1 | 

update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype5' where hist_id = 114;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype4' where hist_id = 115;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype3' where hist_id = 116;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype2' where hist_id = 117;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype2' where hist_id = 118;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype4' where hist_id = 120;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype3' where hist_id = 121;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype2' where hist_id = 122;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype2' where hist_id = 123;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype3' where hist_id = 135;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype4' where hist_id = 136;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype2' where hist_id = 137;
update description_hist set taxon_name = 'myrmicinaecrematogaster hova-complex_morphotype3' where hist_id = 121;

