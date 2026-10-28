package org.calacademy.antweb.upload;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.calacademy.antweb.Status;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.home.HomonymDb;
import org.calacademy.antweb.home.TaxonDb;
import org.calacademy.antweb.util.TaxonMgr;

/**
 * Which name a specimen is filed under, given the name in the curator's file (the uploaded name).
 *
 * This is the decision the specimen upload has always made in
 * SpecimenUploadProcess.setStatusAndCurrentValidName(), taken out of it (2026-10) so that the
 * nightly name update (SpecimenNameUpdate) uses exactly the same rule, and so that the rule can be
 * tested without a database (Lookup). The upload keeps its side effects (report lines, the homonym
 * placeholder taxon, the name columns from makeSpecimenUseTaxon); this class only decides.
 *
 *   indet name (e.g. "(indet)")         -> kept, status indetermined
 *   morphospecies name (e.g. "mg01")    -> kept, status morphotaxon
 *   no taxon row, or status unrecognized -> kept, status unrecognized
 *   status in Status.USES_CURRENT_VALID_NAME (synonym, obsolete combination, ...):
 *       no current valid name            -> kept, status as in AntCat
 *       current valid taxon exists       -> REPLACE: filed under it, status valid, uploaded name kept
 *       current valid name is a homonym  -> filed under a placeholder taxon (the upload creates it)
 *       current valid name not in AntWeb -> kept under the uploaded name, status as in AntCat, listed in the
 *                                           upload report (Brian Fisher, 2026-10-28; was: filed under the
 *                                           current valid name anyway, a name with no taxon row)
 *   any other status (valid, unavailable, homonym, ...) -> kept, status as in AntCat
 *
 * See docs/flows/specimen-upload.md (section 4) and docs/flows/antcat-to-antweb.md.
 */
public final class CurrentValidNameRule {

    private CurrentValidNameRule() {}

    public enum Kind {
        INDET, MORPHO, NOT_IN_ANTCAT, KEEP, NO_CURRENT_VALID_NAME,
        REPLACE, TARGET_IS_HOMONYM, TARGET_MISSING, TARGET_SAME
    }

    /** What the rule needs from a taxon row. */
    public static final class TaxonRow {
        public final String status;
        public final String currentValidName;
        public TaxonRow(String status, String currentValidName) {
            this.status = status;
            this.currentValidName = currentValidName;
        }
    }

    /** The three database questions the rule asks. */
    public interface Lookup {
        /** The taxon row of this name, or null when there is none. */
        TaxonRow getTaxon(String taxonName) throws SQLException;
        /** The name of the taxon row of this current valid name (as TaxonDb.getCurrentValidTaxonName), or null. */
        String getCurrentValidTaxonName(String currentValidName) throws SQLException;
        /** True when the homonym table has this name (the upload then files the specimen under a placeholder taxon). */
        boolean isHomonym(String currentValidName) throws SQLException;
    }

    public static final class Result {
        public final Kind kind;
        /** Status the specimen gets. */
        public final String status;
        /** Name the upload files the specimen under (specimen.taxon_name). */
        public final String taxonName;
        /** The uploaded name when the rule replaced it (specimen.original_taxon_name), otherwise null. */
        public final String originalTaxonName;
        /** REPLACE: the existing current valid taxon. Otherwise null. */
        public final String validTaxonName;
        /** Status of the uploaded name in the taxon table (null if no row). */
        public final String uploadedStatus;
        /** TARGET_MISSING: AntCat's current valid name, which has no taxon in AntWeb yet. Otherwise null. */
        public final String missingCurrentValidName;

        Result(Kind kind, String status, String taxonName, String originalTaxonName, String validTaxonName, String uploadedStatus) {
            this(kind, status, taxonName, originalTaxonName, validTaxonName, uploadedStatus, null);
        }

        Result(Kind kind, String status, String taxonName, String originalTaxonName, String validTaxonName, String uploadedStatus, String missingCurrentValidName) {
            this.missingCurrentValidName = missingCurrentValidName;
            this.kind = kind;
            this.status = status;
            this.taxonName = taxonName;
            this.originalTaxonName = originalTaxonName;
            this.validTaxonName = validTaxonName;
            this.uploadedStatus = uploadedStatus;
        }

        /** True when the upload replaces the uploaded name (and skips the uploaded name's own taxon entry). */
        public boolean replacesName() {
            return originalTaxonName != null;
        }

