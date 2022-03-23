package org.calacademy.antweb.curate;

import java.io.*;
import java.sql.*;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.struts.action.*;

import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class EditHomePageAction extends Action {

    private static Log s_log = LogFactory.getLog(EditHomePageAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		// Extract attributes we will need
		Locale locale = getLocale(request);
		HttpSession session = request.getSession();
		
		HomePageForm theForm = (HomePageForm) form;

		Connection connection = null;
		try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "EditHomePageAction");

			connection.setAutoCommit(true);
			String theQuery = "select * from homepage";
			HashMap contents = new HashMap();
			Statement stmt = connection.createStatement();
			ResultSet rset = stmt.executeQuery(theQuery);
			while (rset.next()) {
				contents.put(rset.getString("content_type"), Formatter.dequote(rset.getString("content")));
			}
			setFormElements(theForm, contents);
			          
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "EditHomePageAction");
        }
        
		return (mapping.findForward("success"));
	}
	
	private void setFormElements(HomePageForm form, HashMap contents) {

		Iterator iter = contents.keySet().iterator();
		String key, value, method;
		Formatter format = new Formatter();

		Field field;
		Class thisClass;
		try {
			Class stringClass = Class.forName("java.lang.String");
			Class params[] = {stringClass};
			
			Method thisMethod;
		
			while (iter.hasNext()) {
				key = (String) iter.next();
				value = (String) contents.get(key);
				method = "set" + format.capitalizeFirstLetter(key);
				thisClass = Class.forName("org.calacademy.antweb.curate.HomePageForm");
				thisMethod = thisClass.getDeclaredMethod(method, params);
				Object[] paramsObj = {contents.get(key)};
				thisMethod.invoke(form, paramsObj);
			}
		} catch (SecurityException e) {
			AntwebUtil.logStackTrace(e);
		} catch (IllegalArgumentException e) {
			AntwebUtil.logStackTrace(e);
		} catch (ClassNotFoundException e) {
			AntwebUtil.logStackTrace(e);
		} catch (NoSuchMethodException e) {
			AntwebUtil.logStackTrace(e);
		} catch (IllegalAccessException e) {
			AntwebUtil.logStackTrace(e);
		} catch (InvocationTargetException e) {
			AntwebUtil.logStackTrace(e);
		}

	}
}	
	

