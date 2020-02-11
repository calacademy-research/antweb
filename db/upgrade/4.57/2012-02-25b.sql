#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.57/2012-02-25b.sql
#


# To get rid of all sorts of empty artists.  Consolidate on uid = 1;

delete from artist_group where artist_id != 1 and artist_id in (select uid from artist where uid < 32 or (uid > 32 and uid < 53) or (uid > 53 and uid < 56) or uid = 62 or (uid > 64 and uid < 68) or uid = 73);
update image set artist = 1 where artist in (select uid from artist where uid < 32 or (uid > 32 and uid < 53) or (uid > 53 and uid < 56) or uid = 62 or (uid > 64 and uid < 68) or uid = 73);
delete from artist where uid != 1 and uid < 32 or (uid > 32 and uid < 53) or (uid > 53 and uid < 56) or uid = 62 or (uid > 64 and uid < 68) or uid = 73;


# To consolidate non-empty artists

update image set artist = 104 where artist = 103;
delete from artist where uid = 103;
delete from artist_group where artist_id = 103;

update image set artist = 76 where artist = 75;
delete from artist where uid = 75;
delete from artist_group where artist_id = 75;

update image set artist = 80 where artist = 61;
delete from artist where uid = 61;
delete from artist_group where artist_id = 61;

update image set artist = 71 where artist = 68;
delete from artist where uid = 68;
delete from artist_group where artist_id = 68;



# To get rid of unknown artist

update image set artist = 1 where artist = 90;
delete from artist where uid = 90;
delete from artist_group where artist_id = 90;


# Set inactive

update artist set active = 0 where uid = 91;
