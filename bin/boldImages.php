<?php

/*
 *  boldImages.php    Written: Mark Sep 8, 2011.
 *
 *  Usage:

      php boldImages.php --dirName=bold_images_110610 --lastManifest=new_100616.csv --newManifest=new_110610.csv --specimenList=sent110610.csv

 options are 
   --specimenList
   --dirName for the directory to store the image
   --lastManifest for the csv file created the last time this script was run and 
   --newManifest for the name of the new csv file (to be used as -t next time).

 this takes a CSV file of specimens and finds a typical picture of the species of that specimen
 and copies a medium sized image of that specimen to a folder.

Process
  Get file from Michele.  Some sort of Bold.xls or .xlsx.
  Open in Excel.  Delete columns.  Save as Windows CSV (on Mac to have expected eol characters).  
  Refer to past csv's to know which.  Here is a sample record with header from Sent_as_of_12...

Specimen Code,Family,Subfamily,Genus,Species,subspecies,
AP011-D01,Formicidae,Myrmicinae,Strumigenys,lexex,,

  Run boldImages.pl script.
  Zip up new dir of images.
  Send to Brian.

 Perform monthly.

 *
 */ 

$args = parseArgs($_SERVER['argv']);
parseArgs($argv);

$specimenList = $args['specimenList'];
$dirName = $args['dirName'];
$lastManifest = $args['lastManifest'];
$newManifest = $args['newManifest'];

echo("dirName:".$dirName." lastManifest:".$lastManifest." newManifest:".$newManifest." specimenList:".$specimenList."\n");

//if ($dataDir == "") {
//  $dataDir = '/data/antweb';
//}


prepare($dirName, $lastManifest, $newManifest, $specimenList);

process($dirName, $lastManifest, $newManifest, $specimenList);

exit;

// --------------------------------------------------------

function process($dirName, $lastManifest, $newManifest, $specimenList) {

  // read through specimen list



  $handle = @fopen($specimenList, "r");
  if ($handle) {
    $i = 0;
    while (($line = fgets($handle, 4096)) !== false) {
      // echo "line:".$line;

      ++$i;
      processLine($line);

      if ($i > 10) {
        break 1;
        exit;
      }
    }
    if (!feof($handle)) {
      echo "Error: unexpected fgets() fail\n";
      exit;
    }
    fclose($handle);
  }
}

  function processLine($line) {
    $items = explode(",", $line);
    $i = 0;
    foreach($items as $item) {
      ++$i;
      if ($i == 4) $genus = $item;
      if ($i == 5) $species = $item;
      echo $item;
      if (strpos($item, "\n") === false) {
	echo ",";
      }
    }
    echo "Genus:".$genus." Species:".$species."\n";

    processSpecimen($genus, $species);
  }

function processSpecimen($genus, $species) {

  // if specimen does not exist in the $lastManifest, process it.

  // For each specimen.  Query for best image.

  $taxonName = "myrmicinaeacanthomyrmex";
  $conn = mysql_connect("localhost","antweb","f0rm1c6");
  @mysql_select_db("ant") or logError("could not connect to ant db");

  $favQuery = "select specimen from favorite_images where taxon_name = '".$taxonName."'";
  $queryResult = mysql_query($favQuery);
  $specimenCode = mysql_result($queryResult, 0, "specimen");
  echo "specimenCode:".$specimenCode."\n";

  $genus = "Acanthomyrmex";
  $species = "concavus";

  $bestQuery = "select distinct specimen.code from specimen, image where "
    ." specimen.genus = '".$genus."'"
    ." and specimen.species = '".$species."'"
    ." and specimen.code = image.image_of_id "
    ." and image.shot_type = 'p' and image.shot_number=1";

  echo "bestQuery:".$bestQuery."\n";
  $queryResult2 = mysql_query($bestQuery);
  $specimenCode = mysql_result($queryResult2, 0, "code");
  echo "specimenCode2:". $specimenCode."\n";

  mysql_close($conn);

  // Copy the image to a directory.

  // Write to the $newManifest
}

function prepare($dirName, $lastManifest, $newManifest, $specimenList) {
  if (file_exists($dirName)) {
    echo "Directory $dirName already exists.\n";
    //exit;
  } else {
    mkdir($dirName);
    echo "Directory created:".$dirName,"\n";
  }

  if (!file_exists($lastManifest)) {
    echo "lastManifest does not exist.  Processing full specimen list\n";
  }
  if (file_exists($newManifest)) {
    echo "newManifest already exists.  Exiting.\n";
    exit;
  }
  if (!file_exists($specimenList)) {
    echo "specimenList must exist.  Exiting.\n";
    exit;
  }
}

function parseArgs($argv){
    array_shift($argv);
    $out = array();
    foreach ($argv as $arg){
        if (substr($arg,0,2) == '--'){
            $eqPos = strpos($arg,'=');
            if ($eqPos === false){
                $key = substr($arg,2);
                $out[$key] = isset($out[$key]) ? $out[$key] : true;
            } else {
                $key = substr($arg,2,$eqPos-2);
                $out[$key] = substr($arg,$eqPos+1);
            }
        } else if (substr($arg,0,1) == '-'){
            if (substr($arg,2,1) == '='){
                $key = substr($arg,1,1);
                $out[$key] = substr($arg,3);
            } else {
                $chars = str_split(substr($arg,1));
                foreach ($chars as $char){
                    $key = $char;
                    $out[$key] = isset($out[$key]) ? $out[$key] : true;
                }
            }
        } else {
            $out[] = $arg;
        }
    }
    return $out;
}

?>
