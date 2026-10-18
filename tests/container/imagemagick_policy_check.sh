#!/bin/sh
# Checks the ImageMagick policy of the antweb image (docker/antweb/imagemagick-policy.xml).
# Run inside the image (CI: .github/workflows/tests.yaml). The formats AntWeb converts
# (TIFF, JPEG, PNG -> JPEG, as ImageUploaded.genImage does) must still work; files that
# use risky formats, whatever their extension, must be refused.
set -u
echo "::notice::ImageMagick in the image: $(convert -version | head -1 | cut -c1-120); formats: $(convert -list format 2>/dev/null | grep -iE "^ *(EPHEMERAL|MVG|MSL|TEXT)" | tr -s " " | tr "\n" "|" | cut -c1-300)"
echo "policy files: $(ls /etc/ImageMagick-*/policy.xml 2>&1)"
convert -list policy | grep -q "MVG" || echo "::error::ImageMagick check: the AntWeb policy is not loaded ($(convert -version | head -1); $(ls -d /etc/ImageMagick-* 2>&1))"
d=$(mktemp -d)
cd "$d" || exit 1
fail=0

# Sample images in the formats curators upload.
convert -size 640x480 gradient:white-gray30 sample.tif &&
convert -size 640x480 gradient:white-gray30 sample.jpg &&
convert -size 640x480 gradient:white-gray30 -alpha set sample.png || { echo "::error::ImageMagick check: could not make samples"; exit 1; }

for f in sample.tif sample.jpg sample.png; do
  # Same operations as im4java builds in ImageUploaded.genImage (flatten, resize).
  if convert "$f" -flatten -resize 300x400 "out-$f.jpg" && [ -s "out-$f.jpg" ]; then
    echo "ok: $f converts"
  else
    echo "::error::ImageMagick check: $f does not convert: $(convert "$f" -flatten -resize 300x400 "out-$f.jpg" 2>&1 | head -c 300)"; fail=1
  fi
done

secret=/etc/hostname
printf 'push graphic-context\nviewbox 0 0 64 64\nimage over 0,0 0,0 "text:%s"\npop graphic-context\n' "$secret" > mvg.tif
printf '<?xml version="1.0"?>\n<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="64" height="64"><image xlink:href="text:%s" width="64" height="64"/></svg>\n' "$secret" > svg.tif
printf '<?xml version="1.0" encoding="UTF-8"?>\n<image><read filename="%s"/><write filename="msl-out.jpg"/></image>\n' "sample.jpg" > msl.png
printf '%%!PS\n/Courier findfont 12 scalefont setfont 10 10 moveto (x) show showpage\n' > ps.jpg

refuse() {
  if convert "$@" refused.jpg 2>/dev/null && [ -s refused.jpg ]; then
    echo "::error::ImageMagick check: not refused: $* ($(cp sample.tif probe.tif; convert -debug Policy "$(echo "$*" | sed 's/sample.jpg/probe.tif/')" probe-out.jpg 2>&1 | grep -i policy | head -3 | tr "\n" " " | cut -c1-400))"; fail=1
  else
    echo "ok: refused: $*"
  fi
  rm -f refused.jpg
}
refuse mvg.tif
refuse mvg:mvg.tif
refuse svg.tif
refuse msl:msl.png
refuse ps.jpg
refuse "text:$secret"
refuse "label:x"
refuse "@$secret"
refuse "ephemeral:sample.jpg"
refuse "https://www.antweb.org/favicon.ico"
[ ! -e msl-out.jpg ] || { echo "::error::ImageMagick check: MSL script ran"; fail=1; }

cd / && rm -rf "$d"
[ "$fail" = 0 ] && echo "ImageMagick policy OK"
exit $fail
