#!/usr/bin/perl

use Image::Magick;
use POSIX;

$inputImageRoot = "/data/antweb/images_bak_2";
$outputImageRoot = "/data/antweb/images_bak_2";
$exifCopy = "/home/antweb/workingdir/exifcopy";

opendir(THISDIR,$inputImageRoot);
@alldirs = grep(!/^\.\.?$/, readdir(THISDIR));
closedir(THISDIR);

foreach $dir (@alldirs) {
	chomp($dir);
	next if ($dir !~/\w/);
  next unless (-d "$inputImageRoot/$dir");

	opendir(IMGDIR,"$inputImageRoot/$dir");
	@allpics = grep(!/^\.\.?$/, readdir(IMGDIR));

	foreach $imageFile (@allpics) {
		chomp($imageFile);
	  next if ($imageFile !~/\w/);
		next unless ($imageFile=~/_high\.jpg/);
		resize($dir, $imageFile, "med", 233);
		resize($dir, $imageFile, "low", 112);
		resize($dir, $imageFile, "thumbview", 480);

	}
	closedir(IMGDIR);
}

sub resize() {
	my($directory, $fileName, $sizeName, $newWidth)=@_;


  ## get the specimen and shot name
  ##
  my ($imageName,$oldType) = split(/\./,$fileName);
  $imageName =~ tr/A-Z/a-z/;

  my ($specimen, $shot, $number, $size) = split(/_/,$imageName);
  if ($number !~ /\d/) {
    $number = 1;
  }
  
  ## read the image into Magick
  ##
  my $image = new Image::Magick;
  my $fullFileName = $inputImageRoot."/".$directory."/".$fileName;
  my $error = $image->Read($fullFileName);
  warn "$error $fullFileName" if "$error";

  ## scale it to given width
  my $height = POSIX::floor($image->Get('rows') * $newWidth / $image->Get('columns'));
  my $resizeImage = $image->Clone();
  $resizeImage->Resize(height=>$height, width=>$newWidth);
  $resizeImage->Set(density=>'72x72');
  $resizeImage->Set(quality=>80);
  writeOut($resizeImage, $sizeName, $dir, $fileName);

}


sub writeOut {
  my($theImage, $newSize, $directory, $fileName)=@_;

  my($specimen, $shot, $number, $size) = split(/_/,$fileName);

  my $newFileName = $outputImageRoot. "/" . $directory . "/" . $specimen . "_" . $shot. "_" . $number . "_" . $newSize .".jpg";
	print "Writing out $newFileName\n";
  $theImage->Write($newFileName);
  warn "in write out $error" if "$error";

  ## copy metadata
  copyMetaData($directory, $outputImageRoot. "/" . $directory . "/". $fileName, $newFileName);

}
  
sub copyMetaData {
  my($directory, $inFile, $outFile) = @_;
  my @args = ($exifCopy, "-bo", $inFile, $outFile);
  system(@args) == 0
         or doError("system $args[0] $args[1] $args[2] $args[3] fails: $!");
}

sub doError {
  my($message) = @_;
  die "$message\n";
}

