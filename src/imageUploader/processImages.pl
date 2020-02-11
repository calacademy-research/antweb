#!/usr/bin/perl

# Called like this (for instance):
# cd /data/antweb/web/imageUploader/
#   was: /var/www/html/imageUpload/toUpload/
# $ sudo /home/antweb/antweb_deploy/src/imageUploader/processImages.pl -a "Will Ericson" -c "California Academy of Sciences, 2002-2014" -l "Attribution-ShareAlike (BY-SA) Creative Commons License and GNU Free Documentation License (GFDL)"
# was:
# sudo /Users/mark/dev/calacademy/antweb/src/perl/processImages.pl -a "Will Ericson" -c "California Academy of Sciences, 2002-2014" -l "Attribution-ShareAlike (BY-SA) Creative Commons License and GNU Free Documentation License (GFDL)"
# from process.php.


use Image::Magick;
use POSIX;
use Getopt::Std;
use File::Copy;

# read options
%options=();
getopt("acl",\%options);

print STDERR "processImages.pl pwuid: " . (getpwuid($<))[0]."  ";

$foo = getpgrp(0);
($name, $passwd, $gid, $members) = getgrgid($foo);
print STDERR "Current info for $foo is $name $passwd $gid $members \n";

$outputImageRoot = "/data/antweb/web/imageUploader/t";
$inputImageRoot = "/data/antweb/web/imageUploader";
# was:
#$outputImageRoot = "/data/antweb/images";
#$inputImageRoot = "/var/www/html/imageUpload/toUpload";
	
$artist = $options{a};
$copyright = $options{c};
$license = $options{l};
$date = getDate();

opendir(THISDIR,$inputImageRoot);
@allfiles = grep(!/^\.\.?$/, readdir(THISDIR));
closedir(THISDIR);
 
# copy over recent images files.  Created by process.php. Was:
#copy "/var/www/html/imageUpload/recentImages_gen_inc.jsp" => "/data/antweb/web/genInc/recentImages_gen_inc.jsp" or die "Can't copy /var/www/html/imageUpload/recentImages_gen_inc.jsp to /data/antweb/web/genInc/recentImages_gen_inc.jsp: $!\n";

