#!/usr/bin/env python3
"""Place corrections: the country and state a specimen is missing, proposed from its coordinates (report only).

Why (Brian Fisher, 2026-11-10, on the upload report of the California Academy of Sciences: "we would need the
casent specimen code and the field and value for updating TW ... Country is missing 714 specimens; Adm1 is
missing, but expected for these countries, 1,500 specimens; Not AntWeb valid countries 2 ... currently the list
is so long they are ignored: some of course can not be corrected but maybe some can be based on lat long or
other means"). The specimens of the Academy come from TaxonWorks every day, so a correction is entered in
TaxonWorks, for each collecting event. This tool writes the working list: what to enter, for which collecting
event, and why; and for the rest, what the record has, grouped so that a person can work through it.

It never writes to the database: every statement is a single select (coordinate_country.read_only).

  python3 place_corrections.py --group-id 1                the California Academy of Sciences
  python3 place_corrections.py --group "Field Museum"      one upload group by its name
  python3 place_corrections.py                             every group
  python3 place_corrections.py --shapes-source naturalearth    the country test on Natural Earth alone
  python3 place_corrections.py --states-source naturalearth    the state shapes from Natural Earth's states and provinces
  python3 place_corrections.py --states-source both            the corrections by geoBoundaries, and the two sources compared
  python3 place_corrections.py --states-source all --ocha-countries MDG,NIC,HND,UGA,PAN    ... and OCHA's files for these countries as the third
  python3 place_corrections.py --no-specimen-test              by the names and the mapping file alone (no read of the specimens)
  python3 place_corrections.py --group-id 1 --tw-archive /root/antweb-place-fix/taxonworksNightly.zip    with the load file for TaxonWorks
  python3 place_corrections.py --tw-archive <file> --tw-summary     what the archive alone says; no database

A second tool, not a mode of coordinate_country.py: the question is another one (which state, for the records
with a gap) and so are the lists. It loads coordinate_country.py, which must lie next to it, and uses its rule
for the country, its index, its reader of boundary files and its lock file.

What it proposes.

  A. State missing: a valid AntWeb country, no state, not an island-country specimen, the country has valid
     states in AntWeb, usable coordinates.
     1. The point must agree with the stored country by the rule of coordinate_country.py: inside it, or
        outside by no more than 1 km (--tolerance-km) or the specimen's stated error. Otherwise no proposal
        ("coordinates outside the stated country": it is in the conflicts report of that tool).
     2. The state shape that holds the point, from geoBoundaries gbOpen for the country's ISO code (or from
        Natural Earth's states), at the level and with the state that AntWeb's own specimens confirm (below).
     3. A point inside one state shape gets that state, always. The nearest other state within 25 km and
        its distance are given with the proposal; when it lies within the allowed distance (the stated
        error, "1 minute" is 1.85 km, or 1 km) the proposal is flagged "within the stated error of <state>",
        for a person to look at first. (Version 1 gave no proposal there, "two candidates": 4,412 records
        of the first real run, most in small units such as the districts of Mauritius.)
        A point outside every state shape (in the water, or in a gap of the file) gets the state within the
        allowed distance when there is exactly one, and no other country lies as near: the note of the
        proposal says "outside the shape, N km from it (coast)". With more than one state that near: "two
        candidates", both named. With another country that near (or the point inside it): no proposal ("in
        no state's shape, and another country within the allowed distance").
     4. One exception to 1, for the islets a country's shape leaves out. A point that the country's shape has
        in the water, farther out than the allowed distance, but that lies inside a state shape of the
        states' own file or within the allowed distance of one alone, gets that state; the note says "the
        country's shape has the point in the water, the states' file draws land here: an islet, or coordinates
        to check (N km from <country> as <shape> draws it)". Production, 2026-11-10: 380 specimens of
        Madagascar on Nosy Mangabe, Nosy Ankao, Nosy Ratsy and Nosy Manampao, 2 to 9 km off the coast of the
        country's shape, got no proposal. A point the country shapes put inside another country gets none.
     One function answers for a point (Corrections.locate), for a missing state, for a province that is
     converted and for the table of Madagascar by region, with every source of state shapes. Version 6 had
     the rule three times: the conversion wanted the point strictly inside a region, and left 2,423 specimens
     of the Academy with their province that lie on the coast up to 0.69 km outside OCHA's line (Nosy Be
     Lokobe, Foulpointe, Cap Est) and that the table by region, with a rule of its own, counted in a region.
  B. Country missing: with usable coordinates, the country by the rule of coordinate_country.py (inside
     exactly one, or in the water within the allowed distance of exactly one), then the state as in A.
     Without coordinates: no proposal; the records are grouped in not_derivable.csv with the place text.
  C. Country not valid in AntWeb ("Africa"): listed with what the record has; with coordinates the country
     the point is in is proposed, and the state when it is missing.

Island countries (Brian Fisher, 2026-11-10: TaxonWorks is filled as AntWeb needs it). AntWeb reads Hawaii as
the state of a specimen of the United States, or as its country, and stores the country United States with the
island country Hawaii (SpecimenUploadParse). So for TaxonWorks the correction is country United States and
state Hawaii. A state shape whose name is a valid island country of AntWeb (geolocale: georank country,
is_island 1, is_valid 1) with the specimen's country as its parent (geolocale.country) is proposed as the state,
by the island's AntWeb name, the basis saying "AntWeb files this under <country> as the island <name>"; it is
not listed as "not a state in AntWeb", and the table of names counts island shapes apart. A missing country
in such an island gets two lines: the country (the parent) and the state (the island). A specimen that has
its island country already is left out, as before.

Which shape is which AntWeb state: tested with AntWeb's own specimens, not approved by a person (Brian Fisher,
2026-11-10: "aren't we dealing with facts that can be tested and evaluated? ... georeference them to test").
AntWeb holds many specimens, of every upload group, that have both a state and coordinates. They are read once
(one select: a valid country, a state, usable coordinates, no island-country specimen; counted by country,
state and place). The stored state is taken as AntWeb's valid state (itself, or the valid state an old name
points to). For a country and a level of its boundary file, every such specimen that lies inside exactly one
shape is counted for (shape, state). Then, for each shape, in this order:
  - a row of the mapping file wins (adm1_shape_map.csv: the decisions the data cannot make);
  - an island country of AntWeb stays what it is;
  - a shape whose name is exactly the name of one valid AntWeb state of the country (capitals, accents and
    punctuation aside; an old name counts), the only shape of the level with that name, is that state. The
    specimens never throw this out; those that say otherwise are listed in to_decide.csv ("stored state
    differs from where the coordinates fall"). Version 4 let them: on staging six specimens filed under
    Haute Matsiatra lay in the shape Ihorombe, the shape lost its state and 6,986 specimens their proposal;
  - a looser name (without a word such as Province, Region, State; for Natural Earth every name the unit has;
    a "name" line of the mapping file) stands as well, unless the state has at least 20 located specimens
    (MIN_AGAINST) and fewer than half of them lie in the shape (CONTRADICTED_BELOW): then it is not used;
  - several shapes of a level with the name of one state: the name does not say which, the specimens do;
  - where no name is in use, "confirmed by specimens" decides: at least 5 specimens of the state lie in the
    shape (MIN_POINTS), they are at least 80% of the state's located specimens (MIN_OF_STATE), and at least 80%
    of the shape's specimens that have a state are filed under it (MIN_OF_SHAPE). This settles "Lower Uele"
    and "Bas-Uele" where no line of the mapping file does.
  mapping_tested.csv has every pair with what it rests on and the three numbers. What a person still has to
  decide is in to_decide.csv, and nothing else is: shapes that hold several AntWeb states (AntWeb is finer than
  the file), AntWeb states that lie in several shapes (the file is finer), names that stand against specimens
  (the stored state of those specimens differs from where their coordinates fall). mapping_suggestions.csv keeps the names most like a shape for the shapes the test
  could not settle, with what AntWeb's specimens in the shape are filed under.

Which level of the boundary files. Not assumed to be ADM1.
  - With specimens to test with (5 or more of the country, and a state with 5 located at the coarsest level):
    each level is tested, coarse to fine; the next finer level is looked at only while more AntWeb states lie
    inside a shape that is not their own than are settled. The level with the most AntWeb states confirmed by
    specimens or matched by name is used (the coarser one of two alike).
  - Without (or when the specimens confirm nothing): by the names, as in version 3. Two shares at each level:
    AntWeb's states that a shape names of all AntWeb's states of the country, and the shapes that are an AntWeb
    state of all shapes. The first level with both at least half; else the level with the largest share of
    shapes, used when half of its shapes are AntWeb states or, for the coarsest level only, half of AntWeb's
    states are matched. Else no level: the country gives no proposal ("AntWeb's states and the boundary file
    do not match: needs a mapping"), with the shape each record lies in. A finer level is never used on the
    states matched alone: its units often have the names of the states (version 1 took the communes of
    Chile, the districts of the Czech Republic, the provinces of Spain that way).
  The report has one line for each country: the levels looked at, what the specimens gave, the level used, why.
  A name spoilt when the boundary file was made is put right before it is compared (mend): the file of
  Chile has "RegiÃ³n de Antofagasta" for "Región de Antofagasta".
  The light ("simplified") files of geoBoundaries are used for the states: measured 2026-11-10 for Madagascar,
  Mexico and the United States, their lines lie within 100 m of the full files' for 99 in 100 points, and they
  are 2 to 50 times smaller. The country test uses the full files.

The mapping file, tools/place/adm1_shape_map.csv (next to this tool), has five kinds of line:
  country,level,shape,antweb_state[,new]   the state of a shape; wins over the test and the names. With "new" in
      the fifth cell the state is one AntWeb does not have yet (Seychelles: the shape Other Islands is the
      state Outer Islands): it is proposed, marked "state to add in the Geolocale Manager first" in the basis
      and the note, and listed in the report under "States to add". Without "new" a row whose state AntWeb
      does not have is not used.
  convert,country,unit,unit,...    AntWeb has two sets of valid states for the country and gives the coarser one
      up. Madagascar (Brian Fisher, 2026-11-10: convert to regions): AntWeb has the six provinces and the 22
      regions; a specimen whose stored state is a province and whose coordinates lie inside one region gets a
      correction: field adm1, current value the province, proposed value the region. The provinces are left
      out of the test and of the states the shapes are compared with. The point is placed as for a missing
      state (A.1 to A.4). With a province and no coordinates: "no coordinates: stays with the province";
      farther than the allowed distance from every region: "coordinates in no region's shape: stays with the
      province".
  within,country,state,unit        the province a region belongs to, for one check: the note of a converted
      specimen says "the stored province is the province of the region", or "stored province differs from the
      coordinates' region: check the coordinates" (production 2026-11-10: about 3,400 of the Academy).
  split,country,shape,unit level,unit,antweb_state,note    a shape of the state level that is several states
      today is told apart by the units of a finer level of geoBoundaries: a point in the shape gets the state
      of the unit it lies in; the unit * stands for every unit not named. Madagascar has 24 regions (Brian
      Fisher, 2026-11-10: "we need to keep this up to date") and the boundary files 22: Vatovavy-Fitovinany
      is Vatovavy and Fitovinany since 2021, the north of Analanjirofo is Ambatosoa since 2023, by the
      districts of the file MDG ADM2. A state AntWeb does not have yet is proposed and marked until it
      exists; the report prints the split lines used, with their notes (the law and its date), and the
      specimens and collecting events of each state. A point in a split shape and in no unit named: no
      proposal ("in a shape the mapping file splits, but in none of the units it names").
  name,country,level,shape,antweb_state,why    the shape's name is another name of the state. A name like any
      other, tested with specimens; nobody approves these lines one by one.

Three sources of state shapes (--states-source).
  geoboundaries (the default): geoBoundaries gbOpen, a file for each country and level, ADM1 to ADM4.
  naturalearth: Natural Earth "admin 1 states and provinces", 1:10m, public domain: one file of 4,596 units for
      the world (40.7 MB), pinned to the commit of the country file and to a checksum. One level, called NE in
      the mapping file. A unit has its ISO 3166-2 code, its ne_id, and names in many languages and
      alternative names: every one is a spelling when a unit is matched with an AntWeb state (NE_NAMES).
      The code and the ne_id are carried into corrections.csv, by_collecting_event.csv and mapping_tested.csv.
  ocha: UN OCHA's common operational datasets of administrative boundaries (COD-AB), from the Humanitarian Data
      Exchange (data.humdata.org): for a country the dataset cod-ab-<iso3>, found through HDX's API, its
      GeoJSON file fetched into the shapes folder as ocha-cod-ab-<iso3>.geojson.zip. They are the files the
      national offices give OCHA (Madagascar: the disaster management office BNGRC, 24 regions, 120 districts,
      reviewed 6 July 2026) and are kept current, for about 114 countries. HDX has no pinned commit: the report
      is the record of which release was used (sha256, HDX's dates, the layer's version), and says "NEW
      RELEASE" when the checksum is another one than on the last run. The layout of the zip is read from the
      file. The key of a unit is OCHA's P-code (MG26), in the column unit_code beside unit_source. When HDX
      refuses or cannot be reached and no file is in the folder, the run stops with the folder and the file
      name to put a download by hand. --ocha-countries MDG,KEN limits the countries taken from OCHA (a file is
      tens of megabytes).
  both, all: the corrections by geoBoundaries; then every country that has specimens to test with is looked up
      in the two (geoBoundaries, Natural Earth) or the three sources, and the report has "Sources compared":
      for each country and source the share of AntWeb's specimens with a state and coordinates that lie in the
      unit confirmed or matched for their state (agreement), in the unit of another state, in a unit with no
      AntWeb state, in no unit (the coast). A file mapping_tested_<source>.csv has each source's pairs.
  One country from another source: a line "source,<country>,<source>" in the mapping file. Madagascar is taken
      from OCHA: geoBoundaries' regions of Madagascar are OpenStreetMap's 22 and do not fit its own districts
      (the office's); with OCHA's file the 24 regions are shapes of their own and no split line is used.
      --ignore-source-lines does without these lines.

Consistency of the source. Where a source has the state level and the next level down, each unit of the lower
level should lie inside one state shape. Measured on a grid of about 40,000 points over the country: the report
says how many units of the lower level lie less than 97% inside one state shape, and names the worst. By
default for the countries the mapping file has split, convert or source lines for; --consistency for every
country looked at (the files of the next level are fetched for it). It is the check that shows a source whose
two levels come from different makers: nine tenths of geoBoundaries' district Vohipeno lie in the wrong region.

The load file for TaxonWorks (--tw-archive <the archive TaxonWorks writes for AntWeb every day, the .zip or its
data.tsv>; needs a group). In the format the TaxonWorks team asked for: tw_load.tsv, tab-delimited, UTF-8 with no
byte order mark, one header row, no quoting, line ends \\n, one line for each collecting event and predicate:
ce_code, predicate (Country, adm1, BiogeographicRegion), action (create: TaxonWorks holds no value; update: it
holds another), current_value (TaxonWorks' own value, from the archive, never AntWeb's: the loader skips a line
whose current value is not what TaxonWorks holds), new_value (AntWeb's valid name), latitude, longitude (for
reference), collecting_event_id (TaxonWorks' internal id). No line where TaxonWorks holds the value already.
The code joins the two: AntWeb's specimen.collectioncode is the archive's fieldNumber without "eventID:".
  Country   what AntWeb has for every specimen of the event, one valid country (stored, or proposed from the
            coordinates): create when the predicate is blank; update when it is an old name or spelling of
            that country, a name AntWeb does not know, or an island AntWeb files under the country.
  adm1      the state proposed from the coordinates (a missing state; a province of Madagascar to its region).
            Left out and listed in tw_unresolved.csv: proposals within the tolerance of another state (they
            are for a person to look at first; --tw-include-flagged puts them in), proposals at a place the
            country's shape leaves out (A.4; the same option puts them in), a state AntWeb does not have yet, a
            stored province that is not the province of the region, different proposals in one event, an
            event whose value in TaxonWorks is another one than AntWeb has. A proposal "outside the shape, N km
            from it (coast)" is in the load file: the note is for the reader of the lists.
  islands   Country = the parent country, adm1 = the island (United States, Hawaii): lines only where
            TaxonWorks differs. Where it holds another state for the island, nothing is proposed.
  BiogeographicRegion   create when the predicate is blank and every specimen of the event has the same
            bioregion in AntWeb. Where TaxonWorks holds another bioregion than AntWeb: tw_bioregion_differs.tsv
            (the same columns), not in the load file, for a person to decide.
tw_unresolved.csv says for each proposal that is not in the load file why: the code is not in the archive, is
on several collecting events, is blank or contains a blank, and the reasons above. The report counts collecting
events, not specimens, for each predicate and action, and says how many codes are not letters followed by
digits (TaxonWorks has one namespace for each prefix). Without --tw-archive these files are not written.

For TaxonWorks, in the lists. The Academy's project keeps country, state and bioregion as predicates (data attributes) of the
collecting event, and those are what AntWeb's upload reads (TaxonWorksTransformer: Country, adm1, adm2,
BiogeographicRegion). corrections.csv and by_collecting_event.csv have a column taxonworks_predicate with the
predicate of the field (Country or adm1) and a column biogeographic_region with the bioregion AntWeb's
definition gives for the place as proposed, to fill the predicate BiogeographicRegion from the same line: the
valid state's bioregion when it has exactly one, else the country's when it has exactly one, else blank (the
rule of place_fix.decide(); "none", "null" and blank mean none; a state that is an island country has the
island's).

Madagascar by region (in the report, when AntWeb has the provinces): every record of Madagascar of the group,
placed by the function that makes the proposals. For each region the specimens placed in it, how many of them
lie outside its shape (the coast), and how many have as stored state the region's province, the region itself,
another state or none; the records with no coordinates; the reasons of those not placed. A line says that the
records with a province placed here are as many as the proposals from a province to its region: the two counts
come from one function and must be the same. One more read of the database, for Madagascar alone, by collecting
event; from it stored_differs.csv is written.

New states. For each proposed state AntWeb does not have yet the report gives what the Geolocale Manager asks
for when a state is added (Brian Fisher: every new state gets its bounding box and centre looked up), measured
on the shape of the source and written as the form takes them: "Centroid (lat, lon)" and "Bounding box (west
lon, south lat, east lon, north lat)", with the unit's code. The centre lies inside the shape: the centroid, or,
when the centroid falls outside (islands, a crescent), the middle of the widest stretch of the largest part.

It writes one folder (default /root/antweb-place-fix/<date>-<time>-corrections/):

  corrections.csv          one line per specimen and field: specimen code, collecting event code, locality
                           code, field (country or adm1), current value, proposed value (AntWeb's valid
                           name), the basis in plain words, the nearest other state, the km to it, the flag
                           "within the stated error of <state>", a note (state to add; the province check;
                           outside the shape, N km from it (coast); a place the country's shape leaves out),
                           latitude, longitude, stated error, locality name, country, upload group, the
                           unit's ISO 3166-2 code and ne_id (Natural Earth), the predicate of TaxonWorks,
                           the bioregion, unit_code (OCHA's P-code; for Natural Earth the ISO 3166-2 code)
                           and unit_source. In the order country, state, collecting event.
  by_collecting_event.csv  the working list for TaxonWorks: one line per collecting event, field and current
                           value with the proposed value, the number of specimens, the first and last
                           specimen code, the locality name and the coordinates. A collecting event whose
                           specimens get different proposals is flagged and not to be entered as it stands. A
                           specimen with no collecting event code has a line of its own. With the columns
                           of corrections.csv from the nearest other state on.
  not_derivable.csv        one line per collecting event (per specimen when it has no code), field and
                           reason: no coordinates; coordinates outside the stated country; two candidates;
                           in no state's shape and another country within the allowed distance; the
                           boundary file's name is not a state in AntWeb; AntWeb's states and the boundary
                           file do not match; no boundary file; country has no states in AntWeb; stays with
                           the province. With the number of specimens and the place text of the record
                           (state, adm2, locality name, locality notes, bioregion).
  stored_differs.csv       for the curator's eye, Madagascar: the records whose stored province or state is
                           not the one their coordinates fall in, one line per collecting event, locality
                           name, stored value and place: specimens, first specimen code, the stored value
                           and what it is (a province, a state, no state of Madagascar in AntWeb), the
                           region by the coordinates and its province, the km from the point to the stored
                           province or state (to the nearest region of that province), the nearest other
                           region and the km to its border, coordinates, stated error, basis, note. In the
                           order region, stored value, most specimens first. A point 0.3 km from the
                           stored province lies on the line; one 40 km from it has wrong coordinates or
                           a wrong province. Only the header when the table of Madagascar is not made.
  mapping_tested.csv       country, level, shape, antweb_state (the cells of a line of adm1_shape_map.csv),
                           what the pair rests on, the state's specimens in the shape, their percent of the
                           state's and of the shape's, what the shape's specimens are filed under today, the
                           unit's ISO 3166-2 code and ne_id.
  to_decide.csv            what a person has to decide: shapes that hold several AntWeb states, AntWeb states
                           that lie in several shapes, shapes named as one state with the specimens of another.
  mapping_suggestions.csv  for the shapes that got no state: the AntWeb states with the most similar names, a
                           score, the records of this run in the shape, what its specimens are filed under.
                           Nothing is used from it.
  tw_load.tsv, tw_bioregion_differs.tsv, tw_unresolved.csv    with --tw-archive: see above.
  report.txt               first a summary of at most 60 lines: the totals for each field, the reasons, the
                           conversion, what the test gave, what is to decide, the states to add, the countries
                           with no level. Then the tables; with the states to add, their centroid and
                           bounding box for the Geolocale Manager.

The password is read from <project>/etc/db.properties and goes to docker through the environment, never on a
command line. Tests: tests/unit/test_place_corrections.py.
"""
import argparse
import codecs
import collections
import csv
import datetime
import difflib
import importlib.util
import json
import math
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("coordinate_country", os.path.join(HERE, "coordinate_country.py"))
cc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cc)

VERSION = "7"
LEVELS = ["ADM1", "ADM2", "ADM3", "ADM4"]
NE_LEVEL = "NE"                  # the one level of Natural Earth's states and provinces, as the mapping file names it
NE_STATES_NAME = "ne_10m_admin_1_states_provinces.geojson"       # Natural Earth 1:10m, public domain; pinned like the country file
NE_STATES_URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/" + cc.SHAPES_COMMIT + "/geojson/" + NE_STATES_NAME
NE_STATES_SHA256 = "22d0e3ad85eb3e27f17cabf8ba2d50e554fbc27a87796ff891d958185da62fb5"       # 40,726,851 bytes, 4,596 units
# The names of a unit of Natural Earth, in the order they are tried as spellings of an AntWeb state
NE_NAMES = ["name", "name_local", "name_en", "name_fr", "name_es", "name_pt", "name_de", "name_it", "name_nl", "name_pl", "name_sv", "name_hu", "name_id",
            "name_tr", "name_vi", "name_ru", "name_uk", "name_el", "name_ar", "name_fa", "name_ur", "name_he", "name_hi", "name_bn", "name_ja", "name_ko",
            "name_zh", "name_zht", "gn_name", "woe_name", "gns_name", "name_alt"]
