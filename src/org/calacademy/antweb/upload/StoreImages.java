package org.calacademy.antweb.upload;

import javax.servlet.http.*;
import javax.servlet.*;
import java.util.*;
import java.io.*;
import java.util.stream.*;
import java.nio.file.*;

public class StoreImages extends HttpServlet {

@Override
protected void doPost(HttpServletRequest request, HttpServletResponse response) 
    throws ServletException, IOException {

        String saveDir=""; //this will be a folder inside
        //directory
        PrintWriter out =response.getWriter();

        //if you want u can give this at run time
		saveDir="6022"; //in my case folder name is 6022 
		//you can alse set this at dynamic

        int flag = 0;

        //now set the path
        //this is the path where my images are stored
        //now u can see the code
		String savepath = "K:/imageupload" + File.separator + saveDir;   

		File file = new File(savepath);

		if(!file.exists()) {
			file.mkdir();
		}

		String filename="";

		List<Part> fileParts = request.getParts().stream().
		filter(part->"file".equals(part.getName())).collect(Collectors.toList());

		for(Part filePart: fileParts){
			filename=Paths.get(filePart.getSubmittedFileName()).
			getFileName().toString();

			filePart.write(savepath+File.separator+filename);
			flag=1;
		}
		if(flag==1) {
			out.println("success");
		} else {
			out.println("try again");
		}

        //now save this and run the project
    }
}