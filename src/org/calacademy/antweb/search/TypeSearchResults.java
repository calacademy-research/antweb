package org.calacademy.antweb.search;

import java.io.*;

/** The bean that handles the results information
 * @author thau
 * @version 0.1
*/
public final class TypeSearchResults
	extends GenericSearchResults
	implements Serializable {

	public void setResults() { // throws Exception
		removeNullCodes();
		super.setResults();
	}
}