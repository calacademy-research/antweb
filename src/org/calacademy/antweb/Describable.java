package org.calacademy.antweb;

import java.util.Hashtable;

/**
 * An object with page text a curator can edit: getName(), setName() and getDescription() (field title to text).
 * Implemented by Taxon (and so Specimen and Homonym) and Overview (places). Used by DescriptionAction
 * (saveDescriptionEdit, prepareDescriptionEdit), DescEditSafety, DescHistory and home/DescEditDb to key and read the
 * text. Docs: docs/data-model.md (description_edit, description_hist).
 */
public interface Describable {

  String getName();
  void setName(String name);
  Hashtable<String, String> getDescription();

}