MIN_SHARE = 0.5                  # the minimum for a level to be used: half of AntWeb's states matched, or half of its shapes
OTHER_KM = (5.0, 25.0)           # how far the nearest other state is looked for, in two steps
MIN_POINTS = 5                   # the test with specimens: at least this many specimens of the state lie in the shape,
MIN_OF_STATE = 0.8               # at least this share of the state's located specimens lie in the shape,
MIN_OF_SHAPE = 0.8               # and at least this share of the shape's specimens that have a state are filed under the state
CONTRADICTED_BELOW = 0.5         # a name is contradicted when fewer than this share of the state's located specimens lie in the shape
MIN_AGAINST = 20                 # ... and the state has at least this many located: six specimens are no evidence against a name
OCHA_API = "https://data.humdata.org/api/3/action/package_show?id=cod-ab-{iso}"       # HDX: the dataset of a country's COD-AB boundaries
OCHA_PAGE = "https://data.humdata.org/dataset/cod-ab-{iso}"
OCHA_AGENT = "Mozilla/5.0 (X11; Linux x86_64) place_corrections.py"                    # HDX refuses a client that does not name itself like a browser
OCHA_FILE = "ocha-cod-ab-{iso}.geojson.zip"                                             # the name of a country's file in the shapes folder
OCHA_LOCK = "ocha-cod-ab.lock.json"
ONE_STATE = 0.97                 # consistency of a source: a unit of the next level should lie at least this much inside one state shape
GRID_POINTS = 40000              # ... measured on a grid of about this many points over the country
MAX_CHECK_BYTES = 80000000       # ... and not measured when the file of the next level is larger than this
MIN_SCORE = 0.7                  # a suggestion of mapping_suggestions.csv is at least this like the shape's name
SUGGESTIONS = 3                  # at most this many suggestions for one shape
SIMPLIFIED = "_simplified"       # the light file of geoBoundaries: within 100 m of the full one for 99 in 100 points
MAP_FILE = os.path.join(HERE, "adm1_shape_map.csv")
UNIT_WORDS = {"province", "provincia", "provincie", "region", "regiao", "state", "estado", "department", "departamento", "departement",
              "district", "distrito", "county", "prefecture", "governorate", "oblast", "parish", "canton", "municipality", "territory",
              "of", "de", "del", "do", "da", "du", "the"}

# More words that say what kind of unit a name is. Left out when two names are compared for mapping_suggestions.csv
# only: a name that fits once these are left out is suggested to a person, never used by itself.
GENERIC_WORDS = UNIT_WORDS | {"city", "capital", "autonomous", "special", "federal", "metropolitan", "administrative", "area", "division",
                              "emirate", "republic", "kraj", "krai", "kray", "okrug", "voivodeship", "megye", "autonome", "autonoma", "autonomo",
                              "comunidad", "comunitat", "community", "principado", "foral", "national", "capitale", "ciudad", "d", "l"}
MADAGASCAR = "Madagascar"
PROVINCES = {"antananarivo", "antsiranana", "fianarantsoa", "mahajanga", "toamasina", "toliara"}     # the six provinces of Madagascar

NO_COORDINATES = "no coordinates"
OUTSIDE = "coordinates outside the stated country"
TWO = "two candidates"
NOT_A_STATE = "the boundary file's name is not a state in AntWeb"
NO_MATCH = "AntWeb's states and the boundary file do not match: needs a mapping"
STAYS = "no coordinates: stays with the province"
STAYS_OUTSIDE = "coordinates in no region's shape: stays with the province"
TO_ADD = "state to add in the Geolocale Manager first"
SAME_PROVINCE = "the stored province is the province of the region"
OTHER_PROVINCE = "stored province differs from the coordinates' region: check the coordinates"
NO_FILE = "no boundary file"
NO_STATES = "country has no states in AntWeb"
NO_SHAPE_HERE = "the boundary file draws no state at the point"
NO_SPLIT_UNIT = "in a shape the mapping file splits, but in none of the units it names"
DIFFERS = "stored state differs from where the coordinates fall"
TERRITORY = "the point is in a territory AntWeb lists as a country of its own"
AT_SEA = "in the water, farther than the allowed distance from any country"
NO_COUNTRY_HERE = "the point is in an area with no AntWeb country"
LEFT_OUT = "the country's shape has the point in the water, the states' file draws land here: an islet, or coordinates to check"
ANOTHER_COUNTRY = "in no state's shape, and another country within the allowed distance"
COAST = "outside the shape, {km} km from it (coast)"            # the note of a proposal for a point in no state shape, near one


# ---------------------------------------------------------------- AntWeb's states and the names of the shapes

def plain(name):
    """A name without capitals, accents or punctuation: Amoron'i Mania and amoron i mania are the same."""
    decomposed = unicodedata.normalize("NFKD", name or "")
    text = "".join(c for c in decomposed if not unicodedata.combining(c)).casefold()
    return " ".join(re.sub(r"[^0-9a-z]+", " ", text).split())


def loose(name):
    """The plain name without the words Province, Region, State ...: Fianarantsoa Province is Fianarantsoa."""
    words = [w for w in plain(name).split() if w not in UNIT_WORDS]
    return " ".join(words)


MOJIBAKE = re.compile("[\u00c2-\u00f4][\u0080-\u00bf\u0152\u0153\u0160\u0161\u0178\u017d\u017e\u0192\u02c6\u02dc\u2013-\u203a\u20ac\u2122]")
SHAPE_NAME = re.compile(rb'"shapeName"\s*:\s*"((?:[^"\\]|\\.)*)"')


def mend(name):
    """A name whose UTF-8 letters were read as Latin-1 when the boundary file was made, put right: the file of
    Chile has "RegiÃ³n de Antofagasta" for "Región de Antofagasta". Any other name comes back as it is."""
    if not MOJIBAKE.search(name):
        return name
    for codec in ("latin-1", "cp1252"):
        try:
            return name.encode(codec).decode("utf-8")
        except UnicodeError:
            pass
    return name


def shape_names(path):
    """The shape names of a boundary file, without reading its lines: enough to see how a level fits."""
    with open(path, "rb") as f:
        return [mend(json.loads(b'"' + m.group(1) + b'"')) for m in SHAPE_NAME.finditer(f.read())]


def generic(name):
    """The plain name without any word that says what kind of unit it is (Jakarta Special Capital Region is
    jakarta). A name made of such words alone (Distrito Federal) stays whole."""
    words = [w for w in plain(name).split() if w not in GENERIC_WORDS]
    return " ".join(words) or plain(name)


def variants(name):
    """The name and its parts: West Sepik (Sandaun) Province is also West Sepik and Sandaun, Cataluña/Catalunya
    also Cataluña and Catalunya."""
    out = [generic(name)]
    for part in re.split(r"[/()\[\]]", name):
        g = " ".join(w for w in plain(part).split() if w not in GENERIC_WORDS)       # a part that only says the kind of unit is none
        if g and g not in out:
            out.append(g)
    return out


def alike(a, b):
    """How alike two names are, 0 to 1, both already made generic. 1: the same. Else the larger of: the share of
    letters in the same order (difflib), and, when every word of one is a word of the other, 0.75 to 0.95 by
    how much of the longer name the shorter one is."""
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0
    if a.replace(" ", "") == b.replace(" ", ""):
        return 0.98
    score = difflib.SequenceMatcher(None, a, b).ratio()
    wa, wb = set(a.split()), set(b.split())
    if wa <= wb or wb <= wa:
        score = max(score, 0.75 + 0.2 * min(len(a), len(b)) / max(len(a), len(b)))
    return score


def likeness(shape, name):
    """How alike a shape name and an AntWeb name are: the best of the shape name's parts."""
    b = generic(name)
    return round(max(alike(a, b) for a in variants(shape)), 2)


class States:
    """The states of geolocale (georank adm1): for each valid country its valid states and the names that mean one."""

    def __init__(self, rows, countries):
        """rows: (name, parent, is_valid, valid_name) of every adm1 row."""
        self.valid = collections.defaultdict(set)                # country -> valid state names
        self.by_plain = collections.defaultdict(dict)            # country -> plain name -> valid state
        self.by_loose = collections.defaultdict(lambda: collections.defaultdict(set))
        self.old = collections.defaultdict(lambda: collections.defaultdict(list))   # country -> valid state -> its old names
        old = []
        for name, parent, is_valid, valid_name in rows:
            country = countries.resolve(parent)
            if country is None or not name:
                continue
            if str(is_valid) == "1":
                self.valid[country.name].add(name)
            elif cc.clean(valid_name):
                old.append((country.name, name, cc.clean(valid_name)))
        for country, names in self.valid.items():
            for name in names:
                self.by_plain[country][plain(name)] = name
                self.by_loose[country][loose(name)].add(name)
        for country, name, valid_name in old:                    # an old name is a spelling of its valid state
            if valid_name in self.valid[country]:
                self.by_plain[country].setdefault(plain(name), valid_name)
                self.old[country][valid_name].append(name)

    def of(self, country):
        return sorted(self.valid.get(country, ()))

    def find(self, country, name):
        """(valid state, how) for a name, or (None, ""): the name itself, an old name, or without the unit word."""
        state = self.by_plain.get(country, {}).get(plain(name))
        if state is not None:
            return state, "name"
        states = self.by_loose.get(country, {}).get(loose(name), ()) if loose(name) else ()
        if len(states) == 1:
            return next(iter(states)), "name without the unit word"
        return None, ""

    def valid_of(self, country, name):
        """The valid state a stored name means: itself, or the valid state an old name points to. None otherwise."""
        return self.by_plain.get(country, {}).get(plain(name))

    def closest(self, country, shape, limit=SUGGESTIONS):
        """The valid states of the country most like a shape name: [(score, state, the old name that was like it
        or "")], best first, at least MIN_SCORE. For a person to look at: nothing is matched by this."""
        found = []
        for state in self.valid.get(country, ()):
            best = (likeness(shape, state), "")
            for name in self.old.get(country, {}).get(state, ()):
                score = likeness(shape, name)
                if score > best[0]:
                    best = (score, name)
            if best[0] >= MIN_SCORE:
                found.append((best[0], state, best[1]))
        return sorted(found, key=lambda x: (-x[0], x[1]))[:limit]


def map_lines(path):
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8", newline="") as f:
        return [[cell.strip() for cell in line] for line in csv.reader(row for row in f if not row.lstrip().startswith("#")) if line]


PREDICATE = {"country": "Country", "adm1": "adm1"}                # the predicate of TaxonWorks (a data attribute of the collecting event) for a field


def no_bioregion(value):
    """Geolocale.noBioregion, as in place_fix.py: blank, "none" and "null" mean no bioregion."""
    v = (value or "").strip()
    return "" if v == "" or v.lower() == "none" or v == "null" else v


class Bioregions:
    """The bioregion AntWeb's definition gives for a place, by the rule of place_fix.decide(): the valid state's
    when it has exactly one (a bioregion and no second one), else the country's when it has exactly one, else
    none. rows: (name, georank, parent, is_valid, bioregion, alt_bioregion) of the place rows."""

    def __init__(self, rows, countries, states):
        self.countries, self.states = countries, states
        self.of_country, self.of_state = {}, {}
        self.names = set()                                       # every bioregion a valid place of AntWeb has
        for name, georank, parent, is_valid, bioregion, alt in rows:
            if str(is_valid) != "1" or not name:
                continue
            self.names |= {x for x in (no_bioregion(bioregion), no_bioregion(alt)) if x}
            one = "" if no_bioregion(alt) else no_bioregion(bioregion)
            if georank == "adm1":
                country = countries.resolve(parent)
                if country is not None:
                    self.of_state[(country.name, name)] = one
            else:
                self.of_country[name] = one

    def of(self, country, state=""):
        """The bioregion to enter for a country and a state (AntWeb's valid names, or a state to add). A state
        that is an island country of AntWeb (Hawaii) has the island's."""
        if state:
            valid = self.states.valid_of(country, state) or state
            one = self.of_state.get((country, valid), "")
            if not one:
                island = self.countries.resolve(valid)
                if island is not None and island.is_island:
                    one = self.of_country.get(island.name, "")
            if one:
                return one
        return self.of_country.get(country, "")


def read_map(path):
    """The rows of adm1_shape_map.csv that name the state of a shape: [(country, level, shape, AntWeb state)].
    Notes (#), the header and the lines of the other kinds (read_rules) are left out."""
    return [tuple(line[:4]) for line in map_lines(path) if len(line) >= 4 and line[1].upper() in LEVELS + [NE_LEVEL]]


class Rules:
    """The other lines of adm1_shape_map.csv. new: the rows (as read_map gives them) whose state AntWeb does not
    have yet. convert: country -> the coarser units that are converted to the states of the shapes. within:
    country -> plain state name -> the coarser unit it belongs to. names: (country, level, shape, state) of the
    "name" lines: the shape's name is another name of the state, tested with specimens like any name. splits:
    (country, shape, level, unit, state, note) of the "split" lines: the shape of the state level is told apart
    by the units of a finer level; the unit "*" stands for every unit not named."""

    def __init__(self, new=(), convert=None, within=None, names=(), splits=(), sources=None):
        self.sources = dict(sources or {})                       # country -> the source of its state shapes: geoboundaries, naturalearth or ocha
        self.new = set(new)
        self.convert = dict(convert or {})
        self.within = {country: {plain(state): unit for state, unit in table.items()} for country, table in (within or {}).items()}
        self.names = list(names)
        self.splits = list(splits)


def read_rules(path):
    new, convert, within, names, splits, sources = set(), {}, collections.defaultdict(dict), [], [], {}
    for line in map_lines(path):
        kind = line[0].lower()
        if kind == "convert" and len(line) >= 3:
            convert.setdefault(line[1], []).extend(cell for cell in line[2:] if cell)
        elif kind == "within" and len(line) >= 4:
            within[line[1]][line[2]] = line[3]
        elif kind == "source" and len(line) >= 3 and line[2].lower() in SOURCE_NAMES:
            sources[line[1]] = line[2].lower()
        elif kind == "name" and len(line) >= 5 and line[2].upper() in LEVELS + [NE_LEVEL]:
            names.append((line[1], line[2].upper(), line[3], line[4]))
        elif kind == "split" and len(line) >= 6 and line[3].upper() in LEVELS:
            splits.append((line[1], line[2], line[3].upper(), line[4], line[5], line[6] if len(line) > 6 else ""))
        elif len(line) >= 5 and line[1].upper() in LEVELS + [NE_LEVEL] and line[4].lower() == "new":
            new.add(tuple(line[:4]))
    return Rules(new, convert, within, names, splits, sources)


ISLAND = "island country"
SOURCE_NAMES = ("geoboundaries", "naturalearth", "ocha")          # the sources of state shapes, as --states-source and the mapping file name them
SPECIMENS = "specimens"                                           # how a shape got its state: AntWeb's specimens in it are filed under it
NEW = "mapping file, a " + TO_ADD                                 # by a row of the mapping file whose state AntWeb does not have yet
SPLIT = "mapping file, which splits the shape"                    # the state is that of the finer unit the point lies in
NAMED = "name the mapping file gives"                             # a "name" line: a name like any other, tested with specimens
BY_FILE = ("mapping file", NEW, SPLIT)


class Evidence:
    """AntWeb's own specimens that have both a state and coordinates, of every upload group: what the shapes are
    tested with. points[valid country] = [(valid state, latitude, longitude, specimens)]."""

    def __init__(self, countries, states):
        self.countries, self.states = countries, states
        self.points = collections.defaultdict(list)
        self.totals = collections.Counter()

    def add(self, country, adm1, lat, lon, count):
        self.totals["specimens with a country, a state and coordinates, not of an island country"] += count
        place = self.countries.resolve(country.strip())
        if place is None:
            self.totals["left out: a country AntWeb does not know"] += count
            return
        state = self.states.valid_of(place.name, adm1.strip())
        if state is None:
            self.totals["left out: a state AntWeb does not know as a state of the country"] += count
            return
        try:
            lat, lon = float(lat), float(lon)
        except ValueError:
            lat = lon = 0.0
        if not cc.usable(lat, lon):
            self.totals["left out: coordinates that cannot be used"] += count
            return
        self.totals["used for the test"] += count
        self.points[place.name].append((sys.intern(state), lat, lon, count))


# a shape: its name, other names, ISO 3166-2 code and ne_id (Natural Earth), P-code (OCHA)
Unit = collections.namedtuple("Unit", "name spellings code ident pcode", defaults=((), "", "", ""))


def thin(ring, metres=None):
    """A ring without the points closer than metres to the last one kept (the line moves by less than that)."""
    metres = cc.THIN_M if metres is None else metres
    if len(ring) < 5:
        return ring
    limit = (metres / 1000.0 / cc.KM_PER_DEGREE) ** 2
    scale = math.cos(math.radians(ring[0][1]))
    kept = [ring[0]]
    for x, y in ring[1:-1]:
        dx, dy = (x - kept[-1][0]) * scale, y - kept[-1][1]
        if dx * dx + dy * dy >= limit:
            kept.append((x, y))
    kept.append(ring[-1])
    return kept if len(kept) >= 4 else ring


def geojson_features(stream, block=1 << 20):
    """The features of a GeoJSON FeatureCollection, one at a time, from a stream of bytes: a file of 100 MB is not
    held in memory. Each is the decoded feature (properties, geometry)."""
    decoder = json.JSONDecoder()
    read = codecs.getincrementaldecoder("utf-8")("replace")
    text, more = "", True

    def fill():
        nonlocal text, more
        data = stream.read(block)
        if not data:
            more = False
            text += read.decode(b"", True)
        else:
            text += read.decode(data)

    while more and '"features"' not in text:
        fill()
    at = text.find('"features"')
    if at < 0:
        return
    while more and "[" not in text[at:]:
        fill()
    at = text.find("[", at) + 1
    while True:
        while True:                                              # to the next feature, or the end of the list
            rest = text[at:].lstrip(" \t\r\n,")
            if rest or not more:
                break
            text, at = "", 0
            fill()
        text, at = rest, 0
        if not text or text[0] == "]":
            return
        while True:
            try:
                feature, end = decoder.raw_decode(text)
                break
            except ValueError:
                if not more:
                    return                                       # a file cut short: what was whole is given
                fill()
        text = text[end:]
        yield feature



Outline = collections.namedtuple("Outline", "south north west east lat lon centre parts")


def holds(ring, x, y):
    """True when the ring (a list of (longitude, latitude)) holds the point."""
    inside = False
    x1, y1 = ring[-1]
    for x2, y2 in ring:
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
            inside = not inside
        x1, y1 = x2, y2
    return inside


def outline(rings):
    """What the Geolocale Manager asks of a new state, from its shape (rings: each a list of (longitude,
    latitude); a ring inside another is a hole): an Outline with the bounding box and a centre that lies inside
    the shape. The centre is the centroid (the parts weighed by their area, the holes taken out); when the
    centroid lies outside the shape (a crescent, a group of islands), it is the middle of the widest stretch of
    the shape along the latitude through the middle of its largest part. A shape that reaches across the 180
    meridian is measured with its western longitudes counted on from 180 (west is then larger than east)."""
    rings = [list(ring) for ring in rings if len(ring) >= 3]
    if not rings:
        return None
    xs = [p[0] for ring in rings for p in ring]
    if max(xs) - min(xs) > 180.0:
        rings = [[(x + 360.0 if x < 0.0 else x, y) for x, y in ring] for ring in rings]
    boxes = [(min(p[0] for p in ring), max(p[0] for p in ring), min(p[1] for p in ring), max(p[1] for p in ring)) for ring in rings]
    west, east = min(b[0] for b in boxes), max(b[1] for b in boxes)
    south, north = min(b[2] for b in boxes), max(b[3] for b in boxes)
    scale = max(math.cos(math.radians((south + north) / 2.0)), 0.01)

    def measure(ring):
        """(area, longitude and latitude of the centroid) of one ring, on a flat map around the shape."""
        x0, y0 = ring[0]
        area = cx = cy = 0.0
        ax, ay = (ring[-1][0] - x0) * scale, ring[-1][1] - y0
        for x, y in ring:
            bx, by = (x - x0) * scale, y - y0
            cross = ax * by - bx * ay
            area += cross
            cx += (ax + bx) * cross
            cy += (ay + by) * cross
            ax, ay = bx, by
        if area == 0.0:
            return 0.0, x0, y0
        return abs(area) / 2.0, x0 + cx / (3.0 * area) / scale, y0 + cy / (3.0 * area)

    within = lambda x, y, skip=-1: sum(1 for i, (ring, b) in enumerate(zip(rings, boxes))
                                       if i != skip and b[0] <= x <= b[1] and b[2] <= y <= b[3] and holds(ring, x, y))
    measured = [measure(ring) for ring in rings]
    total = lon = lat = 0.0
    for i, (area, x, y) in enumerate(measured):
        if within(rings[i][0][0], rings[i][0][1], i) % 2:        # a ring inside another: a hole
            area = -area
        total, lon, lat = total + area, lon + area * x, lat + area * y
    centre = "the centroid of the shape"
    if total > 0.0:
        lon, lat = lon / total, lat / total
    if total <= 0.0 or within(lon, lat) % 2 == 0:
        largest = max(range(len(rings)), key=lambda i: measured[i][0])
        lat = measured[largest][2]
        crossings = sorted(x1 + (lat - y1) * (x2 - x1) / (y2 - y1) for ring in rings for (x1, y1), (x2, y2) in zip(ring, ring[1:] + ring[:1])
                           if (y1 > lat) != (y2 > lat))
        stretches = [(b - a, (a + b) / 2.0) for a, b in zip(crossings[0::2], crossings[1::2])]
        if stretches:
            lon = max(stretches)[1]
            centre = "a point inside the largest part (the centroid of the shape lies outside it)"
        else:
            lon, lat = rings[largest][0]
            centre = "a point of the outline (no point inside was found)"
    back = lambda x: x - 360.0 if x > 180.0 else x
    return Outline(south, north, back(west), back(east), lat, back(lon), centre, len(rings))


class GeoSource:
    """geoBoundaries gbOpen as the source of state shapes: for each country a file for each level ADM1 to ADM4
    (the simplified files), fetched and checked by coordinate_country.Detail."""
    label = "geoBoundaries"
    levels = LEVELS

    def __init__(self, detail):
        self.detail = detail

    def has(self, code, level):
        return self.detail.file(code, level, SIMPLIFIED) is not None

    def names(self, code, level):
        """The units of a level by their names alone (the lines are not read); None when there is no file."""
        path = self.detail.file(code, level, SIMPLIFIED)
        return None if path is None else [Unit(name) for name in shape_names(path)]

    def rings(self, code, level):
        """(units, the rings of each) of a level: its shapes read, their names put right."""
        features, _, _ = cc.read_features(self.detail.file(code, level, SIMPLIFIED))
        features = [(mend(name), rings) for name, rings in features if rings]
        return [Unit(name) for name, _ in features], [rings for _, rings in features]

    def shapes(self, code, level):
        """(units, index) of a level."""
        units, rings = self.rings(code, level)
        return units, cc.index_of_shapes(rings)

    def where(self, code, level):
        return "geoBoundaries " + code + " " + level

    def entry(self, code, level):
        return code + " " + level + SIMPLIFIED, self.detail.lock["files"].get(code + " " + level + SIMPLIFIED, {})

    def below(self, code, level):
        """The next level down, when there is a file of it that is not too large to measure; else (None, why)."""
        if level not in LEVELS[:-1]:
            return None, "no level below"
        lower = LEVELS[LEVELS.index(level) + 1]
        if not self.has(code, lower):
            return None, "no file of " + lower
        size = self.detail.lock["files"].get(code + " " + lower + SIMPLIFIED, {}).get("bytes", 0)
        if size > MAX_CHECK_BYTES:
            return None, "the file of " + lower + " has " + cc.n(size) + " bytes: not measured"
        return lower, ""


