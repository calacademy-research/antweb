package org.calacademy.antweb.upload;

import java.io.File;

import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;

import java.util.*;

import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.disk.*;
import org.apache.commons.fileupload.servlet.*;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileUploadException;

/**
 * Servlet implementation class UploadFileServlet. DEPRECATED. NOT USED.
 */
public class UploadFileServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public UploadFileServlet() {
        super();
        // TODO Auto-generated constructor stub
    }
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // TODO Auto-generated method stub
        response.sendRedirect("../jsp/ErrorPage.jsp");
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // TODO Auto-generated method stub

		PrintWriter out = response.getWriter();
		HttpSession httpSession = request.getSession();
		String filePathUpload = (String) httpSession.getAttribute("path")!=null ? httpSession.getAttribute("path").toString() : "" ;

		String path1 = filePathUpload;
		String filename = null;
		File path = null;
		FileItem item = null;
        String description = null;

		boolean isMultipart = ServletFileUpload.isMultipartContent(request);

		if (isMultipart) {
			FileItemFactory factory = new DiskFileItemFactory();
			ServletFileUpload upload = new ServletFileUpload(factory);
			String fieldName = "";
			try {
				List items = upload.parseRequest(request);
				Iterator iterator = items.iterator();
				while (iterator.hasNext()) {
					item = (FileItem) iterator.next();

					if (fieldName.equals("description")) {
						description = item.getString();
					}
//					}
					if (!item.isFormField()) {
						filename = item.getName();
						path = new File(path1 + File.separator);
						if (!path.exists()) {
							boolean status = path.mkdirs();
						}
						/* START OF CODE FRO PRIVILEDGE*/

						File uploadedFile = new File(path + filename);  // for copy file
						item.write(uploadedFile);
//					}
					} else {
						filename = item.getName();
					}

				} // END OF WHILE 
				response.sendRedirect("welcome.jsp");
			} catch (FileUploadException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			} 
		}   
    }

}