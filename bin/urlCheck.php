<?php

/*
 *  urlCheck.php
 * 
 *  This is a program designed to periodically check and log the results of certain urls.  It is 
 *  hoped that this will help us to diagnose and resolve the Locality Special Characters issue.
 *
 *   php bin/urlCheck.php --hosts=antweb.org,antweb.org/antweb_test,10.2.22.112,localhost/antweb --url=specimen.do?name=casent0160810
 *
 *   Other reasonable values of host:  localhost/antweb 
 *
 *      A log file /usr/local/tomcat/logs/urlCheck.log records urlCheck.php results.
 * 
 *  Installation Instructions:
 *    Insert into the root's crontab as follows
 *
 *    sudo bash (enter password)
 *    crontab -e
 *    [* followed by /30] * * * * php /home/mjohnson/antweb/bin/urlCheck.php
 *
 *    This crontab will be run every 30 minutes, or for every hour ...
 *
 *    *[nospace here]15 * * * * php /home/mjohnson/antweb/bin/appCheck.php      // every 15 min?
 *    *[nospace here]/30 * * * * php /home/mjohnson/antweb/bin/appCheck.php   // every half hour
 *
 *  @author: Mark Johnson
 *
 */ 


$args = parseArgs($_SERVER['argv']);
parseArgs($argv);

logUrlCheckDate("Test:", true);

$hosts = explode(',', $args['hosts']);
$url = $args['url'];
$url = "/".$url;

foreach($hosts as $host) testUrl($host, $url);

exit;


function testUrl($host, $url) {
  $timeout = 3;
  $old = ini_set('default_socket_timeout', $timeout);

  $jspPage = "http://".$host.$url;
  
  $str = file_get_contents($jspPage);
  if ($str === false) {
    //logUrlCheck("Complaint.  No get:".$jspPage);
    return;
  }

  $localityUrlPos = strpos($str, "locality.do");
  $endLocalityUrlPos = strpos($str, '">', $localityUrlPos);
  $localityUrlLength = $endLocalityUrlPos - $localityUrlPos;
  $localityUrl = "/".substr($str, $localityUrlPos, $localityUrlLength);

  //Now get the referenced locality page.
  $locJspPage = "http://".$host.$localityUrl;
  $str = file_get_contents($locJspPage);
  $result = "none";
  if ($str === false) {
    logUrlCheck("testUrl jspPage:".$jspPage." localityUrl:".$localityUrl + " locPage:not found");
  } else {
    if (strpos($str, "We'll be right back!") > 0) $result = "We'll be right back";
    if (strpos($str, "You've been away too long!") > 0) $result= "Away too long";
    $locHeaderPos = strpos($str, "<h1>Locality:");
    if ($locHeaderPos > 0) {
      $endLocHeaderPos = strpos($str, '</h1>', $locHeaderPos);
      $locHeaderLength = $endLocHeaderPos - $locHeaderPos;
      $result = substr($str, $locHeaderPos, $locHeaderLength);
    } 
    if ($localityUrl === "/") $result = "Bad Locality URL (/).";
    
    logUrlCheck("testUrl:".$jspPage." localityUrl:".$localityUrl." result:".$result);
  }
}

function isProduction() {
  return true ; //was file_exists('/home/gg/live/ug');
}

function logUrlCheck($logString) {
  logUrlCheckDate($logString, false);
}

function logUrlCheckDate($logString, $displayDate) {
  $filename = '/usr/local/tomcat/logs/urlCheck.log';
  if ($displayDate) {
    date_default_timezone_set('America/Los_Angeles');  
    $logString = $logString." ".date(DATE_RFC822)."\n";
  } else {
    $logString = $logString."\n";  
  }
  // Let's make sure the file exists and is writable first.

  if (!$handle = fopen($filename, 'a')) {
     echo "Cannot open file ($filename)";
     exit;
  }

  if (fwrite($handle, $logString) === FALSE) {
      echo "Cannot write to file ($filename)";
      exit;
  }

  //echo "Success, wrote ($logString) to file ($filename)"."\n";
  echo $logString;

  fclose($handle);
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
