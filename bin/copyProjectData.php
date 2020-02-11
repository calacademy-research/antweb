<?php

/*
 *  copyProjectData.php 
 *
 *  Usage:
 *    php bin/copyProjectData.php --backupDir=/home/mjohnson/bak/projectData
 *       // This is the default usage.  Will work on production server
 *
 *  To override the default data directory (/data/antweb) use: 
 *     php bin/copyProjectData.php --dataDir=/usr/local/tomcat/webapps/antweb 
 * 
 *  To specify a backup directory to copy the output use: 
 *     php bin/copyProjectData.php --backupDir=/home/mjohnson/bak/projectData
 *    And find the directory there with a timestamp in it's name.  Or just use: --backupDir=.
 *
 *  This script is to facillitate the construction of a fully functional development environment.
 *  This script will create a "projectData" directory that contains all projects, all files, in 
 *  the data directory NOT including the massive images directory, and a few others.  This directory
 *  may be zipped up, transfered, and unzipped in the appropriate webapps dir.
 * 
 *  After that you may care to cut down the size:
 *    find . -name \*.mov | xargs rm
 *    find . -name \*.pdf | xargs rm
 *
 *  Then change directory to your destination webapps directory and invoke an scp command like:
 *    sudo scp -r mark@10.2.22.112:/home/mark/projectData/* .
 *
 *  Mark.  Jan 14, 2011.
 *
 */ 

$args = parseArgs($_SERVER['argv']);
parseArgs($argv);
$dataDir = $args['dataDir'];

echo("dataDir:".$dataDir."\n");

if ($dataDir == "") {
  $dataDir = '/data/antweb';
  //$antwebHome = '/usr/local/tomcat/webapps/antweb';
}

$destDir = 'projectData';

$backupDir = $args['backupDir'];

echo "Copying ".$dataDir."\n";

copyProjectData($dataDir, $destDir, $backupDir);

exit;

function copyProjectData($dataDir, $destDir, $backupDir) {
  if (!is_dir($destDir)) {
    mkdir($destDir);
  }
  if ($handle = opendir($dataDir)) {
    echo "Directory handle: $handle\n";
    echo "Files:\n";

    /* This is the correct way to loop over the directory. */
    while (false !== ($file = readdir($handle))) {
        //echo "$file\n";
        if (
            ($file !== 'images')
            && ($file !== '.')
            && ($file !== '..')
            && ($file !== 'WEB-INF')
            && (strpos($file, "worldAuthorityFiles") === false)
            && (strpos($file, ".") !== 0)    // does not begin with a period  
            //&& (strpos($file, ".pdf") === false)    // these files are nested.  Would not work.
            && ($file !== 'web')
           ) {
          $copyCommand =  " cp -r $dataDir/$file $destDir/$file 2>&1 ";
          echo $copyCommand."\n";
          $output = shell_exec($copyCommand);
          echo $output;        
        }
    }
    
//    $rmPdfCommand = "find $destDir -name \*.pdf -print0 | xargs -0 rm 2>&1 ";
//    echo $rmPdfCommand."\n";
//    $output = shell_exec($rmPdfCommand);
//    echo $output;        
    

    if ($backupDir != "") {
      $dateStr = shell_exec("date +%Y-%m-%d=%H:%M");
      $mvCommand = "mv $destDir $backupDir/projectData$dateStr";
      echo $mvCommand."\n";
      $output = shell_exec($mvCommand);
      echo $output;        
    }
    
    
    closedir($handle);
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
