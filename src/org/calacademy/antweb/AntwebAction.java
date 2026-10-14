package org.calacademy.antweb;

import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.apache.struts.action.Action;
import org.calacademy.antweb.util.AntwebDataSources;

/**
 * Base class for AntWeb's Struts actions.
 *
 * Keeps getDataSource(request, key), which Struts 1.1's Action provided and
 * later Struts 1 versions removed, so the actions did not need to change.
 */
public abstract class AntwebAction extends Action {

    protected DataSource getDataSource(HttpServletRequest request, String key) {
        return AntwebDataSources.get(request.getServletContext(), key);
    }
}
