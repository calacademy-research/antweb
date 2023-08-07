package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;

/*
Sister servlet for the Struts action QueryAction accessible via query.do.
  http://localhost/antweb/query.do?name=speciesListWithRangeData&param=Comoros

QueryFileServlet is accessed by servlet request queryFile:
  http://localhost/antweb/queryFile?name=speciesListWithRangeData&param=Comoros

To make this work, add a section to the web.xml and modify as necessary
/etc/apache2/sites-available/default-ssl.conf
  (don't forget to restart apache: sudo systemctl restart apache2)

*/
public class QueryFileServlet extends HttpServlet {

    private static final Log s_log = LogFactory.getLog(QueryFileServlet.class);

    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        // reads input file from an absolute path

/*
        String filePath = "E:/Test/Download/MYPIC.JPG";
        File downloadFile = new File(filePath);
        FileInputStream inStream = new FileInputStream(downloadFile);
*/

        String name = request.getParameter("name");
        String param = request.getParameter("param");

        String message = Check.init(Check.GEOLOCALE);
        if (message != null) {
            s_log.warn("doGet() name:" + name + " param:" + param + " message:" + message);
        } else {
            NamedQuery namedQuery = null;
            Connection connection = null;
            try {
                DataSource ds = DBUtilSimple.getDataSource();
                connection = ds.getConnection();

                namedQuery = QueryManager.runQueryWithParam(name, param, connection);
            } catch (SQLException e) {
                s_log.error("init() e:" + e);
    //        } catch (java.beans.PropertyVetoException e) {
    //            s_log.error("init() e:" + e);
            } finally {
                try {
                    connection.close();
                } catch (SQLException e) {
                    s_log.warn("doGet() e:" + e);
                }
            }

            message = namedQuery.getResult();

            // if you want to use a relative path to context root:
            //String relativePath = getServletContext().getRealPath("");
            //s_log.warn("relativePath = " + relativePath);

            // obtains ServletContext
            //ServletContext context = getServletContext();

            String mimeType = mimeType = "application/octet-stream";
            /*
            // gets MIME type of the file
            String mimeType = context.getMimeType(filePath);
            if (mimeType == null) {
                // set to binary type if MIME mapping not found
                mimeType = "application/octet-stream";
            }
            //s_log.warn("MIME type: " + mimeType);
            */

            // modifies response
            response.setContentType(mimeType);
            response.setContentLength(message.length());
            //response.setContentLength((int) downloadFile.length());

            // forces download
            String headerKey = "Content-Disposition";
            //String headerValue = String.format("attachment; filename=\"%s\"", downloadFile.getName());
            String headerValue = String.format("attachment; filename=\"%s\"", namedQuery.getFileName());
            response.setHeader(headerKey, headerValue);

            s_log.warn("doGet() name:" + name + " param:" + param + " results:" + namedQuery.getRowCount() + " in " + namedQuery.getTimePassedNote());
        }

        // obtains response's output stream
        OutputStream outStream = response.getOutputStream();

/*
        byte[] buffer = new byte[4096];
        int bytesRead = -1;
         
        while ((bytesRead = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, bytesRead);
        }
 */

        outStream.write(message.getBytes());

        //inStream.close();
        outStream.close();
    }
}