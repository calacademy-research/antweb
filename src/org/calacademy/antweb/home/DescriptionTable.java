package org.calacademy.antweb.home;

import java.util.Hashtable;

/**
 * The page-text fields of a taxon, specimen or overview page (title to HTML), as read from
 * description_edit or object_edit. isLoadFailed() is true when the database read failed: the
 * table is then empty, and pages say the text could not be loaded instead of showing empty fields
 * (and offer no editor, so an empty box can never be saved over the stored text).
 */
public class DescriptionTable extends Hashtable<String, String> {

    private static final long serialVersionUID = 1L;

    private boolean loadFailed = false;

    public boolean isLoadFailed() {
        return loadFailed;
    }

    public void setLoadFailed(boolean loadFailed) {
        this.loadFailed = loadFailed;
    }

    /** An empty table marked as failed. */
    public static DescriptionTable failed() {
        DescriptionTable t = new DescriptionTable();
        t.setLoadFailed(true);
        return t;
    }
}