def fetch_states(folder, url=NE_STATES_URL, sha256=NE_STATES_SHA256):
    """The pinned file of Natural Earth's states and provinces, fetched once into folder. A file with another
    checksum is never used."""
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, NE_STATES_NAME[:-len(".geojson")] + "-" + cc.SHAPES_COMMIT[:12] + ".geojson")
    if os.path.exists(path) and cc.sha256_of(path) == sha256:
        return path
    part = path + ".part"
    with urllib.request.urlopen(url, timeout=300) as r, open(part, "wb") as f:
        for block in iter(lambda: r.read(1 << 20), b""):
            f.write(block)
    found = cc.sha256_of(part)
    if found != sha256:
        os.remove(part)
        sys.exit("The file of states from " + url + " has sha256 " + found + ", expected " + sha256 + ". Nothing was done.")
    os.replace(part, path)
    return path


class NaturalEarthStates:
    """Natural Earth "admin 1 states and provinces" (1:10m, public domain) as the source of state shapes: one
    file for the world, one level. A unit carries its ISO 3166-2 code, its ne_id and its names in many languages;
    every one of them is a spelling when a unit is matched with AntWeb's states (NE_NAMES, in that order).
    The units of a country: those whose adm0_a3 is the country's code, or, when there are none, whose gu_a3 is
    (La Réunion has adm0_a3 FRA and gu_a3 REU). The lines of a unit are read only when its country is asked for."""
    label = "Natural Earth states and provinces 1:10m"
    levels = [NE_LEVEL]

    def __init__(self, path):
        with open(path, encoding="utf-8") as f:
            self.text = f.read()
        self.by_code = collections.defaultdict(list)             # ISO code of a country -> [(Unit, where the feature begins and ends in the text)]
        by_unit = collections.defaultdict(list)
        decoder = json.JSONDecoder()
        at = self.text.index("[", self.text.index('"features"')) + 1
        while True:
            while self.text[at] in " \n\r\t,":
                at += 1
            if self.text[at] == "]":
                break
            feature, end = decoder.raw_decode(self.text, at)
            props = feature.get("properties") or {}
            spellings = []
            for key in NE_NAMES:
                for text in (props.get(key) or "").split("|"):
                    if text.strip() and text.strip() not in spellings:
                        spellings.append(text.strip())
            if spellings and feature.get("geometry"):
                unit = Unit(spellings[0], tuple(spellings[1:]), props.get("iso_3166_2") or "", str(props.get("ne_id") or ""))
                self.by_code[(props.get("adm0_a3") or "").upper()].append((unit, at, end))
                by_unit[(props.get("gu_a3") or "").upper()].append((unit, at, end))
            at = end
        for code, units in by_unit.items():
            if code and code not in self.by_code:
                self.by_code[code] = units

    def has(self, code, level):
        return bool(self.by_code.get(code))

    def names(self, code, level):
        return [unit for unit, _, _ in self.by_code[code]] if self.by_code.get(code) else None

    def rings(self, code, level):
        decoder = json.JSONDecoder()
        shapes = []
        for unit, at, _ in self.by_code[code]:
            geometry = decoder.raw_decode(self.text, at)[0]["geometry"]
            polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
            shapes.append([[(p[0], p[1]) for p in ring] for polygon in polygons for ring in polygon if len(ring) >= 3])
        return [unit for unit, _, _ in self.by_code[code]], shapes

    def shapes(self, code, level):
        units, rings = self.rings(code, level)
        return units, cc.index_of_shapes(rings)

    def where(self, code, level):
        return "Natural Earth states " + code

    def below(self, code, level):
        return None, "the source has one level"

    def entry(self, code, level):
        return "Natural Earth states and provinces", {"licence": "Public domain (Natural Earth)", "source": "naturalearthdata.com"}


class Refused(Exception):
    """HDX could not be asked or did not give the file; the message tells the administrator what to do by hand."""


