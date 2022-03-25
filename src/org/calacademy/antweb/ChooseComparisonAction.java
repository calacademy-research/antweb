package org.calacademy.antweb;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ChooseComparisonAction extends Action {

    private static final Log s_log = LogFactory.getLog(ChooseComparisonAction.class);
    
    public ActionForward execute(
        ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
        String[] chosen = ((ChooseComparisonForm) form).getChosen();

        if (chosen != null) {

            ArrayList<SpecimenImage> theImages = new ArrayList<>();
            for (String s : chosen) {
                theImages.add(createSpecimenImage(s));
            }

            if ("request".equals(mapping.getScope())) {
                request.setAttribute("imageCollection", theImages);
            } else {
                session.setAttribute("imageCollection", theImages.toArray());
            }

            // Set a transactional control token to prevent double posting
            saveToken(request);

            return mapping.findForward("success");
        } else {
            return mapping.findForward("failure");
        }
    }

    private SpecimenImage createSpecimenImage(String fullImagePath) {

        SpecimenImage mySpecimenImage = new SpecimenImage();
        try {
            RE slash = new RE("/");
            RE imageNamePattern = new RE("(.*?).jpg");
            RE underscore = new RE("_");

            String imageName = null;
            String[] stuff = slash.split(fullImagePath);

            if (imageNamePattern.match(stuff[stuff.length - 1])) {
                imageName = imageNamePattern.getParen(1);
                String[] underscores = underscore.split(imageName);
                StringBuffer sb = new StringBuffer();
                for (int loop = 0; loop < underscores.length - 1; loop++) {
                    sb.append(underscores[loop] + "_");
                }
                sb.append("med");
                imageName = sb.toString();

                String newName =
                    "images/"
                        + stuff[stuff.length - 2]
                        + "/"
                        + imageName
                        + ".jpg";
                // Tried hard to find this to test but ran out of time...
                // Not sure it is being used.
                //A.log("createSpecimenImage() newName:" + newName);
                //mySpecimenImage.setThumbview(newName);
            }
        } catch (RESyntaxException e) {
            s_log.error("createSpecimenImage() e:" + e);
        }

        return mySpecimenImage;
    }
}
