#!/usr/bin/perl

# usage:
#   ./boldImages.pl -d bold_images_110610 -t new_100616.csv -f new_110610.csv Sent110610.csv
#      or
#   ./boldImages.pl -d bold_images_100518 -t new_100106.csv -f new_100518.csv < Sent_to_Guelph_as_of_051810.csv
#
# options are 
#   -d for the directory to store the image
#   -t for the csv file created the last time this script was run and 
#   -f for the name of the new csv file (to be used as -t next time).
#
# this takes a CSV file of specimens and finds a typical picture of the species of that specimen
# and copies a medium sized image of that specimen to a folder.
#
#Process
#  Get file from Michele.  Some sort of Bold.xls or .xlsx.
#  Open in Excel.  Delete columns.  Save as Windows CSV (on Mac to have expected eol characters).  
#  Refer to past csv's to know which.  Here is a sample record with header from Sent_as_of_12...
#
#Specimen Code,Family,Subfamily,Genus,Species,subspecies,
#AP011-D01,Formicidae,Myrmicinae,Strumigenys,lexex,,
#
#  Run boldImages.pl script.
#  Zip up new dir of images.
#  Send to Brian.
#
#Perform monthly.

# 
# sed 's/\(.*,.*,.*,.*,\).* \(.*\)/\1\2/' < from_michele_110610.csv > from_michele_fixed_11610.csv


#---- Original Thau doc
#
#how to run this
#
# ./boldImages.pl -d bold_images_100518 -t new_100106.csv -f new_100518 < Sent_to_Guelph_as_of_051810.csv
#
# ./boldImages.pl -d bold_images_110610 -t new_100616.csv -f new_110610.csv < Sent110610.csv 


#----- Subsequent Thau doc
# how to run this
#
# ./boldImages.pl -d bold_images_100518 -t new_100106.csv -f new_100518 < Sent_to_Guelph_as_of_051810.csv
#
#The input file, Sent_to... above, needs to have the following header:
#
#SpecimenCode,Family,Subfamily,Genus,SpeciesName,Subspecies
#
#The next important thing is that the species name cannot also have the genus
#in it, so each line must look like this:
#
#BLF00492(44)-1-D01,Formicidae,Ponerinae,Pachycondyla,JCR06
#
#Notice that the last element is *just* the species name, with no preceding genus.
#
#To run the script for the June 10, 2011 file I ran:
#
#
#./boldImages.pl -d bold_images_110610 -t new_100616.csv -f new_110610.csv < from_michele_fixed_110610.csv
#
# the next time you do this, you'll want to run:
#   	./boldImages.pl -d bold_images_thedate -t new_110610.csv -f new_thedate.csv < input_file.csv


use Getopt::Std;
use File::Copy;
use DBI;
use strict;

my %options=();
getopt("tdf",\%options);

my $directory = $options{d};
my $newCSV = $options{f};
my $oldFile = $options{t};

#my $lineCount = 0;
#while (<>) {
#    $lineCount++;
#}
#if ($lineCount == 0) {
#  print "No input \n";
#  exit;
#}
#if ($lineCount == 1) {
#  print "improper EOLs in $ARGV \n";
#  exit;
#}
#print "lineCount:".$lineCount."\n";

my %specimens = {};
my %taxonMapping = {};

my $imageDir = "/data/antweb/images";

my $host = "dbi:mysql:ant:localhost:3306";
my $user = "antweb";
my $password = "f0rm1c6";

my $connect = DBI->connect($host, $user, $password);

my $favQuery = $connect->prepare("select specimen from favorite_images where taxon_name=?");

my $bestQuery = $connect->prepare("select distinct specimen.code from specimen, image where "
	. " specimen.genus =  ? and specimen.species = ? and specimen.code = image.image_of_id " 
	. " and image.shot_type = 'p' and image.shot_number=1");

# parse the old file, load up the %specimens and %taxonMapping hashes
if (length($oldFile) > 0) {
  print "Parsing:".$oldFile ."\n";
  %taxonMapping = parseOldFile($oldFile,%taxonMapping);
} else {
  print "Parsing error.  Length <= 0 in oldFile:".$oldFile ."\n";
}

