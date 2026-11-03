package org.calacademy.antweb;

/**
 * Thrown by curate/speciesList/SpeciesListValidator when a species list file cannot be read: empty file, headers
 * that are neither genus and species nor taxon_name, more than 50,000 rows. ValidateSpeciesListAction catches it and
 * shows the message.
 */
public class ValidationParseException extends Exception {
    public ValidationParseException(String message) {
        super(message);
    }
}