class OchaSource:
    """UN OCHA's common operational datasets of administrative boundaries (COD-AB), from the Humanitarian Data
    Exchange (HDX), as the source of state shapes: for a country the dataset cod-ab-<iso3>, its GeoJSON resource
    (a zip with one layer for each level). The key of a unit is OCHA's P-code (MG26 for Vatovavy).

    HDX has no pinned commit. The file of a country is fetched once into the shapes folder; on each run HDX is
    asked when the resource was last changed, and a changed file is fetched again. The report is the record of
    which release was used: the file's sha256, HDX's dates, and "new release" when the checksum is another one
    than the last run's. A file a person put into the shapes folder by hand is used as it is.

    The layout inside the zip is read from the file, not assumed: the layer of a level is the .geojson member
    whose name has adm<N> or admin<N> (not the lines or points) or, failing that, whose features have a P-code
    of level N and none of a finer level; the name and the P-code of a unit are the properties adm<N>_name ...
    and adm<N>_pcode in whatever capitals (ADM1_EN, ADM1_PCODE in OCHA's older files)."""
    label = "OCHA COD-AB"
    levels = ["ADM1", "ADM2"]
    splits = False                                               # the split lines of the mapping file mend older shapes; these are current

    def __init__(self, folder, api=OCHA_API, only=None, agent=OCHA_AGENT):
        self.folder, self.api, self.agent = folder, api, agent
        self.only = None if only is None else {x.strip().upper() for x in only if x.strip()}
        self.lock_path = os.path.join(folder, OCHA_LOCK)
        self.lock = {}
        if os.path.exists(self.lock_path):
            with open(self.lock_path, encoding="utf-8") as f:
                self.lock = json.load(f)
        self.record = {}                                         # ISO code -> what was used in this run, for the report
        self.missing = {}                                        # ISO code -> why there is no file
        self._layers = {}

    def path(self, code):
        return os.path.join(self.folder, OCHA_FILE.format(iso=code.lower()))

    def _open(self, url):
        return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": self.agent, "Accept": "*/*"}), timeout=300)

    def by_hand(self, code, why):
        return ("OCHA's boundaries of " + code + " could not be fetched from HDX (" + why + "). Download the GeoJSON file (" + code.lower()
                + "_admin_boundaries.geojson.zip) of " + OCHA_PAGE.format(iso=code.lower()) + " in a browser and put it on the server as "
                + self.path(code) + " ; the next run uses it. Or run with --ignore-source-lines to do without OCHA's files.")

    def file(self, code):
        """The path of the country's zip, fetched if need be; None when HDX has no such dataset (self.missing says
        so) or the country is not among those asked for. Raises Refused when HDX cannot be reached and no file
        is in the folder."""
        if code in self.record:
            return self.path(code)
        if code in self.missing or not code:
            return None
        if self.only is not None and code.upper() not in self.only:
            self.missing[code] = "not among the countries asked for (--ocha-countries)"
            return None
        path, known = self.path(code), self.lock.get(code, {})
        record = {"file": os.path.basename(path)}
        try:
            with self._open(self.api.format(iso=code.lower())) as r:
                answer = json.loads(r.read().decode("utf-8"))
            result = answer.get("result") or {}
            resource = next((x for x in result.get("resources", ()) if "geojson" in (str(x.get("format")) + " " + str(x.get("name")) + " " + str(x.get("url"))).lower()), None)
            if not answer.get("success") or resource is None:
                self.missing[code] = "HDX has no GeoJSON file in the dataset cod-ab-" + code.lower()
                return None
            url = resource.get("download_url") or resource.get("url")
            record.update({"url": url, "last_modified": str(resource.get("last_modified") or result.get("last_modified") or ""),
                           "hdx": {key: str(result[key]) for key in sorted(result) if isinstance(result[key], (str, int, float)) and result[key] != ""
                                   and any(word in key.lower() for word in ("review", "version", "dataset_date", "last_modified", "license_title", "dataset_source"))}})
            if not os.path.exists(path) or not known or known.get("last_modified") != record["last_modified"]:
                os.makedirs(self.folder, exist_ok=True)
                with self._open(url) as r, open(path + ".part", "wb") as f:
                    for block in iter(lambda: r.read(1 << 20), b""):
                        f.write(block)
                os.replace(path + ".part", path)
                record["fetched"] = datetime.date.today().isoformat()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                self.missing[code] = "HDX has no dataset cod-ab-" + code.lower()
                return None
            if not os.path.exists(path):
                raise Refused(self.by_hand(code, "HTTP " + str(e.code))) from None
            record["note"] = "HDX refused (HTTP " + str(e.code) + "): the file in the folder was used"
        except (urllib.error.URLError, OSError, ValueError) as e:
            if not os.path.exists(path):
                raise Refused(self.by_hand(code, str(getattr(e, "reason", e)))) from None
            record["note"] = "HDX could not be asked (" + str(getattr(e, "reason", e)) + "): the file in the folder was used"
        if not zipfile.is_zipfile(path):
            raise Refused(path + " is not a zip file. " + self.by_hand(code, "what came was not the file"))
        record.update({"sha256": cc.sha256_of(path), "bytes": os.path.getsize(path)})
        record["release"] = ("first seen" if not known.get("sha256") else "the same file as on " + known.get("seen", "the last run") if known["sha256"] == record["sha256"]
                             else "NEW RELEASE: the checksum was " + known["sha256"] + " on " + known.get("seen", "the last run"))
        if "last_modified" not in record:
            record["last_modified"] = known.get("last_modified", "")
        self.lock[code] = {"sha256": record["sha256"], "last_modified": record["last_modified"], "seen": datetime.date.today().isoformat()}
        with open(self.lock_path + ".part", "w", encoding="utf-8") as f:
            json.dump(self.lock, f, indent=1, sort_keys=True)
        os.replace(self.lock_path + ".part", self.lock_path)
        self.record[code] = record
        return path

    def layers(self, code):
        """{level number: the member of the zip that holds the polygons of that level}, read from the file."""
        if code not in self._layers:
            found = {}
            path = self.file(code)
            if path is not None:
                with zipfile.ZipFile(path) as z:
                    members = [m.filename for m in z.infolist() if m.filename.lower().endswith((".geojson", ".json")) and not m.is_dir()]
                    for member in sorted(members, key=len):
                        base = os.path.basename(member).lower()
                        if re.search(r"line|point|centroid|admbndl|admbndp|capital", base):
                            continue
                        with z.open(member) as f:
                            first = next(geojson_features(f, 1 << 16), None)
                        if first is None or (first.get("geometry") or {}).get("type") not in ("Polygon", "MultiPolygon"):
                            continue
                        named = re.search(r"adm(?:in)?_?0?([0-5])(?!\d)", base)
                        coded = [int(m.group(1)) for key in (first.get("properties") or {}) for m in [re.match(r"^adm(?:in)?_?([0-5])_?p_?code$", key.lower())] if m]
                        level = int(named.group(1)) if named else (max(coded) if coded else None)
                        if level is not None:
                            found.setdefault(level, member)
                self.record[code]["layers"] = {str(k): v for k, v in sorted(found.items())}
            self._layers[code] = found
        return self._layers[code]

    def has(self, code, level):
        return int(level[-1]) in self.layers(code)

    @staticmethod
    def unit(properties, number):
        """The Unit of a feature of a level: its name, its other names, its P-code, from whatever the file calls them."""
        names, pcode = [], ""
        keys = {key.lower(): key for key in properties}
        for low in sorted(keys, key=lambda k: (not re.search(r"name\d*$", k), not k.endswith(("_en", "_fr", "_es", "_pt")), k)):
            m = re.match(r"^adm(?:in)?_?" + str(number) + r"_?(p_?code|name\d*|ref\w*|alt\w*|[a-z]{2})$", low)
            value = properties[keys[low]]
            if not m or not isinstance(value, str) or not value.strip():
                continue
            if m.group(1).replace("_", "") == "pcode":
                pcode = value.strip()
            elif value.strip() not in names:
                names.append(value.strip())
        return Unit(names[0], tuple(names[1:]), "", "", pcode) if names else None

    def _read(self, code, level, lines):
        number = int(level[-1])
        units, shapes = [], []
        with zipfile.ZipFile(self.path(code)) as z, z.open(self.layers(code)[number]) as f:
            for feature in geojson_features(f):
                unit = self.unit(feature.get("properties") or {}, number)
                geometry = feature.get("geometry") or {}
                if unit is None or geometry.get("type") not in ("Polygon", "MultiPolygon"):
                    continue
                units.append(unit)
                if lines:
                    polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
                    shapes.append([thin([(p[0], p[1]) for p in ring]) for polygon in polygons for ring in polygon if len(ring) >= 4])
                if "properties" not in self.record[code]:
                    self.record[code]["properties"] = {k: str(v) for k, v in sorted((feature.get("properties") or {}).items())
                                                       if any(w in k.lower() for w in ("version", "valid", "date")) and v not in (None, "")}
        return units, shapes

    def names(self, code, level):
        return self._read(code, level, False)[0] if self.has(code, level) else None

    def rings(self, code, level):
        return self._read(code, level, True)

    def shapes(self, code, level):
        units, rings = self.rings(code, level)
        return units, cc.index_of_shapes(rings)

    def where(self, code, level):
        return "OCHA COD-AB " + code + " " + level

    def below(self, code, level):
        lower = "ADM" + str(int(level[-1]) + 1)
        return (lower, "") if int(lower[-1]) in self.layers(code) else (None, "no layer of " + lower)

    def entry(self, code, level):
        hdx = self.record.get(code, {}).get("hdx", {})
        return "OCHA COD-AB " + code, {"licence": hdx.get("license_title", "see the dataset's page on HDX"), "source": hdx.get("dataset_source", "")}

    def report(self):
        """The lines of the report: which release of each country's file was used."""
        out = []
        for code in sorted(self.record):
            r = self.record[code]
            out.append("  " + code + ": " + r["file"] + ", " + cc.n(r["bytes"]) + " bytes, sha256 " + r["sha256"] + "; " + r["release"]
                       + ("; last modified on HDX " + r["last_modified"] if r.get("last_modified") else "; put into the folder by hand or kept from an earlier run")
                       + ("; " + r["note"] if r.get("note") else ""))
            more = dict(r.get("hdx", {}), **r.get("properties", {}))
            if more:
                out.append("    " + "; ".join(key + " " + value for key, value in sorted(more.items())))
            if r.get("layers"):
                out.append("    layers: " + "; ".join("level " + k + " " + v for k, v in r["layers"].items()))
        return out


class Chosen:
    """The level of the boundary files of one country: its shapes, the AntWeb state of each, the index. usable:
    a point in it may get a proposal. where: what the basis of a proposal calls the shapes; units: each shape's
    other names, ISO 3166-2 code and id, where the source has them."""

    def __init__(self, country, code, level, names, states, hows, index, usable=True, where="", units=None, label="geoBoundaries"):
        self.country, self.code, self.level, self.names, self.states, self.hows = country, code, level, names, states, hows
        self.index, self.usable, self.label = index, usable, label
        self.where = where or "geoBoundaries " + code + " " + level
        self.units = units or [Unit(name) for name in names]


At = collections.namedtuple("At", "state shape basis reason detail other other_km flag how inside unit source km",
                            defaults=("", "", "", "", "", "", "", False, None, "", ""))       # km: how far outside its shape the point lies ("": inside)
Tested = collections.namedtuple("Tested", "stats states hows lines decide today")
SHARES = ["in the unit of their state", "in the unit of another state", "in a unit with no AntWeb state", "in no unit"]


def share(part, whole):
    return part / whole if whole else 0.0


def percent(part, whole):
    return format(100.0 * part / whole, ".0f") if whole else ""


def pick_level(tried):
    """Which level of a country is used when there are no specimens to test with: (its entry in tried, usable,
    why). tried: for each level looked at, coarse to fine, a dict with shapes (0: no file), shape_hits (shapes
    that are an AntWeb state), states (AntWeb's valid states of the country) and state_hits (those of them a
    shape names).

    The first level where at least half of AntWeb's states are matched and at least half of the shapes are
    AntWeb states is taken as it stands. Else the level with the largest share of shapes that are AntWeb states
    (the coarser one of two alike). It is used when at least half of its shapes are AntWeb states (AntWeb's
    list has more names than the states); or, for the coarsest level only, when at least half of AntWeb's
    states are matched (AntWeb has few of the country's states). A finer level is not used on the states
    matched alone: its units often have the names of the states they lie in. That is how version 1, which
    counted the states matched, took the communes of Chile for its regions, the districts of the Czech
    Republic for its regions, the provinces of Spain for its communities.

    When no level is used, the entry given is the level whose number of shapes is nearest to the number of
    AntWeb's states (the coarser one of two alike): the level a mapping would most likely be made for. Its
    shapes say where a record lies, and mapping_suggestions.csv is written for it."""
    files = [t for t in tried if t["shapes"]]
    if not files:
        return None, False, "no boundary file"
    for t in files:
        if share(t["state_hits"], t["states"]) >= MIN_SHARE and share(t["shape_hits"], t["shapes"]) >= MIN_SHARE:
            return t, True, "at least half of AntWeb's states are matched and at least half of the shapes are AntWeb states"
    best = max(files, key=lambda t: share(t["shape_hits"], t["shapes"]))            # the first of equals: the coarser
    start = "the level with the largest share of shapes that are AntWeb states"
    if share(best["shape_hits"], best["shapes"]) >= MIN_SHARE:
        return best, True, start + "; at least half of its shapes are AntWeb states"
    if best is files[0] and share(best["state_hits"], best["states"]) >= MIN_SHARE:
        return best, True, start + ", and the coarsest; at least half of AntWeb's states are matched"
    nearest = min(files, key=lambda t: abs(math.log(t["shapes"] / max(t["states"], 1))))
    return nearest, False, ("below the minimum: at the best level, " + best["level"] + ", " + cc.n(best["shape_hits"]) + " of " + cc.n(best["shapes"])
                            + " shapes are AntWeb states and " + cc.n(best["state_hits"]) + " of " + cc.n(best["states"]) + " AntWeb states are matched")


def pick_tested(tried):
    """Which level is used when specimens confirmed a state at some level: the one with the most AntWeb states
    confirmed by specimens or matched by a name the specimens do not contradict (the coarser one of two alike)."""
    files = [t for t in tried if t["shapes"]]
    best = max(files, key=lambda t: t.get("sound_hits", t["state_hits"]))      # a name that stands against the specimens does not count here
    return best, True, "the level with the most AntWeb states confirmed by specimens or matched by name"


def level_text(t):
    if not t["shapes"]:
        return t["level"] + " no file"
    n = cc.n
    text = (t["level"] + " " + n(t["state_hits"]) + "/" + n(t["states"]) + " AntWeb states, " + n(t["shape_hits"]) + "/" + n(t["shapes"]) + " shapes"
            + (", " + n(t["islands"]) + (" of them an island country" if t["islands"] == 1 else " of them island countries") + " of AntWeb" if t["islands"] else ""))
    if t.get("located"):
        text += (" [specimens: " + n(t["confirmed"]) + " states confirmed, " + n(t["contradicted"]) + " name matches not used, " + n(t.get("kept", 0))
                 + " names standing against specimens, of " + n(t["testable"]) + " states with " + str(MIN_POINTS) + " or more located]")
    return text


class StateShapes:
    """For each country: the level of geoBoundaries that holds AntWeb's states, the state of each of its shapes
    (tested with AntWeb's own specimens where there are any), and the state of a point."""

    def __init__(self, detail, states, countries, shape_map, evidence=None, rules=None, source=None, others=None, check=()):
        """source: where the shapes come from (geoBoundaries when none is given). others: {name of a source: the
        source} for the countries whose "source" line of the mapping file names another one; without it the
        source lines are not followed. check: the countries whose source is measured for consistency (True: all)."""
        self.detail, self.states, self.countries, self.evidence = detail, states, countries, evidence
        self.source = source or GeoSource(detail)
        self.rules = rules or Rules()
        self.check = check
        self.by_country = {}                                     # valid country -> its own source
        for country, name in self.rules.sources.items():
            c = countries.resolve(country)
            if c is not None and others and others.get(name) is not None:
                self.by_country[c.name] = others[name]
        self.map = collections.defaultdict(list)                 # (country, level, plain shape) -> [(AntWeb state, a state to add)], in order
        self.map_rows = shape_map
        for row in shape_map:
            country, level, shape, state = row[:4]
            c = countries.resolve(country)
            self.map[(c.name if c is not None else country, level.upper(), plain(shape))].append((state, tuple(row[:4]) in self.rules.new))
        self.convert, self.within = {}, {}                       # valid country -> the coarser states converted; plain state -> coarser state
        for country, units in self.rules.convert.items():
            c = countries.resolve(country)
            if c is not None:
                self.convert[c.name] = {u for u in (states.valid_of(c.name, unit) for unit in units) if u}
                self.within[c.name] = {state: states.valid_of(c.name, unit) for state, unit in self.rules.within.get(country, {}).items()}
        self.names = collections.defaultdict(list)               # (country, level, plain shape) -> AntWeb states of the "name" lines, in order
        for country, level, shape, state in self.rules.names:
            c = countries.resolve(country)
            self.names[(c.name if c is not None else country, level, plain(shape))].append(state)
        self.splits = collections.defaultdict(dict)              # country -> plain shape -> its level, units -> state, the state of the rest, lines
        for country, shape, level, unit, state, note in self.rules.splits:
            c = countries.resolve(country)
            entry = self.splits[c.name if c is not None else country].setdefault(plain(shape), {"shape": shape, "level": level, "units": {}, "rest": None, "lines": []})
            if entry["level"] != level:
                raise ValueError("mapping file: the shape " + shape + " of " + country + " is split by two levels, " + entry["level"] + " and " + level)
            if unit == "*":
                entry["rest"] = state
            else:
                entry["units"][plain(unit)] = state
            entry["lines"].append((country, shape, level, unit, state, note))
        self.splits_used = []                                    # the split lines of the shapes that were looked at
        self._split_index = {}
        self.rows_used = set()                                   # (country, level, shape, state) of the rows that named a shape
        self.level_of = {}                                       # country -> the level chosen, once found
        self.tables = {}                                         # country -> what the report prints
        self.problems = {}                                       # country -> why it has no state shapes
        self.files = {}                                          # lock key -> licence, for the report
        self._chosen = collections.OrderedDict()                 # country -> Chosen, the two used last

    def source_of(self, country):
        return self.by_country.get(country, self.source)

    def consistency(self, country, code, level, units, index):
        """How well the source's state level and its next level down fit: a dict for the report, or None when the
        country is not to be measured. On a grid of about GRID_POINTS points over the country, each unit of the
        next level is counted by the state shape its points lie in; a unit should lie at least ONE_STATE inside
        one state shape. Points in no state shape (the coast) do not count. geoBoundaries' regions of Madagascar
        are from OpenStreetMap and its districts from the disaster management office: nine tenths of the district
        Vohipeno lie in the wrong region shape, and this is the check that shows it."""
        if not (self.check is True or country in self.check):
            return None
        source = self.source_of(country)
        lower, why = source.below(code, level)
        if lower is None:
            return {"level": level, "lower": "", "why": why}
        low_units, low_index = source.shapes(code, lower)
        west, south = index.west, index.south
        width, height = index.ncols * index.cell, index.nrows * index.cell
        step = max(0.002, math.sqrt(width * height / GRID_POINTS))
        counts = collections.defaultdict(collections.Counter)
        y = south + step / 2
        while y < south + height:
            x = west + step / 2
            while x < west + width:
                unit = low_index.inside(y, x)
                if len(unit) == 1:
                    state = index.inside(y, x)
                    if len(state) == 1:
                        counts[unit[0]][state[0]] += 1
                x += step
            y += step
        measured = {i: c for i, c in counts.items() if sum(c.values()) >= MIN_POINTS}
        worst = []
        for i, c in measured.items():
            total, top = sum(c.values()), c.most_common(2)
            if top[0][1] < ONE_STATE * total:
                worst.append((top[0][1] / total, low_units[i].name + ": " + percent(top[0][1], total) + "% in " + units[top[0][0]].name
                              + (", " + percent(top[1][1], total) + "% in " + units[top[1][0]].name if len(top) > 1 else "")))
        return {"level": level, "lower": lower, "units": len(low_units), "measured": len(measured), "under": len(worst), "worst": [text for _, text in sorted(worst)[:8]],
                "step_km": step * cc.KM_PER_DEGREE}

    def wanted(self, country):
        """AntWeb's valid states of the country that the shapes are compared with: all but the coarser units that
        are being converted (the provinces of Madagascar)."""
        return [x for x in self.states.of(country) if x not in self.convert.get(country, ())]

    def island(self, country, name):
        """The AntWeb name of the valid island country a shape name means, when the island's parent country is
        country (Hawaii for the United States); None otherwise."""
        place = self.countries.resolve(name)
        if place is None or not place.is_island or not place.parent:
            return None
        parent = self.countries.resolve(place.parent)
        return place.name if parent is not None and parent.name == country else None

    @staticmethod
    def states_matched(wanted, states, hows):
        """The AntWeb states the shapes of a level name. An island shape is counted apart; it counts here only
        when AntWeb has the island as a state of the country as well (Hawaii of the United States). A state to
        add is none of AntWeb's yet; a shape that is split counts when AntWeb has a state of its name."""
        return {s for s, h in zip(states, hows) if s and (h not in (ISLAND, NEW, SPLIT) or s in wanted)}

    def match(self, country, level, names, units=None):
        """What to enter as the state for each shape name (None for none) and how it was found, before any test
        with specimens: by the mapping file, as an island country of AntWeb, by the name of a state, by another
        name the source gives the unit (units: Natural Earth's names in other languages and its alternative names)."""
        states, hows = [], []
        skip = self.convert.get(country, ())
        for number, name in enumerate(names):
            state, how = None, ""
            for wanted, new in self.map.get((country, level, plain(name)), ()):
                found, _ = self.states.find(country, wanted)
                if found is not None:
                    state, how = found, "mapping file"
                    break
                if new:
                    state, how = wanted, NEW
                    break
            if state is None and getattr(self.source_of(country), "splits", True) and plain(name) in self.splits.get(country, {}):
                state, how = self.states.find(country, name)[0] or name, SPLIT
            if state is None:
                for wanted in self.names.get((country, level, plain(name)), ()):
                    found, _ = self.states.find(country, wanted)
                    if found is not None and found not in skip:
                        state, how = found, NAMED
                        break
            if state is None:
                island = self.island(country, name)
                if island is not None:
                    state, how = island, ISLAND
            if state is None:
                state, how = self.states.find(country, name)
                if state in skip:                                # a shape with the name of a unit that is being given up
                    state, how = None, ""
            if state is None and units is not None:
                for spelling in units[number].spellings:
                    found, _ = self.states.find(country, spelling)
                    if found is not None and found not in skip:
                        state, how = found, "name " + spelling + " of the unit"
                        break
            states.append(state)
            hows.append(how)
        return states, hows

    def test(self, country, level, names, index, units=None, code=""):
        """The state of each shape of a level, tested with AntWeb's own specimens: a Tested.

        Every specimen of the country with a state and coordinates that lies inside exactly one shape of the
        level is counted for (shape, state). Lying inside a shape of the country's own file is what makes the
        point agree with its stored country here. Then, for each shape, in this order:
          - a row of the mapping file wins (a state, a state to add, a split); an island country of AntWeb
            stays what it is;
          - a shape whose name is exactly the name of one valid AntWeb state of the country (capitals, accents
            and punctuation aside; an old name counts), and the only shape of the level with that name, is
            that state. The specimen test never throws this out. Specimens that say otherwise (the shape is
            "confirmed" for another state, or the state's own specimens lie mostly elsewhere) are listed in
            to_decide.csv: their stored state differs from where their coordinates fall. Staging 2026-11-10:
            six specimens filed under Haute Matsiatra lay in the shape Ihorombe; version 4 took the name away
            and 6,986 specimens lost their proposal;
          - a looser name (without a word such as Province; another name the source gives the unit; a "name"
            line of the mapping file) stands as well, unless the state has at least MIN_AGAINST located
            specimens and fewer than CONTRADICTED_BELOW of them lie in the shape: then it is not used;
          - several shapes of the level with the name of one state: the name does not say which. A shape that
            holds fewer than MIN_POINTS of the state's specimens, and fewer than half of them, while the state
            has MIN_POINTS located, is not used;
          - where no name is in use, "confirmed by specimens" decides: at least MIN_POINTS specimens of a state
            lie in the shape, they are at least MIN_OF_STATE of the state's located specimens, and at least
            MIN_OF_SHAPE of the shape's specimens are filed under the state.
        The lines for mapping_tested.csv carry the three numbers. decide: shapes that hold several AntWeb
        states, AntWeb states that lie in several shapes, and names that stand against the specimens."""
        n = cc.n
        skip = self.convert.get(country, ())
        wanted = set(self.wanted(country))
        named, named_how = self.match(country, level, names, units)
        label = self.source_of(country).label
        shapes_named = collections.Counter(x for x, h in zip(named, named_how) if x and h not in BY_FILE + (ISLAND,))
        ids = lambda i: ([units[i].code, units[i].ident, units[i].pcode or units[i].code, label if units[i].pcode or units[i].code else ""]
                         if units is not None else ["", "", "", ""])
        tab = collections.defaultdict(collections.Counter)       # shape number -> state -> specimens
        where = collections.defaultdict(collections.Counter)     # state -> shape number -> specimens
        unlocated = 0
        halves = collections.Counter()                           # in a shape that is split: (the state of the unit the specimen lies in or None, its stored state)
        for state, lat, lon, count in (self.evidence.points.get(country, ()) if self.evidence is not None else ()):
            if state in skip:
                continue
            inside = index.inside(lat, lon)
            if len(inside) != 1:
                unlocated += count
                continue
            tab[inside[0]][state] += count
            where[state][inside[0]] += count
            if named_how[inside[0]] == SPLIT and code:
                halves[(self.split_at(Chosen(country, code, level, names, named, named_how, index), inside[0], lat, lon, 0.0)[0], state)] += count
        of_state = {state: sum(shapes.values()) for state, shapes in where.items()}
        confirmed = {}
        for i, counts in tab.items():
            state, most = max(counts.items(), key=lambda kv: (kv[1], kv[0]))
            if most >= MIN_POINTS and most >= MIN_OF_STATE * of_state[state] and most >= MIN_OF_SHAPE * sum(counts.values()):
                confirmed[i] = state
        places = lambda state: "; ".join(names[i] + " " + n(c) for i, c in sorted(where[state].items(), key=lambda kv: (-kv[1], names[kv[0]]))[:4])
        states, hows, lines, today, contradicted, doubted, conflicts, sides = [], [], [], {}, 0, set(), [], []
        for i, name in enumerate(names):
            counts = tab.get(i, {})
            total = sum(counts.values())
            filed = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
            filed = "; ".join(x + " " + n(c) for x, c in filed[:6]) + (" ... and " + n(len(filed) - 6) + " more" if len(filed) > 6 else "")
            line = lambda state, basis: [country, level, name, state, basis, counts.get(state, 0), percent(counts.get(state, 0), of_state.get(state, 0)),
                                         percent(counts.get(state, 0), total), filed] + ids(i)
            elsewhere = lambda state, floor: of_state.get(state, 0) >= floor and counts.get(state, 0) < CONTRADICTED_BELOW * of_state[state]
            state, how = named[i], named_how[i]
            final = (None, "")
            if how in BY_FILE:
                final = (state, how)
                if how == SPLIT:
                    entry = self.splits[country][plain(name)]
                    targets = sorted(set(entry["units"].values()) | ({entry["rest"]} if entry["rest"] else set()))
                    lines.append(line(state, "mapping file: split by its " + entry["level"] + " units into " + "; ".join(targets)))
                else:
                    lines.append(line(state, "mapping file" + (": " + TO_ADD if how == NEW else "")))
            elif how == ISLAND:
                final = (state, how)
                lines.append(line(state, "island country of AntWeb"))
            elif state:
                have = of_state.get(state, 0)
                if shapes_named[state] > 1:                      # several shapes with the name of this state: the specimens say which
                    thrown = have >= MIN_POINTS and counts.get(state, 0) < MIN_POINTS and elsewhere(state, MIN_POINTS)
                else:                                            # the only one: an exact name always stands, a looser one against little evidence
                    thrown = how != "name" and elsewhere(state, MIN_AGAINST)
                if state == confirmed.get(i):
                    final = (state, how)
                    lines.append(line(state, "confirmed by specimens, and by the name"))
                elif thrown:
                    contradicted += 1
                    lines.append(line(state, "name match NOT USED: " + ("another shape has the name too; " if shapes_named[state] > 1 else "contradicted by specimens; ")
                                      + "those of the state lie in " + places(state)))
                    if i in confirmed:
                        final = (confirmed[i], SPECIMENS)
                        lines.append(line(confirmed[i], "confirmed by specimens"))
                else:
                    final = (state, how)
                    against = ("the specimens in the shape are filed under " + filed if i in confirmed else
                               "the specimens of " + state + " lie in " + places(state) if elsewhere(state, MIN_POINTS) else "")
                    if against:
                        doubted.add(state)
                        sides.append(confirmed[i] if i in confirmed else None)       # the state whose specimens lie in this shape, or None: this state's lie elsewhere
                        conflicts.append([country, level, DIFFERS, name, "the shape is " + state + " by its name; " + against, total if i in confirmed else have, state])
                        lines.append(line(state, "by name; the name stands, " + against + " (to_decide.csv)"))
                    else:
                        lines.append(line(state, "by name only, " + n(have) + " specimens" if have < MIN_POINTS else "by name; the specimens agree in part"))
            elif i in confirmed:
                final = (confirmed[i], SPECIMENS)
                lines.append(line(confirmed[i], "confirmed by specimens"))
            states.append(final[0])
            hows.append(final[1])
            if final[0] is None and filed:
                today[name] = filed
        # the same specimens are listed once: where they lie (a shape with the specimens of another state), not again for the shape of their state
        elsewhere_listed = {x for x in sides if x}
        conflicts[:] = [c[:6] for c, side in zip(conflicts, sides) if side is not None or c[6] not in elsewhere_listed]
        standing = len(conflicts)
        settled = {x for x in states if x}
        settled_by_test = {confirmed[i] for i in confirmed if states[i] == confirmed[i]}
        decide, inside_mixed = conflicts, 0
        for i, counts in sorted(tab.items(), key=lambda kv: names[kv[0]]):
            several = sorted(((c, x) for x, c in counts.items() if c >= MIN_POINTS), reverse=True)
            if len(several) > 1 and i not in confirmed and hows[i] not in BY_FILE + (ISLAND,):
                decide.append([country, level, "shape holds several AntWeb states", names[i], "; ".join(x + " " + n(c) for c, x in several), sum(counts.values())])
        for state in sorted(where):
            if of_state[state] < MIN_POINTS or state in settled_by_test or state in {x for x, h in zip(states, hows) if h in BY_FILE}:
                continue
            parts = sorted(((c, names[i]) for i, c in where[state].items() if c >= MIN_POINTS), reverse=True)
            if len(parts) > 1:
                decide.append([country, level, "AntWeb state lies in several shapes", state, "; ".join(x + " " + n(c) for c, x in parts), of_state[state]])
            if state not in settled and max(where[state].values()) >= MIN_OF_STATE * of_state[state]:
                inside_mixed += 1                                # the state lies in one shape that is not its own: AntWeb is finer than this level
        stats = {"level": level, "shapes": len(names), "shape_hits": sum(1 for x in states if x), "states": len(wanted),
                 "state_hits": len(self.states_matched(wanted, states, hows)), "islands": sum(1 for h in hows if h == ISLAND),
                 "located": sum(of_state.values()), "unlocated": unlocated, "testable": sum(1 for c in of_state.values() if c >= MIN_POINTS),
                 "confirmed": len(settled_by_test), "contradicted": contradicted, "inside_mixed": inside_mixed,
                 "sound_hits": len(self.states_matched(wanted, states, hows) - doubted), "kept": standing}
        # how the specimens lie in the units as they are now named: what the source is worth for AntWeb's states
        shares = collections.Counter({SHARES[3]: unlocated})
        for i, counts in tab.items():
            if hows[i] == SPLIT and code:                        # a shape that is split: by the state of the unit each specimen lies in
                continue
            for state, count in counts.items():
                shares[SHARES[0] if states[i] == state else SHARES[1] if states[i] else SHARES[2]] += count
        for (half, state), count in halves.items():
            shares[SHARES[0] if half == state else SHARES[1] if half else SHARES[2]] += count
        stats["shares"] = shares
        return Tested(stats, states, hows, lines, decide, today)

    def look(self, country, code):
        """How each level fits, coarse to fine: (tried, kept). tried is the list pick_level and pick_tested read;
        kept is the (names, index, Tested) of the best level tested with specimens so far, or None.

        With specimens to test with (at least MIN_POINTS of the country, and at least one state with that many
        located at the coarsest level): each level is read and tested. The next finer level is looked at only
        while more AntWeb states lie inside a shape that is not their own (AntWeb is finer than the level) than
        are settled at the level. Without: only the names of a level are read, as before, until one fits by
        both halves."""
        source = self.source_of(country)
        wanted = set(self.wanted(country))
        have = sum(p[3] for p in self.evidence.points.get(country, ()) if p[0] not in self.convert.get(country, ())) if self.evidence is not None else 0
        testing = have >= MIN_POINTS
        tried, kept = [], None
        for level in source.levels:
            if not source.has(code, level):
                tried.append({"level": level, "shapes": 0})
                continue
            if testing:
                units, index = source.shapes(code, level)
                tested = self.test(country, level, [u.name for u in units], index, units, code)
                t = tested.stats
                tried.append(t)
                if kept is None or t["sound_hits"] > kept[2].stats["sound_hits"]:
                    kept = (units, index, tested)
                if t["testable"] == 0 and len([x for x in tried if x["shapes"]]) == 1:
                    testing = False                              # nothing to test with here: go on by the names alone
                elif t["inside_mixed"] <= t["sound_hits"]:
                    break
                else:
                    continue
            else:
                units = source.names(code, level)
                names = [u.name for u in units]
                states, hows = self.match(country, level, names, units)
                tried.append({"level": level, "shapes": len(names), "shape_hits": sum(1 for x in states if x), "states": len(wanted),
                              "state_hits": len(self.states_matched(wanted, states, hows)), "islands": sum(1 for h in hows if h == ISLAND)})
            t = tried[-1]
            if share(t["state_hits"], t["states"]) >= MIN_SHARE and share(t["shape_hits"], t["shapes"]) >= MIN_SHARE:
                break
        return tried, kept

    def choose(self, country, code):
        """The Chosen level of a country, or None (self.problems says why). A level that is not usable is given
        too: its shapes say where a point is, for what a person has to decide."""
        if country in self._chosen:
            self._chosen.move_to_end(country)
            return self._chosen[country]
        if country in self.problems:
            return None
        if not code:
            self.problems[country] = "AntWeb has no ISO code for the country and no Natural Earth shape gives one"
            return None
        source = self.source_of(country)
        try:
            if country in self.tables:                           # chosen before, dropped to make room: read again
                t = self.tables[country]
                units, index = source.shapes(code, t["level"])
            else:                                                # the first time: which level, the state of each shape, what the report says
                tried, kept = self.look(country, code)
                if any(x.get("confirmed") for x in tried):
                    picked, usable, why = pick_tested(tried)
                else:
                    picked, usable, why = pick_level(tried)
                if picked is None:
                    self.problems[country] = ("geoBoundaries has no file of the divisions of " if source.label == "geoBoundaries" else
                                              source.label + " has no units of ") + code + ("" if not getattr(source, "missing", {}).get(code) else ": " + source.missing[code])
                    return None
                level = picked["level"]
                if kept is not None and kept[2].stats["level"] == level:
                    units, index, tested = kept
                else:
                    units, index = source.shapes(code, level)
                    tested = self.test(country, level, [u.name for u in units], index, units, code)
                    usable = usable and any(tested.states)       # the specimens may have contradicted every name
                kept = None
                names = [u.name for u in units]
                wanted = self.wanted(country)
                key, entry = source.entry(code, level)
                self.files[key] = entry
                states, hows = tested.states, tested.hows
                for name, state, how in zip(names, states, hows):
                    if how in BY_FILE:
                        self.rows_used.add((country, level, name, state))
                got = self.states_matched(wanted, states, hows)
                shape_of = {}
                for name, state in zip(names, states):
                    if state:
                        shape_of.setdefault(state, name)
                t = self.tables[country] = {
                    "code": code, "level": level, "tried": tried, "usable": usable, "why": why, "states": states, "hows": hows,
                    "where": source.where(code, level), "levels": list(source.levels), "label": source.label,
                    "consistency": self.consistency(country, code, level, units, index),
                    "lines": tested.lines, "decide": tested.decide, "today": tested.today, "stats": tested.stats,
                    "matched": sorted(got), "not_matched": [x for x in wanted if x not in got], "shape_of": shape_of,
                    "shapes_not_states": sorted({n for n, x in zip(names, states) if not x}),
                    "islands": sorted(n + (" (" + x + ")" if x != n else "") for n, x, h in zip(names, states, hows) if h == ISLAND),
                    "to_add": sorted({x for x, h in zip(states, hows) if h == NEW} | set(self.split_targets(country, names, hows, True))),
                    "new_states": self.new_states(country, code, level, source, units, states, hows),
                    "named": sorted(n + " -> " + x for n, x, h in zip(names, states, hows) if h == NAMED),
                    "other_way": sorted(n + " -> " + x + " (" + h + ")" for n, x, h in zip(names, states, hows) if x and h not in ("name", ISLAND)),
                    "licence": entry.get("licence", ""), "source": entry.get("source", "")}
                self.level_of[country] = level
                for name, how in zip(names, hows):
                    if how == SPLIT:
                        self.splits_used += [x for x in self.splits[country][plain(name)]["lines"] if x not in self.splits_used]
        except (urllib.error.URLError, OSError) as e:
            self.problems[country] = "a boundary file of " + code + " could not be fetched (" + str(getattr(e, "reason", e)) + ")"
            return None
        chosen = Chosen(country, code, t["level"], [u.name for u in units], t["states"], t["hows"], index, t["usable"], t["where"], units, source.label)
        self._chosen[country] = chosen
        while len(self._chosen) > 2:
            self._chosen.popitem(last=False)
        return chosen

    def new_states(self, country, code, level, source, units, states, hows):
        """For each state that the mapping file names and AntWeb does not have yet, what the Geolocale Manager
        asks for when it is added, measured on the source's own shape: [(state, Outline or None, where the
        shape is from, the unit's code)]. A state that is one or more shapes of the level: those shapes. A
        state that a split line makes of finer units: those units of geoBoundaries. The file is read once more,
        and only for a country that has such a state."""
        whole = collections.OrderedDict()                        # state -> the numbers of its shapes
        parts = collections.defaultdict(lambda: collections.defaultdict(set))   # finer level -> state -> the plain names of its units
        rest = []
        for number, (state, how) in enumerate(zip(states, hows)):
            if how == NEW:
                whole.setdefault(state, []).append(number)
            elif how == SPLIT:
                entry = self.splits[country][plain(units[number].name)]
                for unit, target in entry["units"].items():
                    if self.states.find(country, target)[0] is None:
                        parts[entry["level"]][target].add(unit)
                if entry["rest"] and self.states.find(country, entry["rest"])[0] is None and entry["rest"] not in rest:
                    rest.append(entry["rest"])
        out = []
        if whole:
            _, rings = source.rings(code, level)
            for state, numbers in whole.items():
                out.append((state, outline([ring for n in numbers for ring in rings[n]]),
                            source.where(code, level) + ", shape " + "; ".join(units[n].name for n in numbers),
                            "; ".join(x for x in (units[n].pcode or units[n].code for n in numbers) if x)))
        geo = GeoSource(self.detail)
        for finer in sorted(parts):
            found, rings = geo.rings(code, finer) if geo.has(code, finer) else ([], [])
            for state in sorted(parts[finer]):
                numbers = [n for n, u in enumerate(found) if plain(u.name) in parts[finer][state]]
                out.append((state, outline([ring for n in numbers for ring in rings[n]]) if numbers else None,
                            "geoBoundaries " + code + " " + finer + ", units " + "; ".join(found[n].name for n in numbers), ""))
        out += [(state, None, "every unit of a split shape that the mapping file does not name: not measured", "") for state in rest]
        return out

    def shapes_of(self, chosen, name):
        """The numbers of the shapes of a Chosen level that are a state or a coarser unit (a province) by its
        AntWeb name: the shapes of that state, or of the states the mapping file puts within that unit."""
        within = self.within.get(chosen.country, {})
        fits = lambda state: bool(state) and (state == name or within.get(plain(state)) == name)
        found = []
        for number, (state, how) in enumerate(zip(chosen.states, chosen.hows)):
            if how == SPLIT:
                entry = self.splits[chosen.country][plain(chosen.names[number])]
                targets = list(entry["units"].values()) + ([entry["rest"]] if entry["rest"] else [])
                if any(fits(self.states.find(chosen.country, t)[0] or t) for t in targets):
                    found.append(number)
            elif fits(state):
                found.append(number)
        return tuple(found)

    def split_targets(self, country, names, hows, new_only=False):
        """The states the split shapes among names are told apart into (new_only: those AntWeb does not have yet)."""
        found = []
        for name, how in zip(names, hows):
            if how == SPLIT:
                entry = self.splits[country][plain(name)]
                for state in list(entry["units"].values()) + ([entry["rest"]] if entry["rest"] else []):
                    valid = self.states.find(country, state)[0]
                    if (valid or state) not in found and not (new_only and valid):
                        found.append(valid or state)
        return found

    def split_at(self, chosen, first, lat, lon, allowed):
        """A point in a shape that the mapping file splits: (state, a state to add, unit name, where the units are
        from, the nearest unit of another state and the km to it), or (None, ...) with the unit it lies in when
        the mapping file gives that unit no state. The units are those of a finer level of geoBoundaries (the
        districts of Madagascar); a unit not named in the file has the state of the "*" line, if there is one."""
        entry = self.splits[chosen.country][plain(chosen.names[first])]
        key = (chosen.code, entry["level"])
        if key not in self._split_index:
            geo = GeoSource(self.detail)
            self._split_index[key] = geo.shapes(chosen.code, entry["level"]) if geo.has(chosen.code, entry["level"]) else None
        where = "geoBoundaries " + chosen.code + " " + entry["level"]
        if self._split_index[key] is None:
            return None, False, "", where + ": no file", "", ""
        units, index = self._split_index[key]
        state_of = lambda i: entry["units"].get(plain(units[i].name), entry["rest"])
        inside = index.inside(lat, lon)
        near = index.near(lat, lon, max(allowed, OTHER_KM[-1]))
        if inside:
            unit = min(inside, key=lambda i: units[i].name)
        else:                                                    # the two files draw the coast differently: the unit next to the point
            close = sorted((km, units[i].name, i) for i, km in near.items() if km <= allowed)
            if not close:
                return None, False, "", where, "", ""
            unit = close[0][2]
        state = state_of(unit)
        if state is None:
            return None, False, units[unit].name, where, "", ""
        others = sorted((km, state_of(i)) for i, km in near.items() if i != unit and state_of(i) and state_of(i) != state)
        valid = self.states.find(chosen.country, state)[0]
        return valid or state, valid is None, units[unit].name, where, others[0][1] if others else "", others[0][0] if others else ""

    def state_at(self, chosen, lat, lon, allowed, tolerance=None):
        """What the shapes say of a point: an At. With a state and a basis when the point is inside one state
        shape, or outside every shape and within the allowed distance of one; else the reason why there is no
        proposal, with the names a person needs. A point inside one state always gets it: another state within
        the allowed distance is named (other, other_km) and flagged, for a person to look at first. "Two
        candidates" is left for a point outside every shape and within the allowed distance of more than one,
        and for the rare point that two shapes of the file both hold."""
        label = lambda n: chosen.states[n] or chosen.names[n] + " (not a state in AntWeb)"
        inside = chosen.index.inside(lat, lon)
        near = chosen.index.near(lat, lon, allowed)
        if inside:
            labels = sorted({label(n) for n in inside})
            if len(labels) > 1:
                return At(None, "", "", TWO, "inside both " + " and ".join(labels) + ": the shapes of the file overlap")
            first = min(inside, key=lambda n: chosen.names[n])
        else:
            best = {}
            for n, km in near.items():
                if km < best.get(label(n), (float("inf"), 0))[0]:
                    best[label(n)] = (km, n)
            if not best:
                return At(None, "", "", NO_SHAPE_HERE, "")
            if len(best) > 1:
                return At(None, "", "", TWO, " or ".join(sorted(best, key=lambda x: (best[x][0], x))))
            first = next(iter(best.values()))[1]
        name = chosen.names[first]
        if chosen.states[first] is None:
            return At(None, name, "", NOT_A_STATE, name)
        # the nearest other state: within the allowed distance, or looked for a little farther
        own, other, other_km, flag = label(first), "", "", ""
        for reach in (allowed,) + tuple(km for km in OTHER_KM if km > allowed):
            found = near if reach == allowed else chosen.index.near(lat, lon, reach)
            others = sorted((km, label(n)) for n, km in found.items() if label(n) != own and n not in inside)
            if others:
                other, other_km = others[0][1], cc.rounded(others[0][0])
                if reach == allowed:
                    limit = tolerance if tolerance is not None and allowed <= tolerance else None
                    flag = ("within the allowed " + format(limit, "g") + " km of " if limit is not None
                            else "within the stated error (" + cc.rounded(allowed) + " km) of ") + other
                break
        where = chosen.where
        if chosen.hows[first] == SPLIT:                          # the state is that of the finer unit the point lies in
            state, new, unit, units_from, half, half_km = self.split_at(chosen, first, lat, lon, allowed)
            if state is None:
                return At(None, name, "", NO_SPLIT_UNIT, name + (": in " + unit + " (" + units_from + ")" if unit else ": in no unit of " + units_from))
            if half and (other_km == "" or half_km < float(other_km)):
                other, other_km = half, cc.rounded(half_km)
                limit = tolerance if tolerance is not None and allowed <= tolerance else None
                flag = "" if half_km > allowed else (("within the allowed " + format(limit, "g") + " km of " if limit is not None
                                                     else "within the stated error (" + cc.rounded(allowed) + " km) of ") + half)
            basis = (("coordinates inside " if inside else "in the water, " + cc.rounded(near[first]) + " km from ") + name + ", " + where + "; in its unit " + unit
                     + " (" + units_from + "), which the mapping file gives to " + state + (", a " + TO_ADD if new else ""))
            return At(state, name, basis, "", "", other, other_km, flag, NEW if new else SPLIT, bool(inside), chosen.units[first], chosen.label,
                      "" if inside else cc.rounded(near[first]))
        if chosen.hows[first] == ISLAND:
            where += "; " + files_as_island(chosen.country, chosen.states[first])
        elif chosen.hows[first] != "name":
            name += " (" + chosen.states[first] + " by the " + chosen.hows[first] + ")"
        basis = ("coordinates inside " if inside else "in the water, " + cc.rounded(near[first]) + " km from ") + name + ", " + where
        return At(chosen.states[first], chosen.names[first], basis, "", "", other, other_km, flag, chosen.hows[first], bool(inside), chosen.units[first], chosen.label,
                  "" if inside else cc.rounded(near[first]))


def files_as_island(country, island):
    return "AntWeb files this under " + country + " as the island " + island


# ---------------------------------------------------------------- one specimen

Row = collections.namedtuple("Row", "code event locality_code country adm1 adm2 island lat lon stated locality notes bioregion group")
Proposal = collections.namedtuple("Proposal", "field current proposed basis country other other_km flag note code ident bioregion unit_code unit_source",
                                  defaults=("", "", "", "", "", "", "", "", ""))
Not = collections.namedtuple("Not", "field reason detail country", defaults=("",))
Placed = collections.namedtuple("Placed", "at chosen stop note")       # what Corrections.locate says of a point


class Corrections:
    """The rule: what is proposed for one record and why not."""

    def __init__(self, checker, states, shapes, bioregions=None):
        self.checker, self.states, self.shapes, self.bioregions = checker, states, shapes, bioregions
        self.countries = checker.countries
        self.kinds = collections.Counter()                       # what the records looked at were
        self._memo = {}

    def work(self, row):
        """The proposals and the reasons for one record: a list of Proposal and Not."""
        key = (row.country, row.adm1, row.island == "", row.lat, row.lon, row.stated)
        if key not in self._memo:
            if len(self._memo) > 200000:
                self._memo.clear()
            self._memo[key] = self.with_bioregion(row, self._work(row))
        return self._memo[key]

    def with_bioregion(self, row, results):
        """Each proposal with the bioregion AntWeb's definition gives for the place as it will be: the proposed
        country with the state the record has or gets; the proposed state in its country."""
        if self.bioregions is None:
            return results
        state = next((r.proposed for r in results if isinstance(r, Proposal) and r.field == "adm1"), row.adm1)
        return [r if not isinstance(r, Proposal) else r._replace(bioregion=self.bioregions.of(r.country, state if r.field == "country" else r.proposed))
                for r in results]

    def point(self, row):
        try:
            lat, lon = float(row.lat), float(row.lon)
        except ValueError:
            return None
        return (lat, lon) if cc.usable(lat, lon) else None

    def derive(self, row, point, current):
        """The country of a record that has none, or none AntWeb knows: (Proposal or Not, valid country or None,
        Proposal of the state or None). A point in an island country of AntWeb (Hawaii) gives its parent country
        as the country and the island as the state: that is how AntWeb, and so TaxonWorks, files it."""
        prefix = "" if current == "" else "country not valid in AntWeb; "
        if point is None:
            return Not("country", prefix + NO_COORDINATES, ""), None, None
        r = self.checker.derive(point[0], point[1], row.stated)
        if r.cls in (cc.DERIVE_IN, cc.DERIVE_NEAR):
            place = self.countries.rows[r.country]
            basis = ("coordinates inside " if r.cls == cc.DERIVE_IN else "in the water, " + cc.rounded(r.km) + " km from ") + r.country + ", " + r.shape
            parent = self.countries.resolve(place.parent) if place.is_island and place.parent else None
            if parent is None:
                return Proposal("country", current, r.country, basis, r.country), r.country, None
            basis += "; " + files_as_island(parent.name, r.country)
            return Proposal("country", current, parent.name, basis, parent.name), parent.name, Proposal("adm1", "", r.country, basis, parent.name)
        if r.cls == cc.TWO:
            return Not("country", prefix + TWO, r.country), None, None
        if r.cls == cc.DERIVE_NONE:
            return Not("country", prefix + NO_COUNTRY_HERE, r.country), None, None
        nearest = (cc.rounded(r.km) + " km from " + r.country) if r.country else ((cc.rounded(r.km) + " km from the nearest country") if r.km is not None else "")
        return Not("country", prefix + AT_SEA, nearest), None, None

    def converting(self, row, country):
        """The coarser unit (a province of Madagascar) stored as the state of a record, when the mapping file says
        that the country's coarser units are converted to the states of the shapes; None otherwise."""
        units = self.shapes.convert.get(country)
        if not units or not row.adm1 or row.island:
            return None
        stored = self.states.valid_of(country, row.adm1)
        return stored if stored in units else None

    def _work(self, row):
        out = []
        point = self.point(row)
        valid = self.countries.resolve(row.country) if row.country else None
        stated_country = None                                    # the stored country, when it is valid
        island_state = None
        if not row.country or valid is None:
            first, country, island_state = self.derive(row, point, row.country if row.country else "")
            out.append(first)
        else:
            country = stated_country = valid.name
        converting = self.converting(row, country) if stated_country is not None else None
        if (row.adm1 and converting is None) or row.island or country is None:
            return out
        if island_state is not None:
            return out + [island_state]
        if not self.states.of(country):
            return out + [Not("adm1", NO_STATES, country, country)]
        if point is None:
            return out + [Not("adm1", STAYS if converting else NO_COORDINATES, "")]
        placed = self.locate(row.country if stated_country is not None else None, country, point[0], point[1], row.stated)
        if placed.stop is not None:                              # no state for the point; a province stays
            stays = converting and placed.stop.reason == NO_SHAPE_HERE
            return out + [placed.stop._replace(reason=STAYS_OUTSIDE) if stays else placed.stop]
        at = placed.at
        notes = [TO_ADD] if at.how == NEW else []
        if converting:                                           # from the province to the region the coordinates lie in
            if at.state == converting:
                return out
            belongs = self.shapes.within.get(country, {}).get(plain(at.state))
            if belongs is None:
                notes.insert(0, "the mapping file does not say which province " + at.state + " is in")
            else:
                notes.insert(0, SAME_PROVINCE if belongs == converting else OTHER_PROVINCE + " (" + at.state + " is in " + belongs + ")")
        if placed.note:
            notes.append(placed.note)
        return out + [Proposal("adm1", row.adm1, at.state, at.basis, country, at.other, at.other_km, at.flag, "; ".join(notes), at.unit.code, at.unit.ident, "",
                               at.unit.pcode or at.unit.code, at.source if at.unit.pcode or at.unit.code else "")]

    def locate(self, stored, country, lat, lon, stated):
        """The state of one point of a record of a country: a Placed. This is the one place that is asked, by the
        proposals (a state missing, a province to convert) and by the table of Madagascar by region, for every
        source of state shapes, so the same point gets the same answer everywhere. stored: the country as the
        record has it, None when the country was derived from the point itself. Placed(at, chosen, None, note)
        when the point gets a state (at: what state_at says), Placed(None, chosen or None, the Not with the
        reason, "") when not.

        The allowed distance is the tolerance (1 km), or the record's stated error when that is larger.
        1. A point the country shapes put inside another country, farther from its stored country than the
           allowed distance: no state ("coordinates outside the stated country"); the states' file is not asked.
        2. A point inside the shape of one state gets that state.
        3. A point in no state shape that lies within the allowed distance of exactly one state's shape gets
           that state, and the note says "outside the shape, N km from it (coast)". Within the allowed distance
           of two states: no state ("two candidates", both named). Another country inside which the point
           lies, or within the allowed distance: no state (ANOTHER_COUNTRY). No state shape that near: no state.
        4. A point of 2 or 3 that the country's shape has in the water, farther out than the allowed distance:
           the states' file draws land that the country's shape leaves out (the islets Nosy Mangabe, Nosy
           Ankao: 380 specimens of the Academy), or the coordinates are wrong. It gets the state, and the note
           says so (LEFT_OUT, with the distance), for a person to look at."""
        gate = None
        if stored is not None:
            r = self.checker.decide(stored, "", lat, lon, stated)
            if r.cls == cc.TERRITORY:
                return Placed(None, None, Not("adm1", TERRITORY, r.country), "")
            if r.cls == cc.FLAG:
                gate = r
            elif r.cls not in (cc.INSIDE, cc.NEAR):
                return Placed(None, None, Not("adm1", NO_FILE, "the stated country has no shape"), "")
        outside = None if gate is None else Not("adm1", OUTSIDE, gate.country + ", " + cc.rounded(gate.km) + " km from " + country)
        if gate is not None and gate.country not in (cc.WATER, cc.WATER_OR_BORDER):
            return Placed(None, None, outside, "")               # inside another country
        chosen = self.shapes.choose(country, self.checker.code_of_country.get(country, ""))
        if chosen is None:
            return Placed(None, None, outside or Not("adm1", NO_FILE, self.shapes.problems.get(country, ""), country), "")
        allowed = cc.allowed_km(stated, self.checker.tolerance)
        at = self.shapes.state_at(chosen, lat, lon, allowed, self.checker.tolerance)
        if gate is not None and not (chosen.usable and at.state is not None):
            return Placed(None, chosen, outside, "")
        if not chosen.usable:                                    # no proposal from a level below the minimum; the shape is named
            return Placed(None, chosen, Not("adm1", NO_MATCH, at.shape, country), "")
        if at.state is None:
            return Placed(None, chosen, Not("adm1", at.reason, at.detail, country), "")
        notes = []
        if not at.inside:
            other = self.other_country(country, lat, lon, allowed)
            if other is not None:
                return Placed(None, chosen, Not("adm1", ANOTHER_COUNTRY, other + "; " + at.km + " km from " + at.state, country), "")
            notes.append(COAST.format(km=at.km))
        if gate is not None:
            notes.append(LEFT_OUT + " (" + cc.rounded(gate.km) + " km from " + country + " as " + (gate.shape or "the country's shape") + " draws it)")
        return Placed(at, chosen, None, "; ".join(notes))

    def other_country(self, country, lat, lon, km):
        """Another country than the record's (and its territories) that the point lies in or within km of, by the
        country shapes: "<name>, <km> km" of the nearest; None when there is none."""
        checker = self.checker
        _, accept, _ = checker.stored(country, "")
        foreign = lambda n: checker.shapes[n].country not in accept and not (checker.shapes[n].also & accept)
        found = {n: 0.0 for n in checker.index.inside(lat, lon) if foreign(n)}
        for n, d in checker.index.near(lat, lon, km).items():
            if foreign(n):
                found.setdefault(n, d)
        if not found:
            return None
        nearest = min(found, key=lambda n: (found[n], n))
        return checker.names([nearest])[0] + ", " + ("the point lies in it" if found[nearest] == 0.0 else cc.rounded(found[nearest]) + " km")

    def kind(self, row):
        """Which of the three lists of Brian Fisher's note a record belongs to (for the totals of the report)."""
        if not row.country:
            return "country missing"
        valid = self.countries.resolve(row.country)
        if valid is None:
            return "country not valid in AntWeb"
        if row.adm1:
            return "state is a province to convert to its region" if self.converting(row, valid.name) else "has a state (left as it is)"
        if row.island:
            return "state missing, island-country specimen (left out)"
        return "state missing"


# ---------------------------------------------------------------- the lists

CORRECTIONS_HEADER = ["specimen_code", "collecting_event_code", "locality_code", "field", "current_value", "proposed_value", "basis",
                      "nearest_other_state", "km_to_other_state", "other_state_flag", "note",
                      "latitude", "longitude", "stated_error", "locality_name", "country", "upload_group", "unit_iso_3166_2", "unit_ne_id",
                      "taxonworks_predicate", "biogeographic_region", "unit_code", "unit_source"]
EVENTS_HEADER = ["collecting_event_code", "field", "current_value", "proposed_value", "specimens", "first_specimen_code", "last_specimen_code",
                 "locality_name", "latitude", "longitude", "flag", "nearest_other_state", "km_to_other_state", "other_state_flag", "note",
                 "country", "basis", "upload_group", "unit_iso_3166_2", "unit_ne_id", "taxonworks_predicate", "biogeographic_region", "unit_code", "unit_source"]
# the first four of the next two: the cells of a line of adm1_shape_map.csv
TESTED_HEADER = ["country", "level", "shape", "antweb_state", "basis", "specimens_of_the_state_in_the_shape", "percent_of_the_state",
                 "percent_of_the_shape", "the_shape's_specimens_are_filed_under", "unit_iso_3166_2", "unit_ne_id", "unit_code", "unit_source"]
SUGGESTIONS_HEADER = ["country", "level", "shape", "antweb_state", "score", "specimens", "note", "the_shape's_specimens_are_filed_under"]
DECIDE_HEADER = ["country", "level", "what", "name", "parts", "specimens"]
NOT_HEADER = ["collecting_event_code", "field", "reason", "detail", "specimens", "first_specimen_code", "last_specimen_code", "country",
              "state", "adm2", "locality_name", "locality_notes", "bioregion", "latitude", "longitude", "stated_error", "upload_group"]


class Lists:
    """Everything the four files need, gathered record by record."""

    def __init__(self, groups=None):
        self.groups = groups or {}
        self.proposals = []                                      # (Row, Proposal)
        self.nots = []                                           # (Row, Not)
        self.looked = collections.Counter()

    def group(self, row):
        return self.groups.get(row.group, "group " + row.group if row.group else "")

    def add(self, row, results, kind):
        self.looked[kind] += 1
        for r in results:
            (self.proposals if isinstance(r, Proposal) else self.nots).append((row, r))

    def corrections(self):
        lines = []
        states = {(row.code): p.proposed for row, p in self.proposals if p.field == "adm1"}
        for row, p in self.proposals:
            lines.append(((p.country, states.get(row.code, ""), row.event, row.code, p.field != "country"),
                          [row.code, row.event, row.locality_code, p.field, p.current, p.proposed, p.basis, p.other, p.other_km, p.flag, p.note,
                           row.lat, row.lon, row.stated, row.locality, p.country, self.group(row), p.code, p.ident, PREDICATE.get(p.field, ""), p.bioregion, p.unit_code, p.unit_source]))
        return [line for _, line in sorted(lines, key=lambda x: x[0])]

    def events(self):
        """One line per collecting event, field and current value. A collecting event whose specimens get different
        proposals is flagged."""
        by_event = collections.defaultdict(list)
        for row, p in self.proposals:
            by_event[(row.event or "\0" + row.code, p.field, p.current)].append((row, p))
        more = collections.Counter((row.event, n.field) for row, n in self.nots if row.event)
        several = lambda texts, of: "" if not texts else (texts.most_common(1)[0][0] if sum(texts.values()) == of and len(texts) == 1 else
                                                           cc.n(texts.most_common(1)[0][1]) + " of " + cc.n(of) + " specimens: " + texts.most_common(1)[0][0])
        lines = []
        for (event, field, current), items in by_event.items():
            items.sort(key=lambda x: x[0].code)
            values = collections.Counter(p.proposed for _, p in items)
            row, p = items[0]
            flags = []
            if len(values) > 1:
                flags.append("DIFFERENT PROPOSALS, do not enter as it stands: " + "; ".join(v + " (" + cc.n(c) + ")" for v, c in sorted(values.items())))
            if row.event and more[(row.event, field)]:
                flags.append(cc.n(more[(row.event, field)]) + " more of this collecting event in not_derivable.csv")
            places = {(r.lat, r.lon) for r, _ in items}
            if len(places) > 1:
                flags.append(cc.n(len(places)) + " different coordinates in this collecting event")
            flagged = collections.Counter(x.flag for _, x in items if x.flag)
            near = ""
            if flagged:                                          # another state within the stated error: for a person to look at first
                near = flagged.most_common(1)[0][0]
                if sum(flagged.values()) < len(items) or len(flagged) > 1:
                    near = cc.n(sum(flagged.values())) + " of " + cc.n(len(items)) + " specimens: " + near
            lines.append(((p.country, " | ".join(sorted(values)), row.event, field != "country", current),
                          [row.event, field, p.current, " | ".join(sorted(values)), len(items), items[0][0].code, items[-1][0].code,
                           row.locality, row.lat, row.lon, ". ".join(flags), p.other if len(values) == 1 else "", p.other_km if len(values) == 1 else "", near,
                           several(collections.Counter(x.note for _, x in items if x.note), len(items)),
                           p.country, p.basis if len(values) == 1 else "", self.group(row), p.code if len(values) == 1 else "", p.ident if len(values) == 1 else "",
                           PREDICATE.get(field, ""), p.bioregion if len({x.bioregion for _, x in items}) == 1 else "",
                           p.unit_code if len(values) == 1 else "", p.unit_source if len(values) == 1 else ""]))
        return [line for _, line in sorted(lines, key=lambda x: x[0])]

    def not_derivable(self):
        by_key = collections.defaultdict(list)
        for row, n in self.nots:
            by_key[(row.event or "\0" + row.code, n.field, n.reason, n.detail)].append(row)
        lines = []
        for (event, field, reason, detail), rows in by_key.items():
            rows.sort(key=lambda r: r.code)
            row = rows[0]
            lines.append(((field, reason, row.country, row.event, row.code),
                          [row.event, field, reason, detail, len(rows), rows[0].code, rows[-1].code, row.country, row.adm1, row.adm2, row.locality,
                           row.notes, row.bioregion, row.lat, row.lon, row.stated, self.group(row)]))
        return [line for _, line in sorted(lines, key=lambda x: x[0])]

    def in_shapes(self):
        """How many records lie in each shape that gave no proposal: {(country, shape name): specimens}."""
        counts = collections.Counter()
        for row, x in self.nots:
            if x.reason in (NOT_A_STATE, NO_MATCH) and x.detail:
                counts[(x.country, x.detail)] += 1
        return counts


def suggestions(shapes, states, counts):
    """What is left after the test with specimens: (lines of mapping_suggestions.csv, shapes left out). For each
    shape of a country's level that got no state: the AntWeb valid states of that country whose name, or an old
    name of which, is most like it, the records of this run that lie in it, and what AntWeb's specimens in it
    are filed under today. A shape is listed when records lie in it or a state is like it. Nothing is used from
    this list by the tool."""
    lines, left_out = [], 0
    for country in sorted(shapes.tables):
        t = shapes.tables[country]
        for shape in t["shapes_not_states"]:
            found = states.closest(country, shape)
            found = [x for x in found if x[1] not in shapes.convert.get(country, ())]
            specimens = counts.get((country, shape), 0)
            if not found and not specimens:
                left_out += 1
                continue
            found.sort(key=lambda x: (x[1] in t["shape_of"], -x[0], x[1]))          # a state that has no shape yet comes first
            for score, state, old in found or [("", "", "")]:
                notes = []
                if not state:
                    notes.append("no AntWeb state of " + country + " has a name like it")
                if old:
                    notes.append("by AntWeb's old name " + old)
                if state in t["shape_of"]:
                    notes.append("the state has a shape already: " + t["shape_of"][state])
                lines.append(((country, -specimens, shape, -(score or 0)),
                              [country, t["level"], shape, state, format(score, ".2f") if state else "", specimens, "; ".join(notes), t["today"].get(shape, "")]))
    return [line for _, line in sorted(lines, key=lambda x: x[0])], left_out


def tested_lines(shapes):
    """The lines of mapping_tested.csv: for each country, the state of each shape of the level used and what it
    rests on, with the numbers of the test."""
    return [line for country in sorted(shapes.tables) for line in sorted(shapes.tables[country]["lines"], key=lambda x: (x[2], x[4]))]


def decide_lines(shapes):
    """The lines of to_decide.csv: shapes that hold several AntWeb states, AntWeb states that lie in several shapes."""
    return [line for country in sorted(shapes.tables) for line in shapes.tables[country]["decide"]]


DIFFERS_HEADER = ["collecting_event_code", "locality_name", "specimens", "first_specimen_code", "stored_value", "stored_value_is", "state_by_coordinates",
                  "province_of_that_state", "km_to_the_stored_value", "nearest_other_state", "km_to_the_nearest_border", "latitude", "longitude", "stated_error",
                  "basis", "note", "country"]
IN_THE_WATER = "in the water, "                                  # how the basis of a point outside every shape begins (state_at)


def by_region(work, code, rows):
    """Madagascar by region, and the lines of stored_differs.csv. rows: (collecting event code, locality name,
    stored state, island country, latitude, longitude, stated error, specimens, first specimen code) of every
    record of Madagascar, counted by those. Each point is placed by Corrections.locate, the function the
    proposals are made with: a record with a province gets a proposal exactly when it is counted in a region
    here (and the region is another name than the stored one).
    Returns (table, totals, reasons, lines), or None when there are no shapes of the country.
      table    {region: [specimens placed in it, of them in the water within the allowed distance, stored state
               its province, stored state the region itself, another stored state, no stored state]}
      totals   Counter: specimens, placed, in the water, left out by the country's shape, no coordinates,
               coordinates that cannot be used, province placed (records with a province that a proposal is made for)
      reasons  Counter of the reasons a point with coordinates was not placed (the reasons of not_derivable.csv)
      lines    of stored_differs.csv (DIFFERS_HEADER): the records whose stored state is neither the region the
               coordinates lie in nor that region's province, by collecting event and place."""
    shapes, states = work.shapes, work.states
    chosen = shapes.choose(MADAGASCAR, code)
    if chosen is None:
        return None
    regions = {state for state, how in zip(chosen.states, chosen.hows) if state and how != SPLIT} | set(shapes.split_targets(MADAGASCAR, chosen.names, chosen.hows))
    within = shapes.within.get(MADAGASCAR, {})
    provinces = shapes.convert.get(MADAGASCAR, ())
    table = {region: [0, 0, 0, 0, 0, 0] for region in regions}
    totals, reasons = collections.Counter(), collections.Counter()
    placed_at, numbers_of, lines = {}, {}, []
    for event, locality, adm1, island, lat, lon, stated, count, first in rows:
        totals["specimens"] += count
        try:
            point = (float(lat), float(lon))
        except ValueError:
            totals["no coordinates"] += count
            continue
        if not cc.usable(*point):
            totals["coordinates that cannot be used"] += count
            continue
        key = point + (stated,)
        if key not in placed_at:
            placed_at[key] = work.locate(MADAGASCAR, MADAGASCAR, point[0], point[1], stated)
        placed = placed_at[key]
        if placed.stop is not None:
            reasons[placed.stop.reason] += count
            continue
        at = placed.at
        totals["placed"] += count
        line = table.setdefault(at.state, [0, 0, 0, 0, 0, 0])
        line[0] += count
        if at.basis.startswith(IN_THE_WATER):
            totals["in the water"] += count
            line[1] += count
        if LEFT_OUT in placed.note:
            totals["left out by the country's shape"] += count
        stored = states.valid_of(MADAGASCAR, adm1) if adm1 else None
        if stored in provinces and not island and at.state != stored:
            totals["province placed"] += count
        if not adm1:
            line[5] += count
        elif stored is not None and stored == within.get(plain(at.state)):
            line[2] += count
        elif stored is not None and stored == at.state:
            line[3] += count
        else:                                                    # another province, another region, a name AntWeb does not have
            line[4] += count
            if stored not in numbers_of:
                numbers_of[stored] = shapes.shapes_of(placed.chosen, stored) if stored else ()
            numbers = numbers_of[stored]
            index = placed.chosen.index
            here = set(index.inside(*point))
            km = index.distance(numbers, *point) if numbers and not here & set(numbers) else None
            other, border = at.other, at.other_km
            if border == "":                                     # no other state within 25 km: the nearest one, however far
                far = [(index.distance((n,), *point), state or name) for n, (state, name) in enumerate(zip(placed.chosen.states, placed.chosen.names))
                       if n not in here and (state or name) != at.state]
                far = sorted((round(d, 3), name) for d, name in far if d is not None)
                if far:
                    other, border = far[0][1], cc.rounded(far[0][0])
            lines.append(((at.state, stored or adm1, -count, event, locality),
                          [event, locality, count, first, adm1, "a province" if stored in provinces else "a state" if stored else "no state of Madagascar in AntWeb",
                           at.state, within.get(plain(at.state)) or "", cc.rounded(km) if km is not None else "", other, border, lat, lon, stated, at.basis,
                           placed.note, MADAGASCAR]))
    return table, totals, reasons, [line for _, line in sorted(lines, key=lambda x: x[0])]


# ---------------------------------------------------------------- the load file for TaxonWorks

TW_CODE = "fieldNumber"                                           # AntWeb's collecting event code, after "eventID:" (TaxonWorksTransformer)
TW_ID = "TW:Internal:collecting_event_id"
TW_PREDICATES = ["Country", "adm1", "BiogeographicRegion"]        # the data attributes of the collecting event that AntWeb's upload reads
TW_ATTRIBUTE = "TW:DataAttribute:CollectingEvent:"
TW_LOAD_HEADER = ["ce_code", "predicate", "action", "current_value", "new_value", "latitude", "longitude", "collecting_event_id"]
TW_UNRESOLVED_HEADER = ["ce_code", "tw_collecting_event_id", "predicate", "proposed_value", "reason", "specimens", "first_specimen_code", "latitude", "longitude"]
USUAL_CODE = re.compile(r"^[A-Za-z]+[0-9]+$")                     # letters, then digits: one namespace for each prefix in TaxonWorks

NOT_IN_ARCHIVE = "code not in the TaxonWorks archive"
BLANK_CODE = "blank code"
CODE_WITH_BLANK = "code contains a blank"
EVENT_WITH_CODES = "the collecting event has more than one code in TaxonWorks"
EVENT_MIXED = "TaxonWorks holds different values on the rows of this collecting event"
DIFFERENT_PROPOSALS = "specimens of one event give different proposals"
NEAR_ANOTHER = "within tolerance of another state"
HOLDS_A_TAB = "value holds a tab or a line break"
NOT_YET = "proposed state not yet in AntWeb"
ISLAND_STATE = "TaxonWorks holds another state for an island that AntWeb files as a country of its own"
BAD_BIOREGION = "AntWeb's stored bioregion is not one of AntWeb's bioregions"


def on_events(count):
    return "code on " + cc.n(count) + " collecting events in TaxonWorks"


class TwEvent:
    __slots__ = ("id", "codes", "values", "lat", "lon", "rows", "mixed")

    def __init__(self, ident, values, lat, lon):
        self.id, self.codes, self.values, self.lat, self.lon, self.rows, self.mixed = ident, [], values, lat, lon, 0, False


class TwArchive:
    """The archive TaxonWorks writes for AntWeb every day (data.tsv, tab-delimited, no quoting; a path to the
    file or to the .zip that holds it): for each collecting event its code as AntWeb reads it, the three
    predicates Country, adm1 and BiogeographicRegion as TaxonWorks holds them, and its coordinates. Nothing else
    of the file is kept."""

    def __init__(self, path):
        self.path = path
        self.events = {}                                         # TaxonWorks' collecting event id -> TwEvent
        self.by_code = collections.defaultdict(list)             # code -> the ids of the collecting events that carry it
        self.rows = self.rows_without_id = 0
        self.rows_of = collections.Counter()                     # code -> rows of the archive
        self.date = datetime.datetime.fromtimestamp(os.path.getmtime(path)).strftime("%Y-%m-%d %H:%M")
        if zipfile.is_zipfile(path):
            with zipfile.ZipFile(path) as z:
                members = [m for m in z.infolist() if os.path.basename(m.filename) == "data.tsv"]
                if not members:
                    sys.exit(path + " holds no data.tsv. Nothing was done.")
                self.date = "%04d-%02d-%02d %02d:%02d" % members[0].date_time[:5]
                with z.open(members[0]) as f:
                    self._read(f)
        else:
            with open(path, "rb") as f:
                self._read(f)

    def _read(self, f):
        head = f.readline().decode("utf-8-sig").rstrip("\r\n").split("\t")
        wanted = [TW_CODE, TW_ID] + [TW_ATTRIBUTE + x for x in TW_PREDICATES] + ["decimalLatitude", "decimalLongitude"]
        missing = [name for name in wanted if name not in head]
        if missing:
            sys.exit(self.path + " has no column " + ", ".join(missing) + ". Nothing was done.")
        code_at, id_at, country_at, adm1_at, bio_at, lat_at, lon_at = (head.index(name) for name in wanted)
        last = max(code_at, id_at, country_at, adm1_at, bio_at, lat_at, lon_at)
        for raw in f:
            cells = raw.decode("utf-8", "replace").rstrip("\r\n").split("\t")
            if len(cells) <= last:
                cells += [""] * (last + 1 - len(cells))
            self.rows += 1
            ident = cells[id_at].strip()
            if not ident:
                self.rows_without_id += 1
                continue
            code = cells[code_at]
            code = code[len("eventID:"):] if code.startswith("eventID:") else code
            values = (cells[country_at].strip(), cells[adm1_at].strip(), cells[bio_at].strip())
            event = self.events.get(ident)
            if event is None:
                event = self.events[ident] = TwEvent(ident, values, cells[lat_at].strip(), cells[lon_at].strip())
            elif event.values != values:
                event.mixed = True
            event.rows += 1
            self.rows_of[code] += 1
            if code not in event.codes:
                event.codes.append(code)
                self.by_code[code].append(ident)

    def find(self, code):
        """(the TwEvent of a code, "") or (None, why the code does not name one collecting event)."""
        if not code:
            return None, BLANK_CODE
        if any(c.isspace() for c in code):
            return None, CODE_WITH_BLANK
        ids = self.by_code.get(code, ())
        if not ids:
            return None, NOT_IN_ARCHIVE
        if len(ids) > 1:
            return None, on_events(len(ids))
        event = self.events[ids[0]]
        if len(event.codes) > 1:
            return None, EVENT_WITH_CODES
        if event.mixed:
            return None, EVENT_MIXED
        return event, ""

    def summary(self):
        """What the archive alone says, without the database: the lines for the report."""
        n = cc.n
        codes = [c for c in self.by_code if c]
        odd = [c for c in codes if not USUAL_CODE.match(c)]
        rows_of = self.rows_of
        kinds = collections.Counter()
        for code in odd:
            kinds[re.sub(r"[A-Za-z]+", "A", re.sub(r"[0-9]+", "9", code)).replace(" ", "_blank_")] += 1
        blank = lambda at: sum(1 for e in self.events.values() if not e.values[at])
        out = ["TaxonWorks archive " + self.path + ", dated " + self.date + ": " + n(self.rows) + " rows, " + n(len(self.events)) + " collecting events, "
               + n(len(codes)) + " codes" + (" (" + n(self.rows_without_id) + " rows with no collecting event id left out)" if self.rows_without_id else "") + ".",
               "  Collecting events with no Country predicate: " + n(blank(0)) + "; no adm1: " + n(blank(1)) + "; no BiogeographicRegion: " + n(blank(2)) + ".",
               "  Codes on more than one collecting event: " + n(sum(1 for c in codes if len(self.by_code[c]) > 1)) + ". Collecting events with a blank code: "
               + n(len(self.by_code.get("", ()))) + ". Codes that contain a blank: " + n(sum(1 for c in codes if any(x.isspace() for x in c)))
               + ". Collecting events with more than one code: " + n(sum(1 for e in self.events.values() if len(e.codes) > 1))
               + ". Collecting events whose rows differ in a predicate: " + n(sum(1 for e in self.events.values() if e.mixed)) + ".",
               "  Codes that are letters followed by digits: " + n(len(codes) - len(odd)) + " (" + n(sum(rows_of[c] for c in codes if USUAL_CODE.match(c))) + " rows), with "
               + n(len({re.match(r"[A-Za-z]+", c).group(0) for c in codes if USUAL_CODE.match(c)})) + " prefixes. Other codes: " + n(len(odd)) + " (" + n(sum(rows_of[c] for c in odd))
               + " rows), by their shape (A letters, 9 digits), the 15 most common:"]
        shown = sorted(kinds.items(), key=lambda kv: (-kv[1], kv[0]))
        return (out + ["  " + n(count).rjust(9) + "  " + kind for kind, count in shown[:15]]
                + (["  " + "".rjust(9) + "  ... and " + n(len(shown) - 15) + " more shapes (" + n(sum(c for _, c in shown[15:])) + " codes)"] if len(shown) > 15 else []))


def tw_events_sql(columns, group_id):
    """AntWeb's specimens of the group, counted by collecting event code and what is stored of the place: for the
    country, the island and the bioregion of each collecting event."""
    text = lambda name: "hex(ifnull(s." + name + ", ''))" if name in columns else "''"
    return ("select " + ", ".join([text("collectioncode"), text("country"), text("adm1"), text("island_country"), text("bioregion")])
            + ", count(*), hex(min(s.code)), ifnull(round(min(s.decimal_latitude), 5), ''), ifnull(round(min(s.decimal_longitude), 5), '')"
            + " from specimen s where s.access_group = " + str(int(group_id)) + " group by 1, 2, 3, 4, 5")


def tw_load(archive, lists, stored, countries, states, bioregions, include_flagged=False):
    """The rows for TaxonWorks: (lines of tw_load.tsv, lines of tw_bioregion_differs.tsv, lines of
    tw_unresolved.csv, counts). One line for each collecting event and predicate that TaxonWorks has to create
    (it holds no value) or update (it holds another one). current_value is always TaxonWorks' own value, from the
    archive: its loader skips a line whose current_value is not what it holds.

    stored: {collecting event code: [(country, adm1, island country, bioregion, specimens, first specimen code,
    latitude, longitude)]} of every specimen of the group, as AntWeb stores them. lists: the proposals of this run.
      Country   what AntWeb has for every specimen of the event: one valid country (stored, or proposed from the
                coordinates). create when TaxonWorks holds none; update when it holds an old name or another
                spelling of that country, a name AntWeb does not know, or an island AntWeb files under it.
      adm1      the state proposed from the coordinates (state missing; a province of Madagascar to its region).
                Left out, and listed in tw_unresolved.csv: a proposal within the tolerance of another state
                (unless include_flagged), a state AntWeb does not have yet, a province that is not the province
                of the region the coordinates lie in, specimens of one event with different proposals.
      islands   AntWeb needs Country = the parent country and adm1 = the island (United States, Hawaii).
      BiogeographicRegion   create when TaxonWorks holds none and every specimen of the event has the same
                bioregion in AntWeb. Where TaxonWorks holds another one than AntWeb: tw_bioregion_differs.tsv."""
    by_event = collections.defaultdict(lambda: collections.defaultdict(list))
    for row, p in lists.proposals:
        by_event[row.event][p.field].append((row, p))
    load, differs, unresolved = [], [], []
    counts = {"load": collections.Counter(), "already": collections.Counter(), "unresolved": collections.Counter(), "differs": 0}
    order = {name: i for i, name in enumerate(TW_PREDICATES)}

    def out(event, code, predicate, new, group, target=None):
        """A line of the load file (or of target), or of the unresolved list when a value cannot be written."""
        current = event.values[order[predicate]]
        if current == new:
            counts["already"][predicate] += 1
            return
        if any(c in value for value in (code, current, new) for c in "\t\r\n"):
            return hold(event, code, predicate, new, HOLDS_A_TAB, group)
        action = "update" if current else "create"
        line = [code, predicate, action, current, new, event.lat or group[2], event.lon or group[3], event.id]
        if target is None:
            counts["load"][(predicate, action)] += 1
            load.append(line)
        else:
            counts["differs"] += 1
            target.append(line)

    def hold(event, code, predicate, proposed, reason, group):
        counts["unresolved"][reason] += 1
        unresolved.append([code, event.id if event is not None else "", predicate, proposed, reason, group[0], group[1],
                           (event.lat if event is not None else "") or group[2], (event.lon if event is not None else "") or group[3]])

    for code in sorted(set(stored) | set(by_event)):
        specimens = stored.get(code, [])
        proposals = by_event.get(code, {})
        group = (sum(x[4] for x in specimens), min((x[5] for x in specimens), default=""), next((x[6] for x in specimens if x[6]), ""), next((x[7] for x in specimens if x[7]), ""))
        of = lambda items: (len(items), min(r.code for r, _ in items), items[0][0].lat, items[0][0].lon)
        event, why = archive.find(code)
        if event is None:                                        # the code names no single collecting event: what was proposed is listed, nothing else is known
            for field, predicate in (("country", "Country"), ("adm1", "adm1")):
                by_value = collections.defaultdict(list)
                for item in proposals.get(field, ()):
                    by_value[item[1].proposed].append(item)
                for value, items in sorted(by_value.items()):
                    hold(None, code, predicate, value, why, of(items))
            continue
        tw_country, tw_adm1, tw_bio = event.values

        # an island AntWeb files as a country of its own: Country is its parent, adm1 the island
        islands = {x[2] for x in specimens}
        island = countries.resolve(next(iter(islands))) if len(islands) == 1 and "" not in islands else None
        parent = countries.resolve(island.parent) if island is not None and island.is_island and island.parent else None
        if parent is not None:
            if tw_adm1 and plain(tw_adm1) != plain(island.name):
                hold(event, code, "adm1", island.name, ISLAND_STATE, group)
            else:
                out(event, code, "Country", parent.name, group)
                out(event, code, "adm1", island.name, group)
        else:
            # the country
            names, uncovered = set(), 0
            for x in specimens:
                valid = countries.resolve(x[0]) if x[0] else None
                if valid is not None:
                    names.add(valid.name)
                else:
                    uncovered += x[4]
            proposed = proposals.get("country", [])
            names |= {p.proposed for _, p in proposed}
            if len(names) > 1:
                if tw_country not in names:
                    hold(event, code, "Country", " | ".join(sorted(names)), DIFFERENT_PROPOSALS, group)
            elif names and len(proposed) >= uncovered:
                name = next(iter(names))
                held = countries.resolve(tw_country) if tw_country else None
                if not tw_country or held is None or held.name == name:
                    out(event, code, "Country", name, group)
                else:
                    hold(event, code, "Country", name, "TaxonWorks holds " + tw_country + ", AntWeb has " + name + ": the archive and AntWeb differ", group)
            # the state
            items = proposals.get("adm1", [])
            if items:
                values = sorted({p.proposed for _, p in items})
                first = items[0][1]
                if len(values) > 1:
                    hold(event, code, "adm1", " | ".join(values), DIFFERENT_PROPOSALS, of(items))
                elif any(TO_ADD in p.note for _, p in items):
                    hold(event, code, "adm1", values[0], NOT_YET, of(items))
                elif any(p.note.startswith(OTHER_PROVINCE) for _, p in items):
                    hold(event, code, "adm1", values[0], OTHER_PROVINCE, of(items))
                elif any(p.flag for _, p in items) and not include_flagged:
                    hold(event, code, "adm1", values[0], NEAR_ANOTHER, of(items))
                elif any(LEFT_OUT in p.note for _, p in items) and not include_flagged:
                    hold(event, code, "adm1", values[0], LEFT_OUT, of(items))
                elif tw_adm1 and tw_adm1 != values[0] and any(p.current != tw_adm1 for _, p in items):
                    hold(event, code, "adm1", values[0], "TaxonWorks holds " + tw_adm1 + ", AntWeb has " + (first.current or "no state") + ": the archive and AntWeb differ", of(items))
                else:
                    out(event, code, "adm1", values[0], of(items))
        # the bioregion
        stored_bio = {no_bioregion(x[3]) for x in specimens}
        if len(stored_bio) == 1 and "" not in stored_bio:
            bio = next(iter(stored_bio))
            if bioregions and bio not in bioregions:
                if tw_bio != bio:
                    hold(event, code, "BiogeographicRegion", bio, BAD_BIOREGION, group)
            elif tw_bio and tw_bio != bio:
                out(event, code, "BiogeographicRegion", bio, group, differs)
            else:
                out(event, code, "BiogeographicRegion", bio, group)
        elif not tw_bio and len(stored_bio - {""}) > 1:
            hold(event, code, "BiogeographicRegion", " | ".join(sorted(stored_bio - {""})), DIFFERENT_PROPOSALS, group)
    key = lambda line: (line[0], order[line[1]])
    return sorted(load, key=key), sorted(differs, key=key), sorted(unresolved, key=lambda x: (x[4], x[0], x[2])), counts


def write_tsv(path, header, lines):
    """Tab-delimited, UTF-8 without a byte order mark, one header row, no quoting at all, line ends \\n: the format
    the TaxonWorks team asked for. (A value with a tab or a line break never comes here: tw_load.)"""
    with open(path, "w", encoding="utf-8", newline="") as f:
        csv.writer(f, delimiter="\t", quoting=csv.QUOTE_NONE, quotechar=None, escapechar=None, lineterminator="\n").writerows([header] + lines)


def tw_report(archive, counts, include_flagged):
    """The lines of the report on the load file: collecting events, not specimens."""
    n = cc.n
    out = ["", "For TaxonWorks (tw_load.tsv: one line for each collecting event and predicate; the counts are collecting events):"]
    out += ["  " + "predicate".ljust(22) + "create".rjust(10) + "update".rjust(10) + "already set in TaxonWorks".rjust(28)]
    for predicate in TW_PREDICATES:
        out.append("  " + predicate.ljust(22) + n(counts["load"][(predicate, "create")]).rjust(10) + n(counts["load"][(predicate, "update")]).rjust(10)
                   + n(counts["already"][predicate]).rjust(28))
    out += ["  BiogeographicRegion where TaxonWorks holds another one than AntWeb (tw_bioregion_differs.tsv, not in the load file, to decide): " + n(counts["differs"]),
            "  Proposals within the tolerance of another state are " + ("in the load file (--tw-include-flagged)." if include_flagged else "left out of the load file."),
            "  Not in the load file (tw_unresolved.csv): " + n(sum(counts["unresolved"].values())) + ", by reason:"]
    merged = collections.Counter()
    for reason, count in counts["unresolved"].items():
        merged[re.sub(r"^TaxonWorks holds .*: the archive and AntWeb differ$", "TaxonWorks holds another value than AntWeb: the archive and AntWeb differ", reason)] += count
    return out + cc.lines_of(merged, str, limit=20) + archive.summary()


def write_csv(path, header, lines):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(lines)


SUMMARY_LINES = 60


def top(counter, limit):
    """Counted lines, largest first, never more than limit lines: the rest is one line."""
    rows = sorted(counter.items(), key=lambda kv: (-kv[1], str(kv[0])))
    if len(rows) > limit:
        rest = rows[limit - 1:]
        rows = rows[:limit - 1] + [(cc.n(len(rest)) + " other reasons", sum(c for _, c in rest))]
    return ["  " + cc.n(count).rjust(9) + "  " + str(key) for key, count in rows] or ["  none"]


def level_line(country, t):
    """One line for a country: how each level fits, which is used and why."""
    looked = [x["level"] for x in t["tried"]]
    rest = [level for level in t.get("levels", LEVELS) if level not in looked]
    return ("  " + country + " (" + t["code"] + "): " + "; ".join(level_text(x) for x in t["tried"])
            + ("; " + (rest[0] if len(rest) == 1 else rest[0] + " to " + rest[-1]) + " not looked at" if rest else "")
            + (" | used: " if t["usable"] else " | NONE USED; a mapping would be for ") + t["level"] + " (" + t["why"] + ")")


def compared(sources):
    """The sources of state shapes compared on AntWeb's own specimens: the lines of the report. sources:
    [(label, StateShapes)] after each has chosen its units for the countries. For each country and source:
    the specimens that have a state and coordinates, and the share of them that lie in the unit confirmed or
    matched for their state (agreement), in the unit of another state, in a unit with no AntWeb state, in no
    unit (the coast, or a country the source has no shapes for)."""
    n = cc.n
    pct = lambda part, whole: (format(100.0 * part / whole, ".1f") if whole else "").rjust(7)
    countries = sorted({c for _, shapes in sources for c in list(shapes.tables) + list(shapes.problems)})
    rows, totals, without = [], [collections.Counter() for _ in sources], [[0, 0] for _ in sources]
    for country in countries:
        cells, most = [], 0
        for k, (label, shapes) in enumerate(sources):
            t = shapes.tables.get(country)
            if t is None:                                        # the source has no shapes of the country: not in its total
                have = sum(x[3] for x in shapes.evidence.points.get(country, ()) if x[0] not in shapes.convert.get(country, ())) if shapes.evidence is not None else 0
                counts, level = collections.Counter({SHARES[3]: have}), "none"
                without[k][0] += 1
                without[k][1] += have
            else:
                counts, level = t["stats"]["shares"], t["level"] + ("" if t["usable"] else "*")
                totals[k].update(counts)
            whole = sum(counts.values())
            most = max(most, whole)
            cells.append(level.ljust(6) + "".join(pct(counts[x], whole) for x in SHARES))
        rows.append((most, country, cells))
    width = 6 + 7 * len(SHARES)
    out = ["", "Sources compared. AntWeb's specimens that have a state and coordinates (of every group; units being converted, such as the provinces of Madagascar, left",
           "out), by where they lie in the units of each source, in percent: in the unit confirmed or matched for their state (agreement), in the unit of",
           "another state, in a unit with no AntWeb state, in no unit (the coast). level*: the country gives no proposals. none: the source has no shapes of the",
           "country, or was not asked for it; such a country is not in the source's line for all countries.",
           "  " + "country".ljust(28) + "specimens".rjust(10) + "".join("  | " + label[:width].ljust(width) for label, _ in sources),
           "  " + "".ljust(38) + "".join("  | " + "level".ljust(6) + "  agree  other no st.no unit" for _ in sources),
           "  " + "ALL IT HAS SHAPES OF".ljust(28) + "".rjust(10) + "".join("  | " + "".ljust(6) + "".join(pct(t[x], sum(t.values())) for x in SHARES) for t in totals),
           "  " + "  specimens in them".ljust(28) + "".rjust(10) + "".join("  | " + (n(sum(t.values())) + "; none: " + n(w[0]) + " countries, " + n(w[1]))[:width].ljust(width)
                                                                         for t, w in zip(totals, without))]
    for most, country, cells in sorted(rows, key=lambda r: (-r[0], r[1])):
        out.append("  " + country[:28].ljust(28) + n(most).rjust(10) + "".join("  | " + cell for cell in cells))
    return out, totals


def consistency_lines(labelled):
    """The lines of the report on the consistency of the sources, and (countries measured, countries with units
    under the mark). labelled: [(label, StateShapes)]; each country and source once."""
    n = cc.n
    seen, lines, bad = set(), [], 0
    for _, shapes in labelled:
        for country in sorted(shapes.tables):
            c = shapes.tables[country].get("consistency")
            key = (shapes.tables[country].get("label"), country)
            if c is None or key in seen:
                continue
            seen.add(key)
            start = "  " + country + ", " + key[0] + ", " + c["level"]
            if not c["lower"]:
                lines.append(start + ": not measured (" + c["why"] + ")")
                continue
            bad += 1 if c["under"] else 0
            lines.append(start + " and " + c["lower"] + ": " + n(c["under"]) + " of " + n(c["measured"]) + " units of " + c["lower"] + " lie less than "
                         + format(100 * ONE_STATE, ".0f") + "% inside one shape of " + c["level"] + (" (" + n(c["units"] - c["measured"]) + " too small to measure)"
                                                                                                   if c["units"] > c["measured"] else "")
                         + (". The worst: " + "; ".join(c["worst"]) if c["worst"] else ""))
    if not lines:
        return [], 0, 0
    measured = sum(1 for x in lines if "not measured" not in x)
    head = ["", "Consistency of the source: where a source has the state level and the next level down, each unit of the lower level should lie inside one state",
            "shape. Measured on a grid of about " + n(GRID_POINTS) + " points over the country; points in no state shape (the coast) do not count. A source whose two",
            "levels come from different makers shows here (geoBoundaries' Madagascar: regions from OpenStreetMap, districts from the disaster management office)."]
    return head + lines, measured, bad


def region_lines(table, totals, reasons, province_of, where, group_text, proposals, differs):
    """The lines of the report for Madagascar by region (by_region). proposals: the proposals of this run from a
    province to its region, to say that the table and the proposals count the same records; differs: the
    lines of stored_differs.csv."""
    n = cc.n
    same = totals["province placed"] == proposals
    out = ["", "Madagascar by region: every record of " + MADAGASCAR + " of " + ("every group" if group_text == "every group" else "the group")
           + " by the region its coordinates lie in, not only the records with a gap; " + where + ", " + n(len(table)) + " regions.",
           "  Each point is placed by the function that makes the proposals: inside one region's shape, or in no shape and within the allowed distance (1 km, or the",
           "  stated error when larger) of one region alone and of no other country.",
           "  Records of Madagascar: " + n(totals["specimens"]) + ". Placed in a region: " + n(totals["placed"]) + " (" + n(totals["in the water"])
           + " of them outside its shape, within the allowed distance of it alone; " + n(totals["left out by the country's shape"]) + " at a place the country's shape leaves out)."
           + " No coordinates: " + n(totals["no coordinates"]) + ". Coordinates that cannot be used: " + n(totals["coordinates that cannot be used"]) + ".",
           "  With coordinates and not placed: " + n(sum(reasons.values())) + ("." if not reasons else ", by reason: " + "; ".join(
               n(count) + " " + reason for reason, count in sorted(reasons.items(), key=lambda kv: (-kv[1], kv[0]))) + "."),
           "  Records with a province that are placed in a region: " + n(totals["province placed"]) + "; proposals from a province to its region in this run: " + n(proposals)
           + (" (the same)." if same else " (NOT THE SAME, and they should be: both come from one function)."),
           "  stored_differs.csv: " + n(len(differs)) + " lines, " + n(sum(x[2] for x in differs)) + " specimens whose stored province or state is not the one their coordinates"
           + " fall in (the column \"another\"), by collecting event.",
           "  " + "region".ljust(26) + "specimens".rjust(10) + "outside shape".rjust(14) + "  " + "its province".ljust(25) + "stored state: that province".rjust(28)
           + "the region".rjust(12) + "another".rjust(10) + "none".rjust(10)]
    for region in sorted(table):
        inside, water, same, itself, another, none = table[region]
        out.append("  " + region.ljust(26) + n(inside).rjust(10) + n(water).rjust(14) + "  " + (province_of(region) or "not in the mapping file").ljust(25)
                   + n(same).rjust(28) + n(itself).rjust(12) + n(another).rjust(10) + n(none).rjust(10))
    return out


def split_lines(lists, shapes):
    """The lines of the report on the shapes the mapping file splits: the lines used, and for each state the
    specimens and collecting events proposed for it."""
    if not shapes.splits_used:
        return []
    n = cc.n
    out = ["", "Shapes told apart by finer units (the split lines of the mapping file that were used):"]
    out += ["  " + country + ", " + shape + ", by " + level + ": " + ("every other unit" if unit == "*" else unit) + " -> " + state + (" (" + note + ")" if note else "")
            for country, shape, level, unit, state, note in shapes.splits_used]
    targets = collections.OrderedDict()
    for country, shape, level, unit, state, note in shapes.splits_used:
        c = shapes.countries.resolve(country)
        country = c.name if c is not None else country
        valid = shapes.states.find(country, state)[0]
        targets.setdefault((country, valid or state), valid is not None)
    out += ["  " + "state".ljust(34) + "specimens".rjust(10) + "collecting events".rjust(19) + "  in AntWeb"]
    for (country, state), known in targets.items():
        rows = [row for row, p in lists.proposals if p.field == "adm1" and p.country == country and p.proposed == state]
        out.append("  " + (country + ": " + state).ljust(34) + n(len(rows)).rjust(10) + n(len({row.event or "\0" + row.code for row in rows})).rjust(19)
                   + ("  yes" if known else "  NOT YET: proposed state not yet in AntWeb, add it in the Geolocale Manager first"))
    return out


def report_text(when, commit, group_text, lists, shapes, detail, seconds, tolerance, map_path=MAP_FILE, suggested=None, madagascar=None, evidence=None,
                sources=None, costs=(), taxonworks=None, available=None):
    """The report: a summary of at most SUMMARY_LINES lines (totals, reasons, what the test with specimens gave,
    what a person has to decide), then the long tables. suggested: (lines of mapping_suggestions.csv, shapes left
    out of it); madagascar: the lines of region_lines, when that table was made; evidence: the Evidence read;
    sources: [(label, StateShapes)] to compare (the first is shapes); costs: lines on time and memory;
    taxonworks: the lines of tw_report, or None when no archive was given; available: {name: source} of the
    sources that were made, for the record of the files used."""
    n = cc.n
    events = lists.events()
    out = ["AntWeb place corrections, " + when + " (tool version " + VERSION + ", AntWeb commit " + (commit or "unknown") + ")",
           "Upload group: " + group_text,
           "Summary: totals, reasons, the test with specimens, what a person has to decide. The tables follow under \"Details\".", "",
           "Records looked at (no state, no country, a country AntWeb does not know, a province to convert): " + n(sum(lists.looked.values()))]
    out += top(lists.looked, 5)
    values = {}
    # (field, label, most reasons shown, converting: the records whose stored state is a province to convert)
    blocks = (("country", "Country", 5, None), ("adm1", "State (adm1)", 9, False), ("adm1", "State (adm1), from a province to its region", 4, True))
    for field, label, limit, converting in blocks:
        proposals = [(row, p) for row, p in lists.proposals if p.field == field and (converting is None or bool(p.current) == converting)]
        nots = collections.Counter()
        for row, x in lists.nots:
            if x.field == field and (converting is None or bool(row.adm1) == converting):
                nots[x.reason] += 1
        lines = [e for e in events if e[1] == field and (converting is None or bool(e[2]) == converting)]
        if converting and not proposals and not nots:
            continue
        different = sum(1 for e in lines if e[10].startswith("DIFFERENT"))
        out += ["", label + ": " + n(len(proposals)) + " specimens with a proposal (corrections.csv), in " + n(len(lines)) + " lines of by_collecting_event.csv"
                + (", " + n(different) + " of them flagged for different proposals" if different else "")]
        if field == "adm1":
            flagged = sum(1 for _, p in proposals if p.flag)
            out += ["  " + n(flagged) + " of them (" + n(sum(1 for e in lines if e[13])) + " lines) lie within the stated error, or the allowed " + format(tolerance, "g")
                    + " km, of another state: column other_state_flag, look at those first."]
            water = sum(1 for _, p in proposals if p.basis.startswith(IN_THE_WATER))
            left = sum(1 for _, p in proposals if LEFT_OUT in p.note)
            if water or left:
                out += ["  " + n(water) + " of them lie outside the state's shape, within the allowed distance of it alone (column note: outside the shape, N km from it (coast)); "
                        + n(left) + " at a place the country's shape leaves out (column note: an islet, or coordinates to check)."]
        if converting:
            other = collections.Counter(p.proposed for _, p in proposals if p.note.startswith(OTHER_PROVINCE))
            out += ["  " + n(sum(other.values())) + " of them: " + OTHER_PROVINCE + " (column note)"
                    + (". By region: " + "; ".join(k + " " + n(c) for k, c in sorted(other.items(), key=lambda kv: (-kv[1], kv[0]))[:8]) if other else "") + "."]
        out += ["  No proposal (not_derivable.csv): " + n(sum(nots.values())) + " specimens, by reason:"] + top(nots, limit)
        if not converting:
            values[field] = collections.Counter((p.country + ": " + p.proposed) if field == "adm1" else p.proposed for _, p in proposals)
        else:
            values["convert"] = collections.Counter(p.country + ": " + p.current + " -> " + p.proposed for _, p in proposals)
    tested = tested_lines(shapes)
    kinds = collections.Counter("contradicted" if x[4].startswith("name match NOT USED") else "stands" if x[4].startswith("by name; the name stands") else
                                x[4].split(",")[0].split(";")[0].split(":")[0] for x in tested)
    decide = decide_lines(shapes)
    used = evidence.totals["used for the test"] if evidence is not None else 0
    out += ["", "The shapes and AntWeb's states (mapping_tested.csv), tested with AntWeb's own specimens that have a state and coordinates (" + n(used) + " of every group):",
            "  " + n(kinds["confirmed by specimens"]) + " shapes confirmed by specimens, " + n(kinds["by name only"]) + " by name only, "
            + n(kinds["by name"]) + " by name with the specimens agreeing in part, " + n(kinds["stands"]) + " by a name that stands against specimens, "
            + n(kinds["mapping file"]) + " by the mapping file, "
            + n(kinds["island country of AntWeb"]) + " island countries; " + n(kinds["contradicted"]) + " name matches not used.",
            "For a person to decide (to_decide.csv): " + n(sum(1 for x in decide if x[2].startswith("shape holds"))) + " shapes that hold several AntWeb states, "
            + n(sum(1 for x in decide if x[2].startswith("AntWeb"))) + " AntWeb states that lie in several shapes, "
            + n(sum(1 for x in decide if x[2] == DIFFERS)) + " names that stand against specimens (their stored state differs from where they lie)."]
    adding = collections.Counter({c + ": " + x: 0 for c, t in shapes.tables.items() for x in t["to_add"]})
    for row, p in lists.proposals:
        if p.field == "adm1" and TO_ADD in p.note:
            adding[p.country + ": " + p.proposed] += 1
    if adding:
        out += ["Proposed states not yet in AntWeb, to add in the Geolocale Manager first (specimens in brackets): "
                + "; ".join(k + " (" + n(c) + ")" for k, c in sorted(adding.items())[:6]) + ("; and more, see Details" if len(adding) > 6 else "")]
    measured = consistency_lines([("", shapes)] + list(sources or []))
    if measured[1]:
        out += ["Consistency of the sources (do the state level and the next level down fit?): " + n(measured[1]) + " countries measured, " + n(measured[2])
                + " with units that lie in more than one state shape (under Details)."]
    if taxonworks is None:
        out += ["For TaxonWorks: no archive was given (--tw-archive), so tw_load.tsv was not written."]
    else:
        out += ["For TaxonWorks: tw_load.tsv, tw_bioregion_differs.tsv and tw_unresolved.csv were written; the table of predicates and actions is under Details."]
    comparison = compared(sources) if sources else None
    if comparison is not None:
        out += ["Sources compared, the specimens that lie in the unit of their state: " + "; ".join(
            label + " " + (format(100.0 * t[SHARES[0]] / sum(t.values()), ".1f") if sum(t.values()) else "0") + "% of " + n(sum(t.values()))
            for (label, _), t in zip(sources, comparison[1])) + " (table under Details)."]
    looked = {x.country for _, x in lists.nots} | {p.country for _, p in lists.proposals}
    below = collections.Counter({c + " " + t["level"]: 0 for c, t in shapes.tables.items() if not t["usable"] and (sources is None or len(sources) < 2 or c in looked)})
    for row, x in lists.nots:
        if x.reason == NO_MATCH:
            below[x.country + " " + shapes.tables[x.country]["level"]] += 1
    out += ["", NO_MATCH + " (" + n(len(below)) + (" country, " if len(below) == 1 else " countries, ") + n(sum(below.values()))
            + " specimens; see mapping_suggestions.csv):"]
    names = [key + " (" + n(count) + ")" for key, count in sorted(below.items(), key=lambda kv: (-kv[1], kv[0]))]
    listed = cc.wrap(names)
    room = SUMMARY_LINES - len(out) - 2
    if len(listed) > room:
        listed = listed[:room - 1] + ["  ... and more: every one of them is marked NONE USED in the list of levels below"]
    out += listed + ["", "Report only: nothing was changed in the database."]
    assert len(out) <= SUMMARY_LINES

    out += ["", "Details", ""]
    for field, label in (("country", "Country"), ("adm1", "State (adm1)"), ("convert", "State (adm1), from a province to its region")):
        if field in values:
            out += [label + ", proposed values (the 40 largest):"] + cc.lines_of(values[field], str, limit=40) + [""]
    out += ["The test with specimens. A shape is a state \"confirmed by specimens\" when at least " + str(MIN_POINTS) + " specimens of the state lie in the shape, they are",
            "at least " + format(100 * MIN_OF_STATE, ".0f") + "% of the state's specimens that lie in any shape of the level, and at least " + format(100 * MIN_OF_SHAPE, ".0f")
            + "% of the shape's specimens that have a state are filed under it.",
            "This decides only where the name does not. A shape whose name is exactly the name of one valid AntWeb state, the only shape of that name, is that state",
            "whatever the specimens say; those that say otherwise are in to_decide.csv (\"" + DIFFERS + "\"). A looser name (without Province,",
            "another name of the unit, a name line of the mapping file) is not used when the state has " + str(MIN_AGAINST) + " or more located specimens and fewer than "
            + format(100 * CONTRADICTED_BELOW, ".0f") + "% of them lie in the shape.",
            "Of several shapes with the name of one state, one that holds fewer than " + str(MIN_POINTS) + " of the state's specimens, and fewer than half, is not used. "
            "mapping_tested.csv has the numbers of each pair.",
            "A specimen counts when it lies inside exactly one shape of the level of its stored country's file; the units being converted (provinces) are left out."]
    if evidence is not None:
        out += cc.lines_of(evidence.totals, str)
    out += ["", "For a person to decide (to_decide.csv; the 60 with the most specimens):"]
    out += ["  " + n(x[5]).rjust(9) + "  " + x[0] + " " + x[1] + ": " + ("" if x[2] == DIFFERS else
                                                                    "the shape " + x[3] + " holds " if x[2].startswith("shape") else "AntWeb's " + x[3] + " lies in ") + x[4]
            for x in sorted(decide, key=lambda x: (-x[5], x[0], x[3]))[:60]] or ["  none"]
    if adding:
        out += ["", "Proposed states not yet in AntWeb (a line of the mapping file names them): add them in the Geolocale Manager first. With the specimens they are proposed for:"]
        out += cc.lines_of(adding, str)
        out += ["For each of them, what the Geolocale Manager asks for, measured on the shape of the source (to type as they stand):"]
        for country in sorted(shapes.tables):
            for state, box, where, unit in shapes.tables[country].get("new_states", ()):
                out += ["  " + country + ": " + state + " (" + n(adding[country + ": " + state]) + " specimens)"]
                if box is None:
                    out += ["    not measured: " + where]
                    continue
                f = lambda x: format(x, ".4f")
                out += ["    Centroid (lat, lon): " + f(box.lat) + ", " + f(box.lon) + "      " + box.centre,
                        "    Bounding box (west lon, south lat, east lon, north lat): " + ", ".join(f(x) for x in (box.west, box.south, box.east, box.north))
                        + "      latitude " + f(box.south) + " to " + f(box.north) + ", longitude " + f(box.west) + " to " + f(box.east)
                        + (" (across the 180 meridian)" if box.west > box.east else ""),
                        "    from " + where + (", unit code " + unit if unit else "") + "; " + n(box.parts) + (" ring" if box.parts == 1 else " rings")]
    out += [""]
    names = collections.Counter()
    for row, x in lists.nots:
        if x.reason == NOT_A_STATE:
            names[(x.country, x.detail)] += 1
    out += ["For the Geolocale Manager: names of the boundary files that are no state in AntWeb, with the specimens that lie in them:"]
    out += cc.lines_of(names, lambda k: k[0] + ": " + k[1], limit=200)
    out += ["", "The level of the boundary files, for each country whose records were looked up in them (" + n(len(shapes.tables)) + " countries). At each level:",
            "AntWeb's valid states of the country that a shape names / all of them, and the shapes that are an AntWeb state / all shapes of the level.",
            "Where specimens confirmed a state: the level with the most AntWeb states confirmed by specimens or matched by name (the coarser one of two alike).",
            "Where there are none to test with: the level with the largest share of shapes that are AntWeb states (the coarser one of two alike), when at least half of",
            "its shapes are AntWeb states or, for the coarsest level only, at least half of AntWeb's states are matched. Below that: no proposal (NONE USED).",
            "A country whose records all lack coordinates, or lie outside it, is not in this list."]
    out += [level_line(country, shapes.tables[country]) for country in sorted(shapes.tables)]
    out += ["", "How the names fit at the level named above, country by country:"]
    for country in sorted(shapes.tables):
        t = shapes.tables[country]
        if "matched" not in t:
            continue
        out += ["  " + country + ": " + t["where"] + ("" if t["usable"] else " (NOT USED: below the minimum)"),
                "    AntWeb states matched (" + n(len(t["matched"])) + "): " + "; ".join(t["matched"]),
                "    AntWeb states not matched (" + n(len(t["not_matched"])) + "): " + "; ".join(t["not_matched"]),
                "    shapes that are an island country of AntWeb, entered as the state (" + n(len(t["islands"])) + "): " + "; ".join(t["islands"]),
                "    shapes that are no state in AntWeb (" + n(len(t["shapes_not_states"])) + "): " + "; ".join(t["shapes_not_states"][:300])
                + (" ... and " + n(len(t["shapes_not_states"]) - 300) + " more" if len(t["shapes_not_states"]) > 300 else "")]
        if t["other_way"]:
            out += ["    matched in another way than by the name itself: " + "; ".join(t["other_way"])]
    if shapes.problems:
        out += ["", "Countries with no state shapes (" + n(len(shapes.problems)) + "):"] + ["  " + c + ": " + why for c, why in sorted(shapes.problems.items())]
    used = sorted(shapes.rows_used)
    out += ["", "Mapping file " + map_path + (": " if os.path.exists(map_path) else ": NOT FOUND (fetch it next to the tool): ") + n(len(shapes.map_rows))
            + " rows that give a shape its state whatever the names and the specimens say (not counted here: " + n(len(shapes.rules.names)) + " name lines, "
            + n(len(shapes.rules.splits)) + " split lines, " + n(len(shapes.rules.sources)) + " source lines; they follow). " + n(len(used)) + " shapes got their state by such a row in this run:"]
    out += ["  " + c + " " + level + ": " + shape + " -> " + state for c, level, shape, state in used] or ["  none"]
    named = [c + " " + shapes.tables[c]["level"] + ": " + x for c in sorted(shapes.tables) for x in shapes.tables[c].get("named", ())]
    out += ["  Names of the mapping file (" + n(len(shapes.rules.names)) + " \"name\" lines, tested with specimens like any name) that are in use: " + n(len(named))] + cc.wrap(named, 150)
    out += split_lines(lists, shapes)
    if taxonworks is not None:
        out += taxonworks
    if suggested is not None:
        lines, left_out = suggested
        out += ["", "mapping_suggestions.csv: what the test could not settle. " + n(len(lines)) + " lines for " + n(len({(x[0], x[2]) for x in lines}))
                + " shapes that got no state, each with the AntWeb states of its",
                "country whose name is most like it (score 1: the same once words such as Province or Region are left out), the records of this run that lie in",
                "the shape, and what AntWeb's specimens in it are filed under. Nothing is used from it by the tool. " + n(left_out) + " more shapes have no records",
                "and no AntWeb state like them, and are not listed."]
    out += madagascar or []
    out += measured[0]
    if comparison is not None:
        out += comparison[0]
    if evidence is not None and getattr(evidence, "cost", ""):
        out += ["", evidence.cost]
    out += list(costs)
    by_licence = collections.defaultdict(list)
    for key, entry in shapes.files.items():
        by_licence[entry.get("licence", "not read")].append(key)
    used = {t.get("label", "geoBoundaries") for t in shapes.tables.values()} | {shapes.source.label} | {label for label, _ in sources or []}
    out += ["", "State shapes:"]
    if "geoBoundaries" in used:
        out += ["  geoBoundaries gbOpen, the simplified files, commit " + detail.commit + ", in " + detail.folder + ". Checksums: " + detail.lock_path]
    if NaturalEarthStates.label in used:
        out += ["  " + NaturalEarthStates.label + ", " + NE_STATES_NAME + " at commit " + cc.SHAPES_COMMIT + " of nvkelso/natural-earth-vector, sha256 " + NE_STATES_SHA256]
    ocha = (available or {}).get("ocha") or next((x for x in [shapes.source] + list(shapes.by_country.values()) if isinstance(x, OchaSource)), None)
    if ocha is not None and ocha.record:
        out += ["  OCHA COD-AB from HDX (data.humdata.org), in " + ocha.folder + ". HDX has no pinned release: this is the record of the files used."] + ocha.report()
    own = sorted(c + ": " + shapes.by_country[c].label for c in shapes.by_country)
    if own:
        out += ["  Countries the mapping file takes from a source of their own: " + "; ".join(own)]
    out += ["  Licences:"] + ["    " + lic + ": " + "; ".join(sorted(keys)) for lic, keys in sorted(by_licence.items())]
    countries_used = sorted(iso for iso, f in detail.used.items() if f.get("bytes") and not f.get("problem"))
    out += ["Country shapes: Natural Earth 1:10m" + ("; near a border the full geoBoundaries file of the country: " + " ".join(countries_used) if countries_used else " alone")
            + ". The allowed distance: " + format(tolerance, "g") + " km, or the specimen's stated error when larger."]
    out += ["", "Run time: " + seconds, "", "Report only: nothing was changed in the database."]
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------- the database (read only)

def places_sql(columns):
    """The state rows and the country rows of geolocale, with their bioregions when the table has those columns:
    name, parent, is_valid, valid_name, georank, bioregion, alt_bioregion."""
    bioregion = "hex(ifnull(bioregion, ''))" if "bioregion" in columns else "''"
    alt = "hex(ifnull(alt_bioregion, ''))" if "alt_bioregion" in columns else "''"
    return ("select hex(name), hex(ifnull(parent, '')), is_valid, hex(ifnull(valid_name, '')), georank, " + bioregion + ", " + alt
            + " from geolocale where georank in ('adm1', 'country') and name is not null")


def group_sql(group_id):
    return "" if group_id is None else " and s.access_group = " + str(int(group_id))


def stored_countries_sql(group_id=None):
    return "select hex(ifnull(s.country, '')), count(*) from specimen s where 1 = 1" + group_sql(group_id) + " group by 1"


def madagascar_sql(columns, names, group_id=None):
    """Every record of Madagascar (names: the stored spellings of the country), counted by collecting event,
    locality name, state, island country, coordinates and stated error, with the first specimen code."""
    text = lambda name: "hex(ifnull(s." + name + ", ''))" if name in columns else "''"
    return ("select " + ", ".join([text("collectioncode"), text("localityname"), "hex(ifnull(s.adm1, ''))", text("island_country"),
                                   "ifnull(round(s.decimal_latitude, 5), '')", "ifnull(round(s.decimal_longitude, 5), '')", text("latlonmaxerror"),
                                   "count(*)", "hex(min(s.code))" if "code" in columns else "''"])
            + " from specimen s where hex(s.country) in (" + ", ".join(cc.sql_string(name.encode("utf-8").hex().upper()) for name in names) + ")"
            + group_sql(group_id) + " group by 1, 2, 3, 4, 5, 6, 7")


def provinces_in_antweb(states):
    """True when AntWeb's states of Madagascar are the six provinces (at least four of them are valid states)."""
    return len(PROVINCES & {plain(name) for name in states.of(MADAGASCAR)}) >= 4


def madagascar_regions(work, code, rows, group_text, lists=None):
    """(The lines of the report for Madagascar by region, the lines of stored_differs.csv), from the shapes the
    proposals are made with. lists: the Lists of the run, for the count of proposals the table is checked against."""
    found = by_region(work, code, rows)
    if found is None:
        return ["", "Madagascar by region: no shapes of " + code + " (" + work.shapes.problems.get(MADAGASCAR, "") + ")."], []
    table, totals, reasons, differs = found
    within = work.shapes.within.get(MADAGASCAR, {})
    proposals = sum(1 for row, p in lists.proposals if p.field == "adm1" and p.current and p.country == MADAGASCAR) if lists is not None else 0
    where = work.shapes.tables[MADAGASCAR]["where"]
    return region_lines(table, totals, reasons, lambda region: within.get(plain(region)), where, group_text, proposals, differs), differs


def evidence_sql(columns):
    """AntWeb's specimens of every upload group that have a country, a state and coordinates and are no island-
    country specimens, counted by country, state and place: what the shapes are tested with."""
    island = " and (s.island_country is null or s.island_country = '')" if "island_country" in columns else ""
    return ("select hex(s.country), hex(s.adm1), round(s.decimal_latitude, 5), round(s.decimal_longitude, 5), count(*) from specimen s"
            " where s.country is not null and s.country <> '' and s.adm1 is not null and s.adm1 <> ''"
            " and s.decimal_latitude is not null and s.decimal_longitude is not null" + island + " group by 1, 2, 3, 4")


def rows_sql(columns, group_id=None, unknown=(), convert=()):
    """The records with a gap: no state, no country, or a country AntWeb does not know (unknown: those names); and
    the records whose state is a coarser unit that is converted (convert: [(the stored spellings of the country,
    the names of its coarser units)], Madagascar and its provinces)."""
    text = lambda name: "hex(ifnull(s." + name + ", ''))" if name in columns else "''"
    number = lambda name: "ifnull(round(s." + name + ", 5), '')"
    hexes = lambda names: ", ".join(cc.sql_string(u.encode("utf-8").hex().upper()) for u in names)
    gap = "s.adm1 is null or s.adm1 = '' or s.country is null or s.country = ''"
    if unknown:
        gap += " or hex(s.country) in (" + hexes(unknown) + ")"
    for spellings, units in convert:
        if spellings and units:
            gap += " or (hex(s.country) in (" + hexes(spellings) + ") and s.adm1 in (" + ", ".join(cc.sql_string(u) for u in units) + "))"
    return ("select " + ", ".join([text("code"), text("collectioncode"), text("localitycode"), text("country"), text("adm1"), text("adm2"),
                                   text("island_country"), number("decimal_latitude"), number("decimal_longitude"), text("latlonmaxerror"),
                                   text("localityname"), text("localitynotes"), text("bioregion"), "ifnull(s.access_group, 0)"])
            + " from specimen s where 1 = 1" + group_sql(group_id) + " and (" + gap + ") order by s.country, s.code")


def read_row(r):
    u = cc.unhex
    number = lambda text: str(float(text)) if text not in ("", "NULL") else ""       # 54.01950 and 54.0195 are one place
    i = sys.intern                                               # the records of a collecting event share most of their text: kept once
    return Row(u(r[0]), i(u(r[1])), i(u(r[2])), i(u(r[3]).strip()), i(u(r[4]).strip()), i(u(r[5])), i(u(r[6]).strip()), i(number(r[7])), i(number(r[8])),
               i(u(r[9])), i(u(r[10])), i(u(r[11])), i(u(r[12])), i(r[13]))


def find_group(groups, name):
    """The id of the upload group with this name (capitals ignored); stops when there is none or more than one."""
    exact = [gid for gid, text in groups.items() if text.casefold() == name.casefold()]
    if len(exact) == 1:
        return exact[0]
    close = sorted(text for text in groups.values() if name.casefold() in text.casefold())
    sys.exit("--group: no upload group is named " + repr(name) + (". Names with that text: " + "; ".join(close) if close else ""))


def main(argv=None):
    a = argparse.ArgumentParser(description="The country and state a specimen is missing, proposed from its coordinates. Report only.")
    a.add_argument("--group", default=None, help="the name of one upload group (default: every group)")
    a.add_argument("--group-id", type=int, default=None, help="the id of one upload group (1: California Academy of Sciences)")
    a.add_argument("--tolerance-km", type=float, default=cc.TOLERANCE_KM, help="how far outside a shape a point may lie (default 1; a larger stated error counts instead)")
    a.add_argument("--shapes-source", choices=["naturalearth", "geoboundaries"], default="geoboundaries",
                   help="the country test: geoboundaries (default) asks the country's detailed file near a border; naturalearth does not")
    a.add_argument("--shapes", default=None, help="a local GeoJSON file in place of the Natural Earth file")
    a.add_argument("--shapes-folder", default=cc.SHAPES_FOLDER, help="where shape files and the lock file are kept (default " + cc.SHAPES_FOLDER + ")")
    a.add_argument("--map", default=MAP_FILE, help="the mapping file (default adm1_shape_map.csv next to this tool)")
    a.add_argument("--states-source", choices=["geoboundaries", "naturalearth", "ocha", "both", "all"], default="geoboundaries",
                   help="the state shapes: geoboundaries (default), naturalearth (Natural Earth's states and provinces), ocha (UN OCHA's COD-AB files from HDX); "
                        "both and all: the corrections by geoboundaries, and the two (geoboundaries, naturalearth) or the three sources compared on AntWeb's "
                        "specimens for every country. A \"source\" line of the mapping file names another source for one country (Madagascar: ocha)")
    a.add_argument("--ocha-countries", default=None, help="the ISO codes of the countries to take from OCHA, with commas (MDG,KEN); default: every country HDX has")
    a.add_argument("--ignore-source-lines", action="store_true", help="do not follow the \"source\" lines of the mapping file: every country from --states-source")
    a.add_argument("--consistency", action="store_true", help="measure for every country looked at how its source's state level and next level fit "
                                                              "(default: the countries the mapping file has split, convert or source lines for)")
    a.add_argument("--tw-archive", default=None, help="the archive TaxonWorks writes for AntWeb (taxonworksNightly.zip, or its data.tsv), copied out of the "
                                                      "container: with it tw_load.tsv, tw_bioregion_differs.tsv and tw_unresolved.csv are written (needs a group)")
    a.add_argument("--tw-summary", action="store_true", help="with --tw-archive: print what the archive alone says (codes, blank predicates) and stop; no database")
    a.add_argument("--tw-include-flagged", action="store_true", help="put proposals within the tolerance of another state into tw_load.tsv too")
    a.add_argument("--no-specimen-test", action="store_true", help="do not read AntWeb's specimens to test the shapes with: by the names and the mapping file alone")
    a.add_argument("--project", default=None, help="AntWeb project folder (default /root/antweb or /root/antweb-next)")
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--out", default=None, help="output folder (default /root/antweb-place-fix/<date>-<time>-corrections)")
    args = a.parse_args(argv)
    if args.tw_summary:
        if not args.tw_archive:
            sys.exit("--tw-summary needs --tw-archive.")
        print("\n".join(TwArchive(args.tw_archive).summary()))
        return 0
    if args.tw_archive and args.group is None and args.group_id is None:
        sys.exit("--tw-archive needs --group or --group-id: the archive is that of one upload group. Nothing was done.")
    if args.tw_archive and not os.path.exists(args.tw_archive):
        sys.exit("--tw-archive: " + args.tw_archive + " is not there. Nothing was done.")
    project = args.project or cc.default_project()
    now = datetime.datetime.now()
    started = time.time()
    folder = args.out or os.path.join("/root/antweb-place-fix", now.strftime("%Y-%m-%d-%H%M") + "-corrections")
    commit = cc.read_first_line(os.path.join(project, "etc", "git-commit"))
    sql = lambda statement: cc.run_sql(project, statement, args.container)

    place_columns = {r[0] for r in sql(cc.columns_sql("geolocale"))}
    countries = cc.read_countries(sql(cc.countries_sql(place_columns)))
    places = [(cc.unhex(r[0]), cc.unhex(r[1]), r[2], cc.unhex(r[3]), r[4], cc.unhex(r[5]), cc.unhex(r[6])) for r in sql(places_sql(place_columns))]
    states = States([x[:4] for x in places if x[4] == "adm1"], countries)
    bioregions = Bioregions([(x[0], x[4], x[1], x[2], x[5], x[6]) for x in places], countries, states)
    groups = {r[0]: cc.unhex(r[1]) for r in sql(cc.GROUPS_SQL)}
    group_id = args.group_id
    if args.group is not None:
        group_id = int(find_group(groups, args.group))
    group_text = "every group" if group_id is None else groups.get(str(group_id), "?") + " (id " + str(group_id) + ")"

    path = args.shapes or cc.fetch_shapes(args.shapes_folder)
    with open(path, encoding="utf-8") as f:
        shapes = cc.read_shapes(json.load(f))
    cc.map_shapes(shapes, countries)
    index = cc.Index(shapes, forget=True)
    store = cc.Detail(args.shapes_folder)
    checker = cc.Checker(countries, shapes, index, args.tolerance_km, store if args.shapes_source == "geoboundaries" else None)
    columns = {r[0] for r in sql(cc.columns_sql("specimen"))}
    evidence = None
    if not args.no_specimen_test:                                # one more read: what the shapes are tested with
        evidence, began, lines = Evidence(countries, states), time.time(), 0
        for r in cc.rows_sql(project, evidence_sql(columns), args.container):
            evidence.add(cc.unhex(r[0]), cc.unhex(r[1]), r[2], r[3], int(r[4]))
            lines += 1
        evidence.cost = ("The read of AntWeb's specimens with a state and coordinates: " + cc.n(lines) + " lines (country, state, place) in "
                         + format(time.time() - began, ".0f") + " seconds; memory after it " + cc.n(cc.peak_mb()) + " MB.")
    rules, rows_of_map = read_rules(args.map), read_map(args.map)
    lined = set() if args.ignore_source_lines else set(rules.sources.values())
    comparing = {"both": ["geoboundaries", "naturalearth"], "all": list(SOURCE_NAMES)}.get(args.states_source, [])
    needed = lined | set(comparing) | {args.states_source}
    available = {"geoboundaries": GeoSource(store),
                 "naturalearth": NaturalEarthStates(fetch_states(args.shapes_folder)) if "naturalearth" in needed else None,
                 "ocha": OchaSource(args.shapes_folder, only=args.ocha_countries.split(",") if args.ocha_countries else None) if "ocha" in needed else None}
    named = lambda country: getattr(countries.resolve(country), "name", country)
    check = True if args.consistency else ({named(c) for c in rules.sources} | {named(c) for c in rules.convert} | {named(c) for c, *_ in rules.splits})
    state_shapes = StateShapes(store, states, countries, rows_of_map, evidence, rules, available.get(args.states_source) or available["geoboundaries"],
                               None if args.ignore_source_lines else available, check)
    for country in sorted(state_shapes.by_country):              # a country the mapping file takes from another source: its file is fetched now, before the slow work
        if isinstance(state_shapes.by_country[country], OchaSource):
            state_shapes.by_country[country].file(checker.code_of_country.get(country, ""))
    work = Corrections(checker, states, state_shapes, bioregions)
    sources = [(available[name].label, StateShapes(store, states, countries, rows_of_map, evidence, rules, available[name], None, True if args.consistency else ()))
               for name in comparing]

    stored = [cc.unhex(r[0]) for r in sql(stored_countries_sql(group_id))]
    unknown = [name for name in stored if name.strip() and countries.resolve(name.strip()) is None]
    spelt = lambda country: [name for name in stored if name.strip() and getattr(countries.resolve(name.strip()), "name", None) == country]
    convert = [(spelt(country), sorted({name for unit in units for name in [unit] + states.old[country].get(unit, [])}))
               for country, units in sorted(state_shapes.convert.items())]
    lists = Lists(groups)
    for r in cc.rows_sql(project, rows_sql(columns, group_id, unknown, convert), args.container):
        row = read_row(r)
        lists.add(row, work.work(row), work.kind(row))

    costs = []
    if sources and evidence is not None:                         # every country that has specimens to test with, in each source
        for label, shapes in sources:
            began = time.time()
            for country in sorted(evidence.points):
                shapes.choose(country, checker.code_of_country.get(country, ""))
            costs.append("Sources compared, " + label + ": " + cc.n(len(shapes.tables)) + " countries looked up in " + format(time.time() - began, ".0f")
                         + " seconds more; memory at the peak after it " + cc.n(cc.peak_mb()) + " MB.")
    os.makedirs(folder, exist_ok=True)
    write_csv(os.path.join(folder, "corrections.csv"), CORRECTIONS_HEADER, lists.corrections())
    write_csv(os.path.join(folder, "by_collecting_event.csv"), EVENTS_HEADER, lists.events())
    write_csv(os.path.join(folder, "not_derivable.csv"), NOT_HEADER, lists.not_derivable())
    suggested = suggestions(state_shapes, states, lists.in_shapes())
    write_csv(os.path.join(folder, "mapping_tested.csv"), TESTED_HEADER, tested_lines(state_shapes))
    write_csv(os.path.join(folder, "mapping_suggestions.csv"), SUGGESTIONS_HEADER, suggested[0])
    write_csv(os.path.join(folder, "to_decide.csv"), DECIDE_HEADER, decide_lines(state_shapes))
    for label, shapes in sources:                                # the sources compared: each one's own list of tested pairs
        write_csv(os.path.join(folder, "mapping_tested_" + re.sub(r"[^a-z0-9]+", "_", label.lower()).strip("_") + ".csv"), TESTED_HEADER, tested_lines(shapes))
    madagascar, differs = None, []
    names = spelt(MADAGASCAR)
    if names and provinces_in_antweb(states) and checker.code_of_country.get(MADAGASCAR):      # one more read: every record of Madagascar
        u = cc.unhex
        number = lambda text: str(float(text)) if text not in ("", "NULL") else ""
        rows = [(u(r[0]), u(r[1]), u(r[2]).strip(), u(r[3]).strip(), number(r[4]), number(r[5]), u(r[6]), int(r[7]), u(r[8]))
                for r in sql(madagascar_sql(columns, names, group_id))]
        madagascar, differs = madagascar_regions(work, checker.code_of_country[MADAGASCAR], rows, group_text, lists)
    write_csv(os.path.join(folder, "stored_differs.csv"), DIFFERS_HEADER, differs)
    taxonworks = None
    if args.tw_archive:                                          # one more read: the group's specimens by collecting event, for the load file of TaxonWorks
        archive = TwArchive(args.tw_archive)
        stored = collections.defaultdict(list)
        for r in cc.rows_sql(project, tw_events_sql(columns, group_id), args.container):
            u = cc.unhex
            stored[u(r[0])].append((u(r[1]).strip(), u(r[2]).strip(), u(r[3]).strip(), u(r[4]).strip(), int(r[5]), u(r[6]), "" if r[7] in ("", "NULL") else str(float(r[7])),
                                    "" if r[8] in ("", "NULL") else str(float(r[8]))))
        load, differs, unresolved, counts = tw_load(archive, lists, stored, countries, states, bioregions.names, args.tw_include_flagged)
        write_tsv(os.path.join(folder, "tw_load.tsv"), TW_LOAD_HEADER, load)
        write_tsv(os.path.join(folder, "tw_bioregion_differs.tsv"), TW_LOAD_HEADER, differs)
        write_csv(os.path.join(folder, "tw_unresolved.csv"), TW_UNRESOLVED_HEADER, unresolved)
        taxonworks = tw_report(archive, counts, args.tw_include_flagged)
    seconds = format(time.time() - started, ".0f") + " seconds; memory at the peak " + cc.n(cc.peak_mb()) + " MB"
    text = report_text(now.strftime("%Y-%m-%d %H:%M"), commit, group_text, lists, state_shapes, store, seconds, args.tolerance_km, args.map, suggested, madagascar, evidence,
                       sources if evidence is not None and sources else None, costs, taxonworks, available)
    with open(os.path.join(folder, "report.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print(text)
    print("Files: " + folder)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Refused as refused:
        sys.exit(str(refused) + " Nothing was written.")
