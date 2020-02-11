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
    $log_file = 'logs/'.$log_time.'_logfile.txt';
    $handle = fopen ($log_file, 'a');
    $start = time();
    fwrite($handle, 'Here we go...'.PHP_EOL);
    $j = 1;
    $ignore_directory = ".snapshot";
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
          if (!preg_match('/.snapshot[^_.]*\./', $path)) {
            iterateDirectory($path);
          }
        } else {
          if (!preg_match('/.snapshot[^_.]*\./', $path)) {
            $ext = pathinfo($path, PATHINFO_EXTENSION);
            $ext = strtolower($ext);
            if ($ext == 'jpg') {
                if (preg_match('/_high[^_.]*\./', $path)) {
                    list($width, $height, $type, $attr) = getimagesize($path); 
                    $new_filename = preg_replace('/_high[^_.]*\./', '_thumbview.', $path);
                    $exists = file_exists($new_filename);
                    if (!$exists) {
                        $image = imagecreatefromjpeg($path);
                        $maxW = 480;
                        $maxH = 360;
                        $w_ratio = $maxW / $width;
                        $h_ratio = $maxH / $height;
                        if(($width/$height) > ($maxW/$maxH)){
                            $maxH = ceil($w_ratio * $height);
                        } else {
                            $maxW  = ceil($h_ratio * $width);
                        }
                        $image2 = imagecreatetruecolor($maxW, $maxH);
                        imagecopyresampled ($image2, $image, 0, 0, 0, 0, $maxW, $maxH, $width, $height);
                        imagejpeg($image2,$new_filename,80);
                        imagedestroy($image2);
                        imagedestroy($image);
                        $success = file_exists($new_filename);
                        if ($success) {
                            if ($j % 1000 == 0) {
                                // fclose($handle);
                                $log_time = date("mYd_H_i_s_");
                                $log_file = 'logs/'.$log_time.$j.'_logfile.txt';
                                $handle = fopen ($log_file, 'a');
                                fwrite($handle, $j.' files written, including '.$new_filename.PHP_EOL);
                            }
                            // fwrite($handle, $j.' - '.$new_filename.PHP_EOL);
                            $j++;
                        } else {
                            $error_file = 'logs/errors.txt';
                            $error_handle = fopen ($error_file, 'a');
                            fwrite($error_handle, 'Could not create '.$new_filename.PHP_EOL);
                            fclose($error_handle);
                        }
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

$time_file = 'logs/total_time.txt';
$handle = fopen ($time_file, 'a');
fwrite($handle, 'The script ran for '.$totaltime.' seconds.'.PHP_EOL);
fclose($handle);
?>