my $key;
my $keyCount = 0;
foreach $key (keys %taxonMapping) {
  $keyCount++;
  # print "key : $key \n";
}
print "keyCount:".$keyCount."\n";

# parse through the new species
my $junk = <>;

print "junk:".$junk."\n";

my @lines = ();
my $bestSpecimen;
my $taxonName;
my @data;
my $line;

# print "argv:".@ARGV[0]." $_:".$_."\n";
print "enterring main loop\n";
my $count = 0;
while (<>) {
  $count++;
  # print "inside New Images loop.\n";
  chop;
  $bestSpecimen = "";
  my($specimen,$family,$subfamily,$genus,$species,$subspecies,@junk) = split(/,/,$_);

  print "s:".$specimen." f:".$family." g:".$genus." sp:".$species." ssp:".$subspecies."\n";

  next if (defined($specimens{$specimen}));

  if (length($subspecies) > 0) {
    $species = $species . " " . $subspecies;
  }
  $taxonName = makeName($subfamily, $genus, $species);

  # get the best specimen
  if (length($taxonMapping{$genus + " " + $species + " " + $subspecies}) > 0) {
    $bestSpecimen = $taxonMapping{$genus + " " + $species + " " + $subspecies};
    print "found in mapping: set best specimen of $genus $species $subspecies to " . $taxonMapping{$genus + " " + $species + " " + $subspecies} ."\n";
  } else {
    $favQuery->execute($taxonName) or die("problem getting favorite image" . $favQuery->errStr);
  
    if ($favQuery->rows > 0) {
      @data = $favQuery->fetchrow_array();
      $bestSpecimen = $data[0];
      print "favorite image of $taxonName is $bestSpecimen in database call\n";
    } else {
      $bestQuery->execute($genus,$species) or die("problem getting favorite image" . $bestQuery->errStr);
      if ($bestQuery->rows > 0) {
        @data = $bestQuery->fetchrow_array();
        $bestSpecimen = $data[0];
      }
      #print "first image of $taxonName is $bestSpecimen\n";
    }
  }

  if (length($bestSpecimen) > 0) {
    # create the path the image
    my $path = $imageDir . "/" . $bestSpecimen . "/" . $bestSpecimen . "_p_1_med.jpg";

    # copy it to the directory
    print "copying $path to $directory\n";
    if(!(copy $path => $directory)) {
      print "can't $taxonName with $path: $! \n";
      $bestSpecimen = "";
    }

    # add the image file name to the csv file
    $line = join(",", ($specimen,$family,$subfamily,$genus,$species,$subspecies));
    $line .= ",";
    if (length($bestSpecimen) > 0) {
      $line.= $bestSpecimen . "_p_1_med.jpg"; 
      print "pushing $bestSpecimen" . "_p_1_med.jpg \n";
    }
    push(@lines, $line);
  }
}

print "After copy files.  Count:" . $count . "\n";

# after done, write out the new csv file
open(OUTFILE, ">$newCSV") || die("can't write to $newCSV");
foreach $line (@lines) {

  print "  line:".$line."\n";
  print OUTFILE "$line\n";
}

print "Close outfile:".$newCSV."\n";
close(OUTFILE);

sub makeName {
  my($subfamily, $genus, $species) = @_;
  my $name = $subfamily . $genus . " " . $species;
  $name =~ tr/A-Z/a-z/;
  return $name;
}

# this updates %specimens and %taxonMapping
sub parseOldFile {
  my($oldFile, %taxonMapping) = @_;

  my $speciesName;
  open(INFILE, $oldFile) || die("can't open $oldFile");
  while (<INFILE>) {
    my($code,$family,$subfamily,$genus,$species,$subspecies,$image) = split(",");
    $specimens{$code} = 1;
    #print "adding $code to specimen\n";
    my($specimen) = $image =~ /(.*?)_p_1_med.jpg/;
  
    $speciesName = $genus . " " . $species . " " . $subspecies;
    $taxonMapping{$speciesName} = $specimen;
    #print "adding $specimen to $speciesName\n";
  }
  
  print "Closing oldfile:".$oldFile ."\n";
    
  close(INFILE);
  return %taxonMapping;
}
