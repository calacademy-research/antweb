/**
 * --------------------------------------------------------------------------
 * Copyright (c) 2002, Sebastian Delmont <sdelmont@zonageek.com> 
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions  of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * - Neither the name of Sebastian Delmont nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * ---------------------------------------------------------------------------
 */

package com.zonageek.jpeg;

import java.util.Vector;
import java.util.HashMap;
import java.util.Iterator;

//=============================================================================
//=============================================================================
public class AdobeBlock extends JpegBlock {
  public final static int UNKNOWN_VALUE = -1;
  
  protected Vector adobeData;
  protected HashMap iptcData;
  
  //===========================================================================
  public AdobeBlock() {
    this(null);
  }

  //===========================================================================
  public AdobeBlock(Jpeg jpeg) {
    super(Jpeg.MARKER_APP13, jpeg);
    this.adobeData = new Vector();
    this.iptcData = new HashMap();
    
    this.dataInSync = false;
  }

  //===========================================================================
  public AdobeBlock(byte[] data, Jpeg jpeg) throws JpegException {
    super(Jpeg.MARKER_APP13, data, jpeg);
  }
  
  //===========================================================================
  public HashMap getInformation() {
    HashMap info = super.getInformation();
    
    info.put("Jpeg Block", "ADOBE (APP14)");
    //info.remove("Jpeg Block Length");

//      Iterator keys = this.adobeData.keySet().iterator();
//      while (keys.hasNext()) {
//        Object key = keys.next();
//        info.put(key.toString(), this.adobeData.get(key));
//      }

    Iterator keys = this.iptcData.keySet().iterator();
    while (keys.hasNext()) {
      Object key = keys.next();
      info.put(key.toString(), this.iptcData.get(key));
    }
    
    if (info.containsKey("JFIFThumbnail")) {
      info.put("JFIFThumbnail", "Binary Data");
    }
    if (info.containsKey("MakerNote")) {
      info.put("MakerNote", "Binary Data");
    }

    return info;
  }

  //===========================================================================
  public HashMap getIptcFields() {
    return this.iptcData;
  }
  
  //===========================================================================
  public Object getIptcField(String name) {
    try {
      return this.iptcData.get(name);
    }
    catch (Exception ex) {
      return null;
    }
  }
  
  //===========================================================================
  public String getIptcString(String name) {
    try {
      Object value = this.iptcData.get(name);
      return value.toString();
    }
    catch (Exception ex) {
      return null;
    }
  }

  //===========================================================================
  public Vector getIptcVector(String name) {
    try {
      Object value = this.iptcData.get(name);
      if (value instanceof Vector)
        return (Vector)value;
      
      Vector v = new Vector();
      v.add(value);
      return v;
    }
    catch (Exception ex) {
      return null;
    }
  }

  //===========================================================================
  public void removeIptcField(String name) {
    // Existing fields will be deleted
    // There is no check for valid fields
    if (this.iptcData == null)
      this.iptcData = new HashMap();
      
    this.iptcData.remove(name);
    
    this.dataInSync = false;
  }

  //===========================================================================
  public void setIptcField(String name, String value) {
    // Existing fields will be deleted
    // There is no check for valid fields
    if (this.iptcData == null)
      this.iptcData = new HashMap();
      
    this.iptcData.put(name, value);
      
    this.dataInSync = false;
  }
  
  //===========================================================================
  public void addIptcField(String name, String value) {
    // Existing fields will be deleted
    // There is no check for valid fields
    if (this.iptcData == null)
      this.iptcData = new HashMap();

    Object existing = this.iptcData.get(name);
    Vector v;
    if (existing == null) {
      this.iptcData.put(name, value);
    }
    else if (existing instanceof Vector) {
      v = (Vector)existing;
      v.add(value);
    }
    else {
      v = new Vector();
      v.add(existing);
      v.add(value);
      this.iptcData.put(name, v);
    }

    this.dataInSync = false;
  }
  
  //===========================================================================
  public Vector getAdobeFields() {
    return this.adobeData;
  }
  
  //===========================================================================
  public HashMap getAdobeField(String name) {
    HashMap map;
    String header;
    try {
      for (int i = 0; i < this.adobeData.size(); i++) {
        map = (HashMap)this.adobeData.elementAt(i);
        header = (String)map.get("header");
        if (header.equals(name))
          return map;
      }
    }
    catch (Exception ex) {
    }
    return null;
  }
  
  //===========================================================================
  public HashMap getAdobeField(int type) {
    HashMap map;
    Integer intObj;
    try {
      for (int i = 0; i < this.adobeData.size(); i++) {
        map = (HashMap)this.adobeData.elementAt(i);
        intObj = (Integer)map.get("type");
        if (intObj.intValue() == type)
          return map;
      }
    }
    catch (Exception ex) {
    }
    return null;
  }

