<?php

/*
 *  rebootCheck.php
 * *
 *  sudo php bin/rebootCheck.php  
 *
 *  Installation Instructions:
 *    Insert into the root's crontab as follows
 *
 *    sudo bash (enter password)
 *    crontab -e
 *    [* followed by /30] * * * * php /home/mjohnson/antweb/bin/appCheck.php
 *
 *    This crontab will be run every 30 minutes, or for every hour ...
 *
 *    *[nospace here]15 * * * * php /home/mjohnson/antweb/bin/appCheck.php      // every 15 min?
 *    *[nospace here]/30 * * * * php /home/mjohnson/antweb/bin/appCheck.php   // every half hour
 *
 *  @author: Mark Johnson (UniversalGiving.org)
 *
 */ 

//logAppCheck("", "Running appCheck");

$fileName = "/data/antweb/web/reboot.txt"
if (file_exists($fileName)) {
  unlink($fileName);
  logAppCheck("", "Rebooting");
}


function logAppCheck($fileName, $logString) {
  $logFileName = '/usr/local/tomcat/logs/appCheck.log';
  if (!($fileName === "")) {
    $logFileName = $fileName;
    echo("is");
  }

  date_default_timezone_set('America/Los_Angeles');
 
  //echo ("logAppCheck fileName:".$fileName." logString:". $logString." logFileName:".$logFileName."\n");

  $logString = $logString." ".date(DATE_RFC822)."\n";
  // Let's make sure the file exists and is writable first.

  if (!$handle = fopen($logFileName, 'a')) {
     echo "Cannot open file ($logFileName)";
     exit;
  }

  if (fwrite($handle, $logString) === FALSE) {
      echo "Cannot write to file ($logFileName)";
      exit;
  }

  echo "Success, wrote ($logString) to file ($logFileName)"."\n";

  fclose($handle);
}


?>