foreach $file (@allfiles) {

# Modified by Mark on March 23, 2011 to support jpgs
# was:  next unless ($file =~ /.*\.tif/);
next unless ( ($file =~ /.*\.tif/) || ($file =~ /.*\.tiff/) || ($file =~ /.*\.jpg/) || ($file =~ /.*\.jpeg/)
           || ($file =~ /.*\.TIF/) || ($file =~ /.*\.TIFF/) || ($file =~ /.*\.JPG/) || ($file =~ /.*\.JPEG/)
  );


  ## get the specimen and shot name
  ##
  ($imageName,$oldType) = split(/\./,$file);
  $imageName =~ tr/A-Z/a-z/;

  ($specimen, $shot, $number) = split(/_/,$imageName);
  if ($number !~ /\d/) {
    $number = 1;
  }
  
  ## add metadata
  $inputFullPath = "$inputImageRoot/$file";
  addMetaData($inputFullPath, $copyright, $artist, $license, $specimen, $date);

  ## create a directory for this specimen if there isn't one
  $outputFullPath = $outputImageRoot."/".$specimen;
  mkdir($outputFullPath) || warn("warn: failed to mk dir $outputImageRoot/$specimen $!");

## warn("inputFullPath:".$inputFullPath." outputFullPath:".$outputFullPath);

  ## make a backup of the file
  backup($specimen, $file);

  ## read the image into Magick
  ##
  $image = new Image::Magick;
  $fileName = $inputImageRoot."/".$file;
  $error = $image->Read($fileName);
  warn "$error $fileName" if "$error";

  ## scale it to width 112
  $width = 112;
  
 eval {
  $newHeight = POSIX::floor($image->Get('rows') * $width / $image->Get('columns'));     
  $resizeImage = $image->Clone();
  $resizeImage->Resize(height=>$newHeight, width=>$width);
  $resizeImage->Set(density=>'72x72');
  $resizeImage->Set(quality=>80);
  writeOut($resizeImage, "low", $shot, $specimen, $number, $date, $copyright, $artist, $license);

  ## scale it to width 233 
  $width = 233;
  $newHeight = POSIX::floor($image->Get('rows') * $width / $image->Get('columns'));
  $resizeImage = $image->Clone();
  $resizeImage->Resize(height=>$newHeight, width=>$width);
  $resizeImage->Set(density=>'72x72');
  $resizeImage->Set(quality=>80);
  writeOut($resizeImage, "med", $shot, $specimen, $number, $date, $copyright, $artist, $license);

  ## scale it to width 480 
  $width = 480;
  $newHeight = POSIX::floor($image->Get('rows') * $width / $image->Get('columns'));
  $resizeImage = $image->Clone();
  $resizeImage->Resize(height=>$newHeight, width=>$width);
  $resizeImage->Set(density=>'72x72');
  $resizeImage->Set(quality=>45);
  writeOut($resizeImage, "thumbview", $shot, $specimen, $number, $date, $copyright, $artist, $license);

  ## scale it to height 540
  $height = 808;
  $newWidth = POSIX::floor($image->Get('columns') * $height / $image->Get('rows'));
  $resizeImage = $image->Clone();
  $resizeImage->Resize(height=>$height, width=>$newWidth);
  $resizeImage->Set(density=>'72x72');
  $resizeImage->Set(quality=>70);
  writeOut($resizeImage, "high", $shot, $specimen, $number, $date, $copyright, $artist, $license);
 };

  ## make a jpeg2000 version of it
  ## makeJP2($resizeImage, $shot, $specimen, $number, $date, $copyright, $artist, $license);

  ## now delete this thing
  unlink($inputImageRoot."/".$file) || warn("problem deleting ".$inputImageRoot."/". $file);
}

  sub backup {
    local($specimen, $theFile)=@_;

    $oldFile = $inputImageRoot. "/" .$theFile;
    $newFile = $outputImageRoot. "/" . $specimen . "/" . $theFile;

    ##@args = ("/bin/cp ", $oldFile, $newFile);
    ##system(@args) == 0
    ##     or doError("system $args[0] $args[1] $args[2] fails: $!");
    copy $oldFile => $newFile or die "Can't copy: $oldFile to $newFile $!\n";
  }

  sub writeOut {
    local($theImage, $type, $shot, $specimen, $number, $date, $copyright, $artist, $license)=@_;

    ## write it out
    $newFileName = $outputImageRoot. "/" . $specimen . "/" . $specimen . "_" . $shot. "_" . $number . "_" . $type .".jpg";
    $theImage->Write($newFileName);
    warn "$error" if "$error";

    ## add metadata
    addMetaData($newFileName, $copyright, $artist, $license, $specimen, $date);
}
  
sub makeJP2 {
    local($theImage, $shot, $specimen, $number, $date, $copyright, $artist, $license)=@_;

    ## write it out
    $newFileName = $outputImageRoot. "/" . $specimen . "/" . $specimen . "_" . $shot. "_" . $number . ".jp2";
    $theImage->Set(magick=>'.jp2');
    $theImage->Set(quality=>100);
    $theImage->Write(filename=>$newFileName);

    ## add metadata
    addMetaData($newFileName, $copyright, $artist, $license, $specimen, $date);
}

sub addMetaData {
  local($filename, $copyright, $artist, $license, $specimen, $date) = @_;

  ## warn("addMetaData() fileName:".$fileName);  
  callExifEdit("copyright", $copyright,$filename) if (length($copyright) > 0);
  callExifEdit("artist",$artist,$filename) if (length($artist) > 0);
  callExifEdit("comment",$license,$filename) if (length($license) > 0);
  callExifEdit("doc-name",$specimen,$filename) if (length($specimen) > 0);
  callExifEdit("date-mod",$date,$filename) if (length($date) > 0);
}

sub callExifEdit {
  local($field, $value, $file) = @_;
  $value =~ s/\,/&#44\;/g;
  @args = ("/usr/local/bin/exifedit", "-b", "-a", "$field=\"$value\"", $file);
  system(@args) == 0
         or doError("system $args[0] $args[1] $args[2] $args[3] fails: $!");
}

sub doError {
  local($message) = @_;
  die "$message\n";
}

sub getDate {
  my ($sec,$min,$hour,$mday,$mon,$year, $wday,$yday,$isdst) = localtime time;
  $year += 1900;
  $mon++;
  $sec = fixUp($sec);
  $min = fixUp($min);
  $hour = fixUp($hour);
  $mon = fixUp($mon);
  $mday = fixUp($mday);

  $date = "$year:$mon:$mday $hour:$min:$sec";
  return $date;
}

sub fixUp {
  local ($number) = @_;

  if ($number < 10) {
    $number = "0" . $number;
  } 

  return $number;
}
