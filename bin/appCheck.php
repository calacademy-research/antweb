<?php

/*
 *  appCheck.php
 *
 *  In the case that the Antweb server is not running, this will launch it.  
 *  This script is designed to be run as a root level cron job and may be run
 *  from the command line:
 *
 *  sudo php bin/appCheck.php  
 *
 *      AppCheck greps the running processes for the Antweb application.  If not found,
 *  then a period of time is waited before retesting.  This avoids restarting the
 *  application during a timed or engineer triggered restart.
 *      A log file /data/antweb/web/log/appCheck.log records appCheck.php triggered events.
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

 * "/etc/init.d/tomcat5 start" is properly accessed by: "/etc/init.d/priv_tomcat start"
 * 
 * Two changes were made on Dec 26.  Comment out first part of stopProcess() and change uptime.jsp to uptime.do
 */ 

//logAppCheck("", "Running appCheck");

// We are searching the ps results for two strings to avoid the grep command itself returning a false positive.
$grepStr1 = "Bootstrap";  // a string found in the process on both stage and production
$grepStr2 = "java"; // might be something like jsvc

// This is the command to launch the process if it is not found.

//$sleepTime = 300;  // in seconds.  5 minutes.
$sleepTime = 10; // useful for testing  
  
//showProcesslist();
  
if (! hasProcess($grepStr1, $grepStr2)) {
  // Then wait five minutes and recheck (might be nightly restart)
  logAppCheck("", "  Process not found.  Waiting:".$sleepTime." seconds...");
  sleep($sleepTime);
  if (! hasProcess($grepStr1, $grepStr2)) {
    //logAppCheck("", "Process still not found.  Starting.");
    startProcess();        // Dec 31
  }
} else {
  if (! processResponds()) {
    // Then wait process time and recheck (might be nightly restart)
    logAppCheck("", "  Process unresponsive.  Waiting:".$sleepTime." seconds...");
    sleep($sleepTime);
    if (! processResponds()) {
      //logAppCheck("", "Process still unresponsive.  Restarting:".$restartCommand);
      stopStartProcess();   // Dec 31!
      //restartProcess();
    }
  }
}

logAppCheck("", "appCheck.");

exit;

function startProcess() {
  global $tomcat;
  $startCommand = "systemctl start tomcat";  // works on stage and production
  exec($startCommand, $return);
  $message = "  Starting Antweb Process: ".$startCommand;   //." return:".$return[0];
  print($message."\n");
  logAppCheck("", $message);
}

function restartProcess() {
  global $tomcat;
  $restartCommand = "systemctl restart tomcat";
  //if (isProduction()) {
  //  $restartCommand = "/home/mjohnson/antweb/bin/restartUg.sh";  
  //}
  print("  Restarting Process: ".$restartCommand."\n");
  logAppCheck("", "  Restarting Antweb".$restartCommand);
  exec($restartCommand, $return);
  print("  Return:".$return[0]."\n");  
}

function stopStartProcess() {
  stopProcess();
  startProcess();
}

function stopProcess() {
  //showProcesslist();

  $stopCommand = "pkill java";
  exec($stopCommand, $return);  
  $message = "  stop:".$stopCommand." return:".$return[0];
  print($message."\n");
  logAppCheck("", $message);
}

/*
function showProcesslist() {  
  $username="antweb";$password="f0rm1c6";$database="ant";
  mysql_connect('localhost',$username,$password);
  @mysql_select_db($database) or die( "Unable to select database");
  $sql_showprocesslist = "SHOW PROCESSLIST";
  $result_showprocesslist = (mysql_query ($sql_showprocesslist));
  $result = "ProcessList:\n";
  $i = 0;
  while ($row = mysql_fetch_array($result_showprocesslist))
  {
    ++$i;
    $result = $result."   ".$i.". id:".$row["Id"]." host:".$row["Host"]." db:".$row["db"]." command:".$row["Command"]." time:".$row["Time"]." state:".$row["State"]." info:".$row["Info"]."\n";
  }
  logAppCheck('/usr/local/tomcat/logs/processList.log', $result);
  mysql_close();
}
*/
function hasProcess($grepStr1, $grepStr2) {

  //if (true) return true;  // Useful for testing processResponds()

  $command= 'ps ax | grep '.$grepStr1." 2>&1";

  print("Executing command:".$command."\n");

  exec($command, $return);

  //$output = $return[0];
  // print("output:".$output."\n");
  //print("return[0]:".$return[0]."\n");  
  //print("return[1]:".$return[1]."\n");  
  //print("return[2]:".$return[2]."\n");

  $hasProcess = false;

  foreach ($return as $grepResult) {
    $findVal = strpos($grepResult, $grepStr2);

    if ($findVal > 0) { 
      print ("\nfindVal:".$findVal." in GrepResult:".$grepResult."\n");
      $hasProcess = true;
    }
  }
  if ($hasProcess == false) {
      //logAppCheck("", "  No Process.  FindVal:".$findVal." return:".$return);
  }
  return $hasProcess;
}

function processResponds() {
  $timeout = 3;
  $old = ini_set('default_socket_timeout', $timeout);

//  $jspPage = '';
  $jspPage = 'http://localhost/uptime.do';

/*
  if (isProduction()) {
    $jspPage = 'http://antweb.org/uptime.do';
  } else {
    $jspPage = 'http://antweb-stg/uptime.do';
    //logAppCheck("", $jspPage);
    //$jspPage = 'http://antweb.org/antweb_test/uptime.jsp';
  }
*/

  $str = file_get_contents($jspPage);
  //logAppCheck("", "  processResponds():".$jspPage." ".strpos($str, "success"));                                                                        

  $successPos =	strpos($str, "success");
  if ($successPos <= 0) {
    // We did not receive success.  What to do?
    logAppCheck("", "Failure from ".$jspPage." returned:".$str);
    runDiagnose();
    return false;
  } else {
    logAppCheck("", "success from ".$jspPage." returned:".$str);
    return true;
  }
}

/*
function isProduction() {
  //return true ; 
  return file_exists('/home/antweb/appCheck.txt');
}
*/

function logAppCheck($fileName, $logString) {
  $logFileName = '/data/antweb/web/log/appCheck.log';
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

  echo "Write ($logString) to file ($logFileName)"."\n";

  fclose($handle);
}

//top -b -n1 | grep mysql
//top -b -n1 | grep httpd

function runDiagnose() {
  $startCommand = "top -b -n1 | grep java";
  exec($startCommand, $return);
  $message = "PID USER      PR  NI  VIRT  RES  SHR S %CPU %MEM    TIME+  COMMAND";
  $message = $message."\n".$startCommand." = ".$return[0];
  //echo("\nMessage0:".$message);                                                                                                                                                                                                                                   
  $startCommand = "top -b -n1 | grep mysql";
  exec($startCommand, $return);
  //echo("\nMessage1:".$message);                                                                                                                                                                                                                                   
  $message = $message."\n".$startCommand." = ".$return[0];
  //echo("\nMessage2:".$message);                                                                                                                                                                                                                                   
  //$fileName = '/usr/local/tomcat/logs/appCheckDiagnose.log';
  $fileName = '/data/antweb/web/log/appCheck.log';
  //echo("message3:".$message);                                                                                                                                                                                                                                     
  logAppCheck($fileName, $message);
}


?>