  //===========================================================================
  public void addAdobeField(HashMap map) {
    if ((map.get("type") != null)
    && (map.get("header") != null)
    && (map.get("data") != null)) {
      this.adobeData.add(map);
    }
  }

  //===========================================================================
  public void addAdobeField(int type, String header, byte[] data) {
    HashMap map = new HashMap();
    map.put("type", new Integer(type));
    map.put("header", header);
    map.put("data", data);
    this.addAdobeField(map);
  }

  //===========================================================================
  public void removeAdobeField(HashMap map) {
    this.adobeData.remove(map);
  }

  //===========================================================================
  public void parseData() throws JpegException {
    if (this.getFixedString(0, 14).equals("Photoshop 3.0\0")) {
      if (this.adobeData == null)
        this.adobeData = new Vector();
      else
        this.adobeData.clear();
      
      int pos = 14;
  
      String eightBim;
      int type;
      int strlen;
      String header;
      int length;
      int basePos;
      
      while (pos < this.data.length) {
        eightBim = this.getFixedString(pos, 4);
        if (!eightBim.equals("8BIM"))
          throw new JpegException("Unexpected data, missing 8BIM");
        pos += 4;
  
        type = this.getShort(pos);
        pos += 2;
  
        strlen = this.getByte(pos);
        pos += 1;
        
        header = this.getFixedString(pos, strlen);
        pos += strlen + 1 - (strlen % 2);  
        // The string is padded to even length, counting the length byte itself
  
        length = this.getLong(pos);
        pos += 4;
  
        basePos = pos;
  
        switch (type) {
        case 0x0404: // Caption (IPTC Data)
          pos = this.readIPTC(pos, length);
          break;
        default:
          HashMap map = new HashMap();
          map.put("type", new Integer(type));
          map.put("header", header);
          map.put("data", this.getByteArray(pos, length));
          
          this.adobeData.add(map);
          break;
        }
  
        pos = basePos + length + (length % 2); // Even padding
      }
    }
    else {
      throw new JpegException("Not an ADOBE block");
    }
  }

  //===========================================================================
  public void createData() throws JpegException {
    this.newData();
    
    int pos = 0;
    int lengthPos;
    byte[] auxBytes;
    String auxString;
    
    this.putString(pos, "Photoshop 3.0\0");
    pos += 14;
    
    // First, lets write the IPTC Block
    if ((this.iptcData != null) && (this.iptcData.size() > 0)) {
      this.putString(pos, "8BIM");
      pos += 4;
      this.putShort(pos, 0x0404);
      pos += 2;
      this.putByte(pos, 0x07);
      pos += 1;
      this.putString(pos, "Caption");
      pos += 7;
      lengthPos = pos;
      
      pos += 4; // Leave space for the length
      
      pos = this.writeIPTC(pos);
      this.putLong(lengthPos, (pos - lengthPos - 4));
      if (((pos - lengthPos) % 2) == 1)
        pos++; // Even padding
    }
    
    // And then, any other blocks we got from the original file
    HashMap map;
    if (this.adobeData != null) {
      for (int i = 0; i < this.adobeData.size(); i++) {
        map = (HashMap)this.adobeData.elementAt(i);

        this.putString(pos, "8BIM");
        pos += 4;
        this.putShort(pos, ((Integer)(map.get("type"))).intValue());
        pos += 2;
        
        auxString = (String)(map.get("header"));
        this.putByte(pos, auxString.length());
        pos += 1;
        this.putString(pos, auxString);
        pos += auxString.length();
        if ((auxString.length() % 2) == 0)
          pos += 1; // Even padding, but counting the length byte

        auxBytes = (byte[])map.get("data");
        this.putLong(pos, auxBytes.length);
        pos += 4;
        
        this.putByteArray(pos, auxBytes);
        pos += auxBytes.length;
        if ((auxBytes.length % 2) == 1)
          pos += 1; // Even padding
      }
    }
    this.trimData();
    this.dataInSync = true;
  }
  
