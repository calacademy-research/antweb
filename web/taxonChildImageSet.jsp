<%@ page import="org.calacademy.antweb.Specimen" %>
<%@ page import="org.calacademy.antweb.SpecimenImage" %>
<%@ page import="org.calacademy.antweb.Taxon" %>
<%@ page import="org.calacademy.antweb.Formatter" %>

<%
// Must have maleSpecimen, workerSpecimen and queenSpecimen defined in included jsp.
// Must have String defaultSpecimen defined in including jsp.
// Must have AccessGroup defined.
// Must have Taxon thisChild defined.

//String queenSpecimen = "casent0004350";
//String maleSpecimen = "casent0004353";
//String workerSpecimen = "casent0004352";

/*
See Sep 31 sent email "taxonPage image count"
In the case of:
  /description.do?name=fj05&genus=cerapachys&rank=species&project=allantwebants
images:{l1=org.calacademy.antweb.SpecimenImage@28d51032, h2=org.calacademy.antweb.SpecimenImage@3c1a578f, h1=org.calacademy.antweb.SpecimenImage@656a8c1c, d2=org.calacademy.antweb.SpecimenImage@3614b648, d1=org.calacademy.antweb.SpecimenImage@9cb4cb5}
images:{l1=org.calacademy.antweb.SpecimenImage@2190419, h1=org.calacademy.antweb.SpecimenImage@385c0662, d2=org.calacademy.antweb.SpecimenImage@21a728d6, d1=org.calacademy.antweb.SpecimenImage@656a0adc, p1=org.calacademy.antweb.SpecimenImage@14ef2588}


Examples of species with images that don't have h1 images:
/description.do?name=nodiferum&genus=tetramorium&rank=species&project=allantwebants
/description.do?name=beb002&genus=meranoplus&rank=species&project=allantwebants
*/

    //AntwebUtil.log("child:" + thisChild);


    SpecimenImage img = null;
    boolean isSpecimen = "specimen".equals(thisChild.getRank());
    boolean isDefault = false;
    Specimen specChild = null;
    if (isSpecimen) {
      specChild = (Specimen) thisChild;
      isDefault = specChild.getCode().equals(maleSpecimen)
		  || specChild.getCode().equals(workerSpecimen) 
		  || specChild.getCode().equals(queenSpecimen); 

       //A.log("taxonChildImageSet.jsp isSpecimen:" + isSpecimen + " isDefault:" + isDefault);		  
    }

    String imageRoot = "";
    String picture = "";
    String code = "";  
    String thumb1_is = thumb_choice + "1";
    String thumb_is = thumb_choice;
    String all = new String();
    all = "a";

    Hashtable childImages = thisChild.getImages();

    if (childImages == null) {
      //A.log("taxonChildImageSet.jsp no images for thisChild:" + thisChild);
      // sculptinodis is in this list. Should have fetched images even though no headshot.
    } else {
        if (!(childImages.size() > 0)) {
          //A.log("taxonChildImageSet.jsp childImages.size() ! > 0 for thisChild:" + thisChild);
        } else {
        
          if (thisChild.getTaxonName().contains("hova radamae")) A.log("taxonChildImages.jsp hova childImages:" + childImages);
          if (thisChild.getTaxonName().contains("radamae")) A.log("taxonChildImages.jsp hova radamae childImages:" + childImages);
        
          String overviewParams = "";
          if (overview != null) overviewParams = "&" + overview.getParams();

          code = ((SpecimenImage) childImages.elements().nextElement()).getCode();
          //A.log("taxonChildImageSet.jsp code:" + code );                    
		  //A.log("taxonChildImageSet.jsp code:" + code + " all:" + all + " thumbChoice:" + thumb_choice);
          if (thumb_is.equals(all)) {
            //A.log("taxonChildImageSet.jsp 4");

            position = 0; // reset for correct thumb layout when > 4 specimens
            ++imgCount;
            if (isSpecimen) {
                imageRoot = specChild.getName();
%>
<div class="data_checkbox" style="float:left; margin-top:2px; display:none;"><input type="checkbox" name="chosen" value="<%= thisChild.getName() %>"></div>
<div class="data_overlay"><a href="<%= AntwebProps.getDomainApp() %>/specimenImages.do?name=<%= code %><%= overviewParams %>"><%= thisChild.getPrettyName() %></a>
<%
                if (specChild.getTypeStatus() != null) { %>
<img style="margin-top:3px;" src="image/has_type_status.png" title="<%= (new Formatter()).capitalizeFirstLetter(specChild.getTypeStatus()) %>">  
             <% }

                if (isDefault) {
                  // A.log("taxonChildImageSet.jsp rank:" + thisChild.getRank() + " defaultSpecimen:" + DefaultSpecimen);
                  String caste = specChild.getCaste();
%>
<img style="margin-top:3px;" src="image/<%= Caste.getPicImg(caste) %>" height="14" title="<%= "Set as default " + caste + " for " + specChild.getTaxonName() %>">
             <% } %>
</div>
         <% } else {
              // ! (isSpecimen)
              imageRoot = code;
              if (thisChild.getIsFossil()) dagger = "&dagger;"; else dagger = "";
%>
<div class="ratio_name"><% if (thisChild.getIsValid()) { %><span class="is_valid"></span><% } %><a href="<%= AntwebProps.getDomainApp() %>/images.do?<%= thisChild.getBrowserParams() %>"><%= thisChild.getPrettyName() %></a></div>
<%
            } %>
<div class="ratio_has_images">
<%
//A.log("taxonChildImageSet.jsp hasImages:" + thisChild.getHasImages());

            if (thisChild.getHasImages()) {
                int titleStr = 0;
                // getHasImagesCount is not calculated for description.do.  Do not include.
                if (thisChild.getHasImagesCount() > 0) {
                    titleStr = thisChild.getHasImagesCount();
                    A.log("taxonChildImageSet.jsp titleStr:" + titleStr);
                } %>
                <%= titleStr %> Images
         <% } else { %>
                No Images
         <% } %>
</div>

<div class="data_overlay">            
<%          
          if (specChild != null) {
            //A.log("taxonChildImageSet.jsp caste:" + specChild.getCaste() + " accessGroup:" + accessGroup);
            String caste = specChild.getCaste();
            String subcaste = specChild.getSubcaste();
            if (LoginMgr.isCurator(request) && isSpecimen) { 
			  String pickerUrl = AntwebProps.getDomainApp() + "/defaultSpecimen.do?taxonName=" + thisChild.getTaxonName() 
				+ "&caste=" + caste + "&specimenCode=" + thisChild.getCode();

              if (isDefault) {
                  pickerUrl += "&command=unpost";
				  if (thisChild.getRank().equals("specimen") && HttpUtil.getTarget(request).contains("images.do")) { %>
					<a href="<%= pickerUrl %>" target=new>Unpick</a>
               <% }
              } else {
  	  	 	      // if not already the selected default                
                  pickerUrl += "&command=post";
				  if (thisChild.getRank().equals("specimen") && HttpUtil.getTarget(request).contains("images.do")) { %>
					<a href="<%= pickerUrl %>" target=new>Pick</a>
			   <% }
              }
            } %>
            &nbsp;&nbsp;<b><%= specChild.getCasteStr() %></b> 
       <%	 }
            %>
</div>

<div class="clear"></div>

<%
            for (SpecimenImage specimenImage : thisChild.getImagesSorted("h|h1,p|p1,d|d1,l|l1,*",true)) {

              //A.log("taxonChildImageSet.jsp code:" +  specImage.getCode() + " number:" + specImage.getNumber()
              //    + " shot:" + specImage.getShot() + " index:" + index + " position:" + position);

              /* Dec 2014.  We were having troubles with 3 blank images showing up in the first three spots of:
                 http://localhost/antweb//images.do?genus=amblyopone&species=pluto&rank=species&project=worldants
                 note: be sure Browse Images By selector is set to All.
                 We probably could have changed the getImagesSorted() or getImages() to not return 
                 images with number = 0 or shot == "null".  Notice the quotes around "null" - that is a string.
                 Not sure where these null number records are coming from.  Seem to be in addition to result set records.
              */
              boolean isGoodShot = !( (specimenImage.getNumber() == 0) || (specimenImage.getShot() == null || "null".equals(specimenImage.getShot())) );
              if (isGoodShot) {
                   // A.log("taxonChildImageSet.jsp image:" + specImage.getCode());
                   // continue  // continue does not seem to work with logic:iterate;

                ++index;
                ++position;
                if (position == 5) {
                    position = 1;
                }
                
                if (!"none".equals(specimenImage.getThumbview())) {                
                    String shotType = specimenImage.getShot();
                    String shotNumber = (new Integer(specimenImage.getNumber())).toString();
%>
<div class="slide medium <% if (position == first) { %> first<% } %><% if (position == fourth) { %> last<% } %> ratio">
    <div class="adjust"><img class="medres" src="<%= AntwebProps.getImgDomainApp() %><%= specimenImage.getThumbview() %>" loading="lazy" onclick="window.location='<%= AntwebProps.getDomainApp() %>/bigPicture.do?name=<%= imageRoot %>&shot=<%= shotType %>&number=<%= shotNumber %>';" /></div>
</div>
           <%   }
              } // isGoodShot
            } 
%>
<div class="clear"></div>

<%
          } else {    // if !thumb_is.equals(all)
              ++index;
              ++position;
              ++imgCount;
              if (position == 5) {
                  position = 1;
              }

              if (isSpecimen) {
                  img = ((org.calacademy.antweb.SpecimenImage) childImages.get(thumb1_is));
                  if (img == null) img = ((org.calacademy.antweb.SpecimenImage) childImages.get(thumb_choice + "1"));  // hack
                  if (img == null) img = ((org.calacademy.antweb.SpecimenImage) childImages.get(thumb_choice + "2"));  // hack
                  if (img == null) img = ((org.calacademy.antweb.SpecimenImage) childImages.get("p"));
              } else {
                  img = ((org.calacademy.antweb.SpecimenImage) childImages.get(thumb_is));
                  if (img == null) img = ((org.calacademy.antweb.SpecimenImage) childImages.get(thumb_choice + "1"));  // hack
                  if (img == null) img = ((org.calacademy.antweb.SpecimenImage) childImages.get(thumb_choice + "2"));  // hack
              }
              
              if (img == null) {
                  // Can happen for ants which don't have a p1, if looking at head shots.
                  //A.log("taxonChildImageSet.jsp case where img == null. Specimen:" + isSpecimen + " Taxon:" + thisChild.getTaxonName() 
                  //  + " position:" + position + " imgCount:" + imgCount);
                  --position;  // If the image doesn't have an image, counter the counter incrementation.
                  --imgCount;  // hack to figure out the count after the fact
                  //A.log("Subtracted 1 from imageCount:" + imgCount);
              } else {             
                  //A.log("taxonChildImageSet.jsp case where img != null.  Taxon:" + thisChild.getTaxonName() + " pos:" + position);

                  if (isSpecimen) {
                      imageRoot = thisChild.getName();

                      //A.log("taxonChildImageSet.jsp code:" + code + " thisChild:" + thisChild);
%>

                <div class="slide medium<% if (position == first) { %> first<% } %><% if (position == fourth) { %> last<% } %>" style="background-image: url('<%= AntwebProps.getImgDomainApp() %><%= img.getThumbview() %>');" id="<%= index %>">
                <div class="hover medium" onclick="window.location='<%= AntwebProps.getDomainApp() %>/specimenImages.do?name=<%= thisChild.getCode() %><%= overviewParams %>';"></div>
                <div class="top_gradient medium"></div>
                <div class="name"><a href="<%= AntwebProps.getDomainApp() %>/specimenImages.do?name=<%= thisChild.getCode() %><%= overviewParams %>"><%= thisChild.getPrettyName() %></a></div>
<%
                  } else {
                    imageRoot = code;
                    if (thisChild.getIsFossil()) dagger = "&dagger;"; else dagger = "";
%>
                <div class="slide medium<% if (position == first) { %> first<% } %><% if (position == fourth) { %> last<% } %>" style="background-image: url('<%= AntwebProps.getImgDomainApp() %><%= img.getThumbview() %>');" id="<%= index %>">

<% // AntwebUtil.log("DEBUG params:" + thisChild.getBrowserParams() + " string:" + thisChild.toString() + " class:" + thisChild.getClass()); %>

                <div class="hover medium" onclick="window.location='images.do?<%= thisChild.getBrowserParams() %>';"></div>
                <div class="top_gradient medium"></div>
                <div class="name"><% if (thisChild.getIsValid()) { %><span class="is_valid"></span><% } %><a href="images.do?<%= thisChild.getBrowserParams() %>"><%= thisChild.getPrettyName() %></a></div>
<%                } %>
    <div class="ratio_icon"><a href="#" onclick="showRatioOverlay('r<%= index %>','s<%= index %>','n<%= index %>'); return false;"><img src="image/ratio_slideshow.png" title="Click to see additional images"></a></div>
        <div class="clear"></div>
        <div class="lower_data medium">        
        <div class="lower_gradient medium"></div>
        <div class="data_left">
<% 
                  if (isSpecimen) {
                    if ((specChild.getTypeStatus() != null)) { 
                      if (!"".equals(specChild.getTypeStatus())) {
                        //A.log("taxonChildImageSet.jsp specChild:" + specChild.getTypeStatus());
%> <img style="margin-top:3px;" src="image/has_type_status.png" title="<%= (new Formatter()).capitalizeFirstLetter(specChild.getTypeStatus()) %>"> <% 
                      }
                    } 
                  }
                  if (isSpecimen) {
                    if (isDefault) {
                    // A.log("taxonChildImageSet.jsp rank:" + thisChild.getRank() + " defaultSpecimen:" + defaultSpecimen);
                      if (LoginMgr.isCurator(request)) {
%> <img style="margin-top:3px;" src="image/default.png" height="14" title="<%= "Set as default for " + thisChild.getTaxonName() %>"> <%
                      }
                    }
                  }               
                  
				 if (!isSpecimen) { %>
						<span id="next_in_taxon"><%= thisChild.getTaxonSet().getNextSubtaxon(1) %></span>
			  <% }
                 String imageCountStr = thisChild.getTaxonSet().getImageCountStr();
                 if  ("No Images".equals(imageCountStr)) {
                   if (thisChild.getImageCount() > 0) {
                     imageCountStr = thisChild.getImageCount() + " Images";
                   }                 
                 }
                 // was: thisChild.getTaxonSet().getImageCountStr()%>   
            <span style="float:right; text-align:right;"><%= imageCountStr %></span>

        </div>

        <div class="data_checkbox" style="display:none;"><input type="checkbox" name="chosen" value="<%= thisChild.getName() %>"></div>
        <div class="clear"></div>
    </div>
    <div class="ratio_overlay<% if (position == first) { %> first<% } %><% if (position == fourth) { %> last<% } %>" id="r<%= index %>" onmouseover="runSlideshow('n<%= index %>');" onmouseleave="hideRatioOverlay('r<%= index %>','s<%= index %>','n<%= index %>');">
        <div id="n<%= index %>" class="slideshow_nav left" style="display:none;">
            <a href="#"></a>
            <a href="#"></a>
            <a href="#"></a>
            <a href="#"></a>
        </div>
        <div class="clear"></div>
        <div id="s<%= index %>" class="slideshow">

<%          int headShotCount = 0;
            for (SpecimenImage specimenImage : thisChild.getImagesSorted("h|h1,p|p1,d|d1,l|l1,*",true)) {
              if (!"none".equals(specimenImage.getThumbview())) {
                if (specimenImage.getCode() == null) { 
                  // A.log("taxonChildImageSet() code is null for thisChild:" + thisChild + " specimenImage:" + specimenImage);
                } else {
                  //  if (specimenImage.getCode().contains("0188199")) A.log("taxonChildImageSet() image:" + specimenImage);
                  ++ headShotCount;
                  String shotType = specimenImage.getShot();
                  String shotNumber = (new Integer(specimenImage.getNumber())).toString();
                  %>                    
                    <div class="ratio_adjust">
                        <img class="ratio_slide" src="<%= AntwebProps.getImgDomainApp() %><%= specimenImage.getThumbview() %>" onclick="window.location='<%= AntwebProps.getDomainApp() %>/bigPicture.do?name=<%= imageRoot %>&shot=<%= shotType %>&number=<%= shotNumber %>';">
                    </div>
<%
                }
              }
            }   
            //if (headShotCount < 4) A.log("taxonChildImageSet() head shot displayed:" + headShotCount);
%> 
        </div>
        <div class="clear"></div>
    </div>
</div>
<%            }  // end img == null check 
          } // end toggle for thumbs or all
        }
    }
%>
