package org.calacademy.antweb.util;

/**
 * Exception a specimen upload may throw to stop early on a developer's test condition
 * (SpecimenUpload.importSpecimens): isCommit() says whether the rows loaded so far are committed or rolled
 * back. The only throw is behind "AntwebProps.isDevMode() && false", so it never happens; the catch blocks and
 * throws clauses (SpecimenUploader, TaxonWorksUploader, Uploader) remain. Tests:
 * tests/unit/test_upload_nightly_safety.py (catch order).
 */
public class TestException extends Exception {

  private boolean commit = false;

  public TestException(boolean commit) {
    setCommit(commit);  
  }


  public void setCommit(boolean commit) {
    this.commit = commit;
  }
  
  public boolean isCommit() {
    return this.commit;
  }
}