  //===========================================================================
  //===========================================================================
  //===========================================================================
  public int readIPTC(int pos, int totalLength) 
  throws JpegException {
    this.iptcData = new HashMap();
    
    IptcTagList tags = new IptcTagList();
    while (pos < (totalLength - 5)) {
      int aux = this.getShort(pos);
      if (aux != 0x1C02) {
        break;
      }
      pos += 2;
      
      int type = this.getByte(pos);
      pos += 1;
      int length = this.getShort(pos);
      pos += 2;
      
      String label = "";
      
      IptcTagInfo tag = tags.getTagById(type);
      if (tag != null) {
        label = tag.name;
      }
      else {
        label = "IPTC_" + JpegShortcuts.byte2hex(type);
      }

      String data = this.getFixedString(pos, length);
      pos += length;

      if (this.iptcData.containsKey(label)) {
        Object obj = this.iptcData.get(label);
        Vector v;
        if (obj instanceof Vector) {
          v = (Vector)obj;
          v.add(data);
        }
        else {
          v = new Vector();
          v.add(obj);
          v.add(data);
          this.iptcData.remove(label);
          this.iptcData.put(label, v);
        }
      }
      else {
        this.iptcData.put(label, data);
      }
    }

    return pos;
  }

  //===========================================================================
  protected int writeIPTC(int pos) throws JpegException {
    IptcTagList tags = new IptcTagList();
    Object data;
    String str;
    Vector vect;

    for (int i = 0; i < tags.getTagCount(); i++) {
      IptcTagInfo tag = tags.getTagAt(i);
      
      data = this.iptcData.get(tag.name);
      if (data != null) {
        if (data instanceof String) {
          str = (String)data;
          this.putShort(pos, 0x1C02);
          pos += 2;
          this.putByte(pos, tag.id);
          pos += 1;
          this.putShort(pos, str.length());
          pos += 2;
          this.putString(pos, str);
          pos += str.length();
        }
        else if (data instanceof Vector) {
          vect = (Vector)data;
          for (int j = 0; j < vect.size(); j++) {
            str = (String)vect.elementAt(j);
            
            this.putShort(pos, 0x1C02);
            pos += 2;
            this.putByte(pos, tag.id);
            pos += 1;
            this.putShort(pos, str.length());
            pos += 2;
            this.putString(pos, str);
            pos += str.length();
          }
        }
      }
    }
    
    return pos;
  }

//=============================================================================
//=============================================================================
}


//=============================================================================
//=============================================================================
class IptcTagInfo {
  public int id;
  public String name;
  
  public IptcTagInfo(int id, String name) {
    this.id = id;
    this.name = name;
  }
  
//=============================================================================
//=============================================================================
}

//=============================================================================
//=============================================================================
class IptcTagList {
  protected HashMap ids;
  protected HashMap names;
  protected Vector order;
  
  //===========================================================================
  public IptcTagList() {
    this.ids = new HashMap();
    this.names = new HashMap();
    this.order = new Vector();

    this.put(new IptcTagInfo(0x00, "IPTCSignature"));
    this.put(new IptcTagInfo(0x78, "Caption"));
    this.put(new IptcTagInfo(0x7A, "CaptionWriter"));
    this.put(new IptcTagInfo(0x69, "Headline"));
    this.put(new IptcTagInfo(0x28, "SpecialInstructions"));
    this.put(new IptcTagInfo(0x50, "Byline"));
    this.put(new IptcTagInfo(0x55, "BylineTitle"));
    this.put(new IptcTagInfo(0x6E, "Credit"));
    this.put(new IptcTagInfo(0x73, "Source"));
    this.put(new IptcTagInfo(0x05, "ObjectName"));
    this.put(new IptcTagInfo(0x37, "DateCreated"));
    this.put(new IptcTagInfo(0x5A, "City"));
    this.put(new IptcTagInfo(0x5F, "ProvinceState"));
    this.put(new IptcTagInfo(0x65, "CountryName"));
    this.put(new IptcTagInfo(0x67, "OriginalTransmissionReference"));
    this.put(new IptcTagInfo(0x0F, "Category"));
    this.put(new IptcTagInfo(0x14, "SuplementalCategories"));
    this.put(new IptcTagInfo(0x0A, "CopyrightFlag"));
    this.put(new IptcTagInfo(0x19, "Keywords"));
    this.put(new IptcTagInfo(0x74, "CopyrightNotice"));
  }
  
  //===========================================================================
  public void put(IptcTagInfo tag) {
    this.ids.put(new Integer(tag.id), tag);
    this.names.put(tag.name, tag);
    this.order.add(tag);
  }
  
  //===========================================================================
  public IptcTagInfo getTagByName(String name) {
    return (IptcTagInfo) this.names.get(name);
  }
  
  //===========================================================================
  public IptcTagInfo getTagById(int id) {
    return (IptcTagInfo) this.ids.get(new Integer(id));
  }

  //===========================================================================
  public int getTagCount() {
    return this.order.size();
  }
  
  //===========================================================================
  public IptcTagInfo getTagAt(int pos) {
    return (IptcTagInfo) this.order.elementAt(pos);
  }
  
//=============================================================================
//=============================================================================
}