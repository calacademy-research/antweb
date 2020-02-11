# newGen.sh
#
# Usage: sh bin/newGen.sh
#
# This shell file will simply output which files have been generated since the last deployment
#
# If release.txt was copied into the build directory and deployed, then this would be more useful.
# Could be run not only on the source tree, but on the docroot (done).

find . -cnewer doc/release.txt