        public String toString() {
            return kind + " " + taxonName + " (" + status + ")" + (originalTaxonName != null ? " uploaded:" + originalTaxonName : "");
        }
    }

    public static Result decide(String taxonName, Lookup lookup) throws SQLException {
        if (Taxon.isIndet(taxonName)) {
            return new Result(Kind.INDET, Status.INDETERMINED, taxonName, null, null, null);
        }
        if (Taxon.isMorpho(taxonName)) {
            return new Result(Kind.MORPHO, Status.MORPHOTAXON, taxonName, null, null, null);
        }
        TaxonRow taxon = lookup.getTaxon(taxonName);
        String status = taxon == null ? null : taxon.status;
        if (taxon == null || status == null || Status.UNRECOGNIZED.equals(status)) {
            return new Result(Kind.NOT_IN_ANTCAT, Status.UNRECOGNIZED, taxonName, null, null, status);
        }
        if (!Status.usesCurrentValidName(status)) {
            return new Result(Kind.KEEP, status, taxonName, null, null, status);
        }
        String currentValidName = taxon.currentValidName;
        if (currentValidName == null) {
            return new Result(Kind.NO_CURRENT_VALID_NAME, status, taxonName, null, null, status);
        }
        String currentValidTaxonName = lookup.getCurrentValidTaxonName(currentValidName);
        if (currentValidTaxonName == null) {
            if (lookup.isHomonym(currentValidName)) {
                return new Result(Kind.TARGET_IS_HOMONYM, Status.UNRECOGNIZED, currentValidName, taxonName, null, status);
            }
            // Brian Fisher, 2026-10-28: the specimen stays under its uploaded name until AntWeb has the taxon.
            return new Result(Kind.TARGET_MISSING, status, taxonName, null, null, status, currentValidName);
        }
        if (currentValidTaxonName.equals(taxonName)) {
            return new Result(Kind.TARGET_SAME, status, currentValidName, taxonName, null, status);
        }
        return new Result(Kind.REPLACE, Status.VALID, currentValidName, taxonName, currentValidTaxonName, status);
    }

    /** The lookups of the specimen upload (unchanged from before 2026-10). */
    public static Lookup uploadLookup(Connection connection) {
        return new Lookup() {
            public TaxonRow getTaxon(String taxonName) throws SQLException {
                Taxon taxon = TaxonMgr.isUseRefreshing() ? TaxonMgr.getTaxon(taxonName) : new TaxonDb(connection).getTaxon(taxonName);
                return taxon == null ? null : new TaxonRow(taxon.getStatus(), taxon.getCurrentValidName());
            }
            public String getCurrentValidTaxonName(String currentValidName) {
                return TaxonDb.getCurrentValidTaxonName(connection, currentValidName);
            }
            public boolean isHomonym(String currentValidName) {
                return new HomonymDb(connection).getHomonym(currentValidName) != null;
            }
        };
    }

    /**
     * The same three questions as plain queries, for the nightly name update (and its tests): no
     * caches, nothing but taxon.status, taxon.current_valid_name and the homonym table's name. The
     * answers are those of uploadLookup() (TaxonDb.getTaxon refuses names with a quote; so does this).
     */
    public static Lookup plainLookup(Connection connection) {
        return new Lookup() {
            public TaxonRow getTaxon(String taxonName) throws SQLException {
                if (taxonName == null || taxonName.contains("'")) return null;
                try (PreparedStatement stmt = connection.prepareStatement(
                        "select status, current_valid_name from taxon where taxon_name = ?")) {
                    stmt.setString(1, taxonName);
                    try (ResultSet rset = stmt.executeQuery()) {
                        if (!rset.next()) return null;
                        return new TaxonRow(rset.getString(1), rset.getString(2));
                    }
                }
            }
            public String getCurrentValidTaxonName(String currentValidName) throws SQLException {
                if (currentValidName == null) return null;
                try (PreparedStatement stmt = connection.prepareStatement("select taxon_name from taxon where taxon_name = ?")) {
                    stmt.setString(1, currentValidName.toLowerCase());
                    try (ResultSet rset = stmt.executeQuery()) {
                        return rset.next() ? rset.getString(1) : null;
                    }
                }
            }
            public boolean isHomonym(String currentValidName) throws SQLException {
                try (PreparedStatement stmt = connection.prepareStatement("select 1 from homonym where taxon_name = ? limit 1")) {
                    stmt.setString(1, currentValidName);
                    try (ResultSet rset = stmt.executeQuery()) {
                        return rset.next();
                    }
                }
            }
        };
    }
}
