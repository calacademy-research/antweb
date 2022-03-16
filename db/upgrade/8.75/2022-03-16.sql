# We don't control how long author_date_html values are, so it's safer to store as text than varchar
alter table ant.taxon MODIFY author_date_html TEXT
