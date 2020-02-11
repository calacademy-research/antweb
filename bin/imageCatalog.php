<?php
/*
 *  imageCatalog.php    Written: Mark Apr 25, 2012.
 *
 *  Usage:
      php imageCatalog.php --dirName=/Users/macpro/dev/tomcat/webapps/antweb/images/
    or    
      php imageCatalog.php --dirName=/data/antweb/images/

 options are 
   --dirName for the directory where images are stored

   This program recurses through all directories, gets all image file names and inserts them into
a database table.  When run accross two servers, then database comparisons can be made.

Steps:

1. Run on a stage server (for instance) to populate the image_catalog table.


2. Create a database dump of that table.  Transfer it to another server and load it.  Then run imageCatalog.php on that server.

mysqldump --opt --skip-lock-tables -u antweb -p ant image_catalog | gzip > /home/mjohnson/bak/db/imageCatalog.sql.gz
scp mjohnson@10.2.22.81:/home/mjohnson/bak/db/imageCatalog.sql.gz .
gunzip < imageCatalog.sql.gz | mysql -u antweb -p ant
  (You may need to create the table.  See: /db/upgrade/4.67/2012-04-25.sql )
create table image_catalog2 as select * from image_catalog;
php imageCatalog.php --dirName=/data/antweb/images/

3. Query the database to see which records are in the old snapshot but not in the new snapshot.

 select distinct i2.dir_name from image_catalog2 i2 where i2.dir_name not in (select distinct dir_name from image_catalog);


This process was successfully implemented on April 25, 2012, verifying that all of the original antweb
images were copied over.
 */ 

$args = parseArgs($_SERVER['argv']);
parseArgs($argv);

$dirName = $args['dirName'];

echo("dirName:".$dirName."\n");

  $conn = mysql_connect("localhost","antweb","f0rm1c6");
  @mysql_select_db("ant") or logAndDie("could not connect to ant db");


deleteImages();

process($dirName);

  mysql_close($conn);
  
exit;

// --------------------------------------------------------

function process($dirName) {
  if ($handle = opendir($dirName)) {
    echo "Directory: $dirName\n";
    //echo "Files:\n";

    /* This is the correct way to loop over the directory. */
    while (false !== ($file = readdir($handle))) {
        //echo "$file\n";
      if ($file !== '..') {

           if (is_dir_LFS($dirName.$file)) {
             echo "$file\n";
             
             if ($subDirHandle = opendir($dirName.$file)) {
               while (false !== ($subDirFile = readdir($subDirHandle))) {
                if (($subDirFile !== '.')
                    && ($subDirFile !== '..')) {
                    
                   echo "  $subDirFile\n";
                   insertImage($file, $subDirFile);
                 }
               }
             }
             closedir($subDirHandle);

             insertImage(".", $file);
           } else {
             echo "$file\n";
           }
      }           
    }
    
    closedir($handle);
  }
}  

function deleteImages() {
  $delete = "delete from image_catalog";
  $queryResult = mysql_query($delete);
}

function insertImage($dir, $file) {
  $insert = "insert into image_catalog (dir_name, image_name) values ('$dir', '$file')";
  $queryResult = mysql_query($insert);
  
  //logError($insert);
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

function is_dir_LFS($path){
  // This is the only one to work on my mac
  return (('d'==substr(exec("ls -dl '$path'"),0,1))?(true):(false));
}

function isDir($dir) {
  $cwd = getcwd();
  $returnValue = false;
  if (@chdir($dir)) {
    chdir($cwd);
    $returnValue = true;
  }
  return $returnValue;
}

  function logAndDie($error) {
    logError($error);
    die;
  }
  
  function logError($error) {
    $std = fopen('php://stderr','w');
    fputs($std, "upload.php.  $error\n");
    fclose($std);
  }
  
?>
