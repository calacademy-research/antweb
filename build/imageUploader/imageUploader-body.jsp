<%@ page import = "java.util.*"%>


<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.util.*" %>

<div id="page_contents">

<div class="admin_left">

<% String message = (String) request.getAttribute("message"); 
   if (! (message == null)) {
     out.println(message);
   }

    Login accessLogin = LoginMgr.getAccessLogin(request);
    Group accessGroup = GroupMgr.getAccessGroup(request);
%>

    <div class="admin_layout" style="background:#fff;">
        <div class="admin_action_item">
            <div style="float:left;"><h1>Specimen Image Uploader</h1></div>
            <div class="align_right action_hint" style="position:relative; top:15px;"><!-- <a href="#" onclick="showHelp('upload_help'); return false;">How to prepare files for upload?</a>--></div>

            <div class="clear"></div>
        </div>
    </div>
    
    <br>

<%
   // This warning is to prevent developers (me) from accidentally posting data to the live site.
   if (!AntwebProps.isDevOrStageMode()) {
     if (LoginMgr.isDeveloper(accessLogin)) { 
       %><h2><font color=red>Warning: Live Site</font></h2><% 
     }
   } 
%>

<br>

      <h3>1. Select files to upload:</h3>
      <br>
      <form action = "<%=AntwebProps.getDomainApp() %>/imageUploader.do" 
            method = "post" enctype = "multipart/form-data">
        <input type="file" name="file" id="file" multiple/>          

        <br>      

        <div id="attribution">

        <br><br>
        <div style="float:left;"><h3>2. Select Artist</h3></div>
        <div class="clear"></div>
        <select name="artist">

        <% 
           ArrayList<Artist> artists = ArtistMgr.getArtists();
           java.util.Collections.sort(artists, Artist.ArtistNameComparator);
           for (Artist artist : ArtistMgr.getArtists()) { %>

             <option value="<%= artist.getId() %>"><%= artist.getName() %></option>

        <% } %>

        </select>
        <br><a class="action_hint" href="<%=AntwebProps.getDomainApp() %>/artists.do">Edit or Create New Artist (Photographer)</a>
        <!-- input type="hidden" name="copyright" value="10"/ -->
        <!-- input type="hidden" name="license" value="1"/ -->
        <input type="hidden" name="group" value="<%= accessGroup.getId() %>">
        
        <br><br><br>

        <h3>3. <input type = "submit" value = "Upload Files" /></h3>


    </form>
    </div>

    <br>
    <br>
    <br>
    <hr>
    <br>
    
    For full image submission documentation, see: <a href='https://www.antweb.org/web/curator/1/Submitting_image_files_to_Antweb%20v12.pdf'>here</a>
    <br>
    
<% if (false && LoginMgr.isDeveloper(request)) { %>
<h3>Overview</h3>

Four standard images (dorsal, head, profile, and label image) for each specimen are typically uploaded to AntWeb, Additional images (no limit on how many) can also be uploaded, e.g. wings of queen or male, male genitalia, and ventral images. Other images, such as SEM images, can be uploaded to AntWeb. 

Images cannot be viewed on AntWeb unless the image files are correctly named and correspond to specimen data already uploaded to AntWeb.

<h3>Files types accepted</h3>

File types accepted; high resolution Tiff, Jpg or PNG files. All image files should be flattened and preferably no larger than 20 MB.  High resolution tiff files are preferred.

<h3>Image File Names:</h3>

Each image file name consists of the unique specimen code followed by an underscore, then a letter code that refers to the position (view) of the specimen, then an underscore and number sequence of the image of that view.

<h3>specimen codes:</h3>

Each specimen imaged is given a specimen code. CAS specimens are given a unique, seven-digit, “CASENT” number such as CASENT0172421, CASENT0005664, and CASENT0435930

<h3>letter view codes:</h3>

D = dorsal image of specimen

H = full-face image of head of specimen

P = profile, or lateral image of specimen

L = label image

V = Ventral images

Example: a profile image of an ant with a specimen code of CASENT0006129 would be named CASENT0006129_P._1 

A label image for the same specimen would be named CASENT0006129_L_1.

<h3>Naming Extra Images:</h3>

If additional images are available, e.g. wings of a queen, male or male genitalia, simply add a number to the view code. The additional images would be _D_2, _H_2, or _P_2. etc.

Example: a profile image of an alate queen or male ant with a specimen code of CASENT0006129 would be named CASENT0006129_P_1, while a second profile image (wing shot) would be named CASENT0006129_P_2.

<b>Notes:</b> Do not use suffix letters other than D, H, P, L, V. AntWeb will not accept them.

Any image containing a “_1” suffix is the first image seen in thumbnails and field guides on AntWeb. If you name one image with no number suffix and an additional image with a “_1” suffix, one will override the other.

For male ants and alate queens, take one close-up image of the wings.

Male ants should also get an additional image of the genitalia taken at a 45-degree angle. This image would have a suffix “_P_3” (assuming you have taken a close up of the wing for “_P_2”).

If a specimen is broken and mounted on more than one point, multiple profile shots are needed. If the head is on a separate point, the standard head image _H_1 should be taken plus an additional image from the lateral view labeled _H_2 (the profile of the head will not show up in the P shot).

Additional images (for keys, plates, publications or information) are always welcome, and should include number suffixes based on the corresponding position; _3, _4, _5 etc.
<% } %>

    <br>
    <br>
    <div id="note" class="action_hint">
       <strong>Note:</strong> All images are uploaded under the <a href="http://creativecommons.org/licenses/by-sa/3.0/">Attribution-ShareAlike 3.0 (cc-by-sa-3.0) Creative Commons License</a>.
    </div> 

    <br>
    <div id="note" class="action_hint">
       <strong>Note:</strong> This new Flash-free, pure Java implementation is faster and more reliable than the old image upload feature. For any issues or feature requests please contact <%= AntwebUtil.getAdminEmail() %>.
    </div> 

</div>

</div>