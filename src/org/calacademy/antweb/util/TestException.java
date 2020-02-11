package org.calacademy.antweb.util;

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