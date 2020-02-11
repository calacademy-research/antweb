#!/usr/bin/php
<?php
ini_set('memory_limit', '-1');
set_time_limit(0);
$mtime = microtime();
$mtime = explode(" ",$mtime);
$mtime = $mtime[1] + $mtime[0];
$starttime = $mtime; 

function iterateDirectory($i) {
    $log_time = date("mYd_H_i_s");
    $log_file = 'logs/'.$log_time.'_med_logfile.txt';
    $handle = fopen ($log_file, 'a');
    $start = time();
    fwrite($handle, 'And we are off...'.PHP_EOL);
    $j = 1;
    foreach ($i as $path) {
        $s = time() - $start;
        $m = floor($s / 60);
        $h = floor($m / 60);
        $mins = ($m %  60);
        $seconds =  ($s % 60);
        if ($j % 5 == 0) {
            // sleep(2);
        }
        if ($path->isDir()) {
            iterateDirectory($path);
        } else {
            $ext = pathinfo($path, PATHINFO_EXTENSION);
            $ext = strtolower($ext);
            if ($ext == 'jpg') {
                if (preg_match('/_med[^_.]*\./', $path)) {
                    $filesize = filesize($path);
                    $filesize = $filesize/1024;
                    if ($filesize > 25) {
                    list($width, $height, $type, $attr) = getimagesize($path); 
                    $image = imagecreatefromjpeg($path);
                    $image2 = imagecreatetruecolor($width, $height);
                    imagecopyresampled ($image2, $image, 0, 0, 0, 0, $width, $height, $width, $height);
                    imagejpeg($image2,$path,100);
                    imagedestroy($image2);
                    imagedestroy($image);
                    if ($j % 1000 == 0) {
                        $log_time = date("mYd_H_i_s_");
                        $log_file = 'logs/'.$log_time.$j.'_med_logfile.txt';
                        $handle = fopen ($log_file, 'a');
                        fwrite($handle, $j.' files written, including '.$path.PHP_EOL);
                    }
                    // fwrite($handle, $j.' - '.$path.PHP_EOL);
                    $j++;
                    $success = file_exists($path);
                    if (!$success) {
                        $error_file = 'logs/errors_med.txt';
                        $error_handle = fopen ($error_file, 'a');
                        fwrite($error_handle, 'Could not create '.$path.PHP_EOL);
                        fclose($error_handle);
                    }
                  }
                }
            }
        }
    }
    fclose($handle);
}

$dir = '/data/antweb/images';
$iterator = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($dir));

iterateDirectory($iterator);

$mtime = microtime();
$mtime = explode(" ",$mtime);
$mtime = $mtime[1] + $mtime[0];
$endtime = $mtime;
$totaltime = ($endtime - $starttime);

$time_file = 'logs/total_time_med.txt';
$handle = fopen ($time_file, 'a');
fwrite($handle, 'The script ran for '.$totaltime.' seconds.'.PHP_EOL);
fclose($handle);
?>
