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


<br>

<%
   // dryRun=1: every file is checked and sent, nothing is saved (used by the staging tests).
   boolean iuDryRun = "1".equals(request.getParameter("dryRun"));
%>
<style>
  #iuStatus { margin: 12px 0; max-width: 60em; }
  #iuStatus .iu-banner { padding: 8px 10px; margin: 8px 0; border: 1px solid #c9a227; background: #fff8e1; }
  #iuStatus .iu-banner.iu-test { border-color: #3a7bd5; background: #eef4fd; }
  #iuStatus .iu-totals { font-weight: bold; margin: 6px 0; }
  #iuStatus .iu-buttons button { margin: 4px 8px 4px 0; }
  #iuList { list-style: none; padding: 0; margin: 6px 0; max-height: 28em; overflow-y: auto; border: 1px solid #ddd; }
  #iuList li { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 10px; padding: 3px 6px; border-bottom: 1px solid #eee; font-size: 0.9em; }
  #iuList .iu-name { flex: 1 1 18em; overflow-wrap: anywhere; }
  #iuList .iu-size { flex: 0 0 5.5em; text-align: right; color: #555; }
  #iuList progress { flex: 0 0 8em; height: 0.9em; }
  #iuList .iu-state { flex: 1 1 14em; }
  #iuList li.iu-done .iu-state { color: #1b6e1b; }
  #iuList li.iu-failed .iu-state, #iuList li.iu-rejected .iu-state { color: #b00020; }
  #iuList li.iu-wait .iu-state { color: #8a6d00; }
</style>

      <div class="action_hint" id="iuHowItWorks" style="max-width:60em; margin-bottom:10px;">
        <strong>Uploading many images:</strong> you can select any number of images (25, 100 or more) in one go.
        The page checks all file names first, then sends the images one at a time and shows each one's progress;
        each image is saved and its sizes made as soon as it arrives, so keep this page open until the list says
        <em>Upload finished</em>. If a file fails because of the network or a busy server, the page tries it again
        by itself (up to 4 tries, waiting longer each time); files that still fail can be sent again with
        <em>Retry failed files</em>. Each image must be smaller than 95 MB. If your login or the site's security check
        runs out during a long upload, the page pauses and tells you what to do; nothing already sent is lost.
        Everything you send in one go appears as one upload in your
        <a href="<%= AntwebProps.getDomainApp() %>/listImageUploads.do?curatorId=<%= accessLogin.getId() %>">Image Upload Reports</a>.
      </div>

      <noscript><p><b>The Image Uploader needs JavaScript. Please enable it in your browser.</b></p></noscript>

      <h3>1. Select files to upload:</h3>
      <br>
      <form id="imageUploadForm" action="<%= org.calacademy.antweb.util.Csrf.action(request, (AntwebProps.getDomainApp()) + "/imageUploader.do") %>" 
            method = "post" enctype = "multipart/form-data"
            data-batch-url="<%= Html.escAttr(AntwebProps.getDomainApp() + "/imageUploadBatch.do") %>"
            data-session-url="<%= Html.escAttr(AntwebProps.getDomainApp() + "/editSession.do") %>"
            data-csrf="<%= Html.escAttr(org.calacademy.antweb.util.Csrf.token(request)) %>"
            data-max-bytes="<%= org.calacademy.antweb.imageUploader.ImageUploadBatchAction.MAX_FILE_BYTES %>"
            data-dry-run="<%= iuDryRun ? "1" : "0" %>"><%= org.calacademy.antweb.util.Csrf.field(request) %>
        <input type="file" name="file" id="file" multiple accept=".jpg,.jpeg,.tif,.tiff,.png,image/jpeg,image/tiff,image/png"/>          

        <br>      

        <div id="attribution">

        <br><br>
        <div style="float:left;"><h3>2. Select Artist</h3></div>
        <div class="clear"></div>
        <select name="artist">

        <% 
           // A sorted copy: the shared list must not be sorted by two requests at once.
           ArrayList<Artist> artists = new ArrayList<>(ArtistMgr.getArtists());
           java.util.Collections.sort(artists, Artist.ArtistNameComparator);
           for (Artist artist : artists) { %>

             <option value="<%= artist.getId() %>"><%= artist.getName() %></option>

        <% } %>

        </select>
        <br><a class="action_hint" href="<%=AntwebProps.getDomainApp() %>/artists.do">Edit or Create New Artist (Photographer)</a>
        <!-- input type="hidden" name="copyright" value="10"/ -->
        <!-- input type="hidden" name="license" value="1"/ -->
        <input type="hidden" name="group" value="<%= accessGroup.getId() %>">
        
        <br><br><br>

        <h3>3. <input type = "submit" id="iuSubmit" value = "Upload Files" /></h3>


    </form>
    </div>

    <div id="iuStatus" aria-live="polite" hidden>
      <% if (iuDryRun) { %><div class="iu-banner iu-test">Test mode (dryRun=1): files are checked and sent, but nothing is saved.</div><% } %>
      <div id="iuBanner" class="iu-banner" hidden></div>
      <div id="iuTotals" class="iu-totals"></div>
      <div class="iu-buttons">
        <button type="button" id="iuPause" hidden>Pause</button>
        <button type="button" id="iuResume" hidden>Resume</button>
        <button type="button" id="iuRetry" hidden>Retry failed files</button>
        <button type="button" id="iuFinish" hidden>Finish without the failed files</button>
        <button type="button" id="iuCopyFailed" hidden>Copy list of failed files</button>
      </div>
      <ul id="iuList"></ul>
    </div>
    <script src="<%= AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, "/imageUploader/imageUploader.js") %>"></script>

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
       <strong>Note:</strong> For any issues or feature requests please contact <%= AntwebUtil.getAdminEmail() %>.
    </div> 

</div>

</div>