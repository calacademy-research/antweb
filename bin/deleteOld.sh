# deleteOld.sh
#
# Change directory to the antweb webapp root
#
#   sh /Users/macpro/dev/calacademy/antweb/bin/deleteOld.sh
#
#   			
#
# This script is designed to delete codebase artifacts from the deployment directory.
# It is non-destructive and safe to run.  Only deprecated files of no further use should be included here.
#

rm -rf projects

rm -f staff-body.jsp
rm -f staff.jsp
rm -f staff_gen_inc.jsp

rm -f login/viewLogin-body.jsp
rm -f ancEdit-body.jsp
rm -f ancEdit.jsp
rm -f ancNew-body.jsp
rm -f ancNew.jsp
rm -f project_upload-body.jsp
rm -f project_upload.jsp

rm -f change_password.jsp 
rm -f changepasswordsuccess-body.jsp 

rm -f manage_groups.jsp
rm -f save_group_success-body.jsp 
rm -f save_group_success.jsp 
rm -f uploadResults.jsp 
rm -f viewgroup-body.jsp 
rm -f viewgroup.jsp

#googleEarth.jsp      is now viewGoogleEarth.jsp
#localityMap.jsp      is no longer referenced in the source tree
#taxonPageEdit.jsp    is no longer referenced in the source tree

rm -f googleEarth.jsp
rm -f localityMap-body.jsp
rm -f localityMap.jsp
rm -f taxonPageEdit-body.jsp
rm -f taxonPageEdit.jsp

rm -f edit_project-body.jsp
rm -f edit_project.jsp
rm -f new_project-body.jsp
rm -f new_project.jsp
rm -f antweb_admin-body.jsp.old
rm -f antweb_admin.jsp
rm -f antweb_admin-body.jsp
rm -f upload-body.jsp
rm -f upload.jsp



# These jsps were removed from CVS, but are ... investigate more.
#documentation/documentation-body.jsp 
#documentation/documentation.jsp
