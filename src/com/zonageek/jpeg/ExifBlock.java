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
public class ExifBlock extends JpegBlock {
  public final static int UNKNOWN_VALUE = -1;
  
  protected boolean isBigEndian;
  protected HashMap exifData;
  
  //===========================================================================
  public ExifBlock() {
    this(null);
  }
  
  //===========================================================================
  public ExifBlock(Jpeg jpeg) {
    super(Jpeg.MARKER_APP1);
    this.isBigEndian = false;
    this.exifData = new HashMap();
    
    this.dataInSync = false;
  }
  
  //===========================================================================
  public ExifBlock(byte[] data, Jpeg jpeg) throws JpegException {
    super(Jpeg.MARKER_APP1, data, jpeg);
  }
  
  //===========================================================================
  public HashMap getInformation() {
    HashMap info = super.getInformation();
    
    info.put("Jpeg Block", "EXIF (APP1)");
    //info.remove("Jpeg Block Length");
    
    Iterator keys = this.exifData.keySet().iterator();
    while (keys.hasNext()) {
      Object key = keys.next();
      info.put(key.toString(), this.exifData.get(key));
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
  public HashMap getExifFields() {
    return this.exifData;
  }
  
  //===========================================================================
  public Object getExifField(String name) {
    return this.exifData.get(name);
  }
  
  //===========================================================================
  public String getExifString(String name) {
    Object value = this.exifData.get(name);
    if (value != null)
      return value.toString();
    else
      return null;
  }

  //===========================================================================
  public int getExifInt(String name, int defaultValue) {
    Object value = this.exifData.get(name);
    if ((value != null) && (value instanceof Integer)) {
      return ((Integer)value).intValue();
    }
    else if ((value != null) && (value instanceof HashMap)) {
      HashMap map = (HashMap) value;
      return ((Double)map.get("val")).intValue();
    }
    else {
      return defaultValue;
    }
  }

  //===========================================================================
  public double getExifRational(String name, double defaultValue) {
    Object value = this.exifData.get(name);
    if ((value != null) && (value instanceof Integer)) {
      return ((Integer)value).doubleValue();
    }
    else if ((value != null) && (value instanceof HashMap)) {
      HashMap map = (HashMap) value;
      return ((Double)map.get("val")).doubleValue();
    }
    else {
      return defaultValue;
    }
  }

  //===========================================================================
  public void clearExifField(String name) {
    this.exifData.remove(name);
    
    this.dataInSync = false;
  }
  
  //===========================================================================
  public void setExifField(String name, Object value) {
    this.exifData.put(name, value);
    this.dataInSync = false;
  }
  
  //===========================================================================
  public void addExifField(String name, Object value) {
    Object existing = this.exifData.get(name);
    Vector v;
    if (existing == null) {
      this.exifData.put(name, value);
    }
    else if (existing instanceof Vector) {
      v = (Vector)existing;
      v.add(value);
    }
    else {
      v = new Vector();
      v.add(existing);
      v.add(value);
      this.exifData.put(name, v);
    }
    this.dataInSync = false;
  }

  //===========================================================================
  public void setExifString(String name, String value) {
    this.setExifField(name, value);
  }
  
  //===========================================================================
  public void addExifString(String name, String value) {
    this.addExifField(name, value);
  }
  
  //===========================================================================
  public void setExifInt(String name, int value) {
    this.setExifField(name, Integer.valueOf(value));
  }
  
  //===========================================================================
  public void addExifInt(String name, int value) {
    this.addExifField(name, Integer.valueOf(value));
  }
  
  //===========================================================================
  public void setExifRational(String name, double value) {
    this.setExifField(name, this.doubleToRational(value, 5));
  }
  
  //===========================================================================
  public void addExifRational(String name, double value) {
    this.addExifField(name, this.doubleToRational(value, 5));
  }
  
  //===========================================================================
  public void setExifRational(String name, double value, int decimals) {
    this.setExifField(name, this.doubleToRational(value, decimals));
  }
  
  //===========================================================================
  public void addExifRational(String name, double value, int decimals) {
    this.addExifField(name, this.doubleToRational(value, decimals));
  }

  //===========================================================================
  public void parseData() throws JpegException {
    if (this.getFixedString(0, 6).equals("Exif\0\0")) {
      this.exifData = new HashMap();

      int pos = 6; 
      // We don't increment 'pos' after this point because Exif uses offsets
      // relative to this particular position
      
      int byteAlign = this.getShort(pos + 0);
      this.isBigEndian = false;
      if (byteAlign == 0x4949) { // "II"
        this.isBigEndian = false;
      }
      else if (byteAlign == 0x4D4D) { // "MM"
        this.isBigEndian = true;
      }
      else {
        throw new JpegException("Unexpected data at Exif Byte Align");
      }
  
      int alignCheck = this.getShort(pos + 2, this.isBigEndian);
      if (alignCheck != 0x002A) { // That's the expected value
        throw new JpegException("Unexpected data at Exif Byte Align check");
      }
      
      int offsetIFD0 = this.getLong(pos + 4, this.isBigEndian);
      if (offsetIFD0 < 8)
        throw new JpegException("Unexpected data at Exif IFD0 offset");
      
      int offsetIFD1 = this.readIFD(pos, offsetIFD0, "ifd0");
      
      if (offsetIFD1 != 0) {
        this.readIFD(pos, offsetIFD1, "ifd1");
      }
    }
    else {
      throw new JpegException("Not an EXIF block");
    }
  }

  //===========================================================================
  public void createData() throws JpegException {
    this.newData();
    
    String auxString;
    Integer auxInteger;
    
    int pos = 0;
    int offsetBase;
    
    this.putString(pos, "Exif\0\0");
    pos += 6;
    offsetBase = pos;
    
    if (this.isBigEndian) {
      this.putString(pos, "MM");
      pos += 2;
    }
    else {
      this.putString(pos, "II");
      pos += 2;
    }
    this.putShort(pos, 0x002A, this.isBigEndian);
    pos += 2;
    
    this.putLong(pos, 0x00000008, this.isBigEndian); // IFD Offset is allways 8
    pos += 4;
    
    pos = this.writeIFD(pos, offsetBase, "ifd0");
    pos = this.writeIFD(pos, offsetBase, "ifd1");

    this.trimData();
    this.dataInSync = true;
  }
  
  //===========================================================================
  //===========================================================================
  //===========================================================================
  protected int readIFD(int base, int offset, String mode) 
  throws JpegException {
    ExifTagList tags = new ExifTagList(mode);
    
    int numEntries = this.getShort(base + offset, this.isBigEndian);
    offset += 2;
    
    int exifTIFFOffset = 0;
    int exifTIFFLength = 0;
    int exifThumbnailOffset = 0;
    int exifThumbnailLength = 0;

    int tag, type, count;
    ExifTagInfo tagInfo;
    int dataLength, dataOffset;
        
    int value, num, den;
    Vector values;
    Object data;
        
    for (int i = 0; i < numEntries; i++) {
      tag = this.getShort(base + offset, this.isBigEndian);
      offset += 2;
      type = this.getShort(base + offset, this.isBigEndian);
      offset += 2;
      count = this.getLong(base + offset, this.isBigEndian);
      offset += 4;

      value = 0;
            
      if ((type < 1) || (type > 12))
        throw new JpegException("Unexpected data, invalid exif type");
      
      dataLength = ExifTagInfo.getUnitLength(type) * count;
      
      if ((dataLength > 0) && (dataLength <= 4)) {
        dataOffset = offset;
      }
      else if (dataLength > 4) {
        dataOffset = this.getLong(base + offset, this.isBigEndian);
      }
      else {
        throw new JpegException("Unexpected data, ifd entry with size 0");
      }
      offset += 4;
      
      switch (type) {
      case ExifTagInfo.TYPE_BYTE:
        // TODO: Check sign
        if (count == 1) {
          value = this.getByte(base + dataOffset);
          data = Integer.valueOf(value);
        }
        else {
          values = new Vector();
          for (int j = 0; j < count; j++) {
            values.add(Integer.valueOf(this.getByte(base + dataOffset)));
            dataOffset += 1;
          }
          data = values;
        }
        break;

      case ExifTagInfo.TYPE_ASCII:
        data = this.getFixedString(base + dataOffset, count);
        break;

      case ExifTagInfo.TYPE_SHORT:
        // TODO: Check sign
        if (count == 1) {
          value = this.getShort(base + dataOffset, this.isBigEndian);
          data = Integer.valueOf(value);
        }
        else {
          values = new Vector();
          for (int j = 0; j < count; j++) {
            values.add(Integer.valueOf(this.getShort(base + dataOffset,
              this.isBigEndian)));
            dataOffset += 2;
          }
          data = values;
        }
        break;

      case ExifTagInfo.TYPE_LONG:
        // TODO: Check sign
        if (count == 1) {
          value = this.getLong(base + dataOffset, this.isBigEndian);
          data = Integer.valueOf(value);
        }
        else {
          values = new Vector();
          for (int j = 0; j < count; j++) {
            values.add(Integer.valueOf(this.getLong(base + dataOffset,
              this.isBigEndian)));
            dataOffset += 4;
          }
          data = values;
        }
        break;

      case ExifTagInfo.TYPE_RATIONAL:
        // TODO: Check sign
        if (count == 1) {
          num = this.getLong(base + dataOffset, this.isBigEndian);
          den = this.getLong(base + dataOffset + 4, this.isBigEndian);

          HashMap map = new HashMap();            
          map.put("val", Double.valueOf((double)num / (double)den));
          map.put("num", Integer.valueOf(num));
          map.put("den", Integer.valueOf(den));
          data = map;
        }
        else {
          values = new Vector();
          for (int j = 0; j < count; j++) {
            num = this.getLong(base + dataOffset, this.isBigEndian);
            den = this.getLong(base + dataOffset + 4, this.isBigEndian);
            dataOffset += 8;

            HashMap map = new HashMap();
            map.put("val", Double.valueOf((double)num / (double)den));
            map.put("num", Integer.valueOf(num));
            map.put("den", Integer.valueOf(den));

            values.add(map);
          }
          data = values;
        }
        break;

      case ExifTagInfo.TYPE_SBYTE:
        // TODO: Check sign
        if (count == 1) {
          value = this.getByte(base + dataOffset);
          data = Integer.valueOf(value);
        }
        else {
          values = new Vector();
          for (int j = 0; j < count; j++) {
            values.add(Integer.valueOf(this.getByte(base + dataOffset)));
            dataOffset += 1;
          }
          data = values;
        }
        break;

      case ExifTagInfo.TYPE_UNDEFINED:
        data = this.getFixedString(base + dataOffset, count);
        break;

      case ExifTagInfo.TYPE_SSHORT:
        // TODO: Check sign
        if (count == 1) {
          value = this.getShort(base + dataOffset, this.isBigEndian);
          data = Integer.valueOf(value);
        }
        else {
          values = new Vector();
          for (int j = 0; j < count; j++) {
            values.add(Integer.valueOf(this.getShort(base + dataOffset,
              this.isBigEndian)));
            dataOffset += 2;
          }
          data = values;
        }
        break;

      case ExifTagInfo.TYPE_SLONG:
        // TODO: Check sign
        if (count == 1) {
          value = this.getLong(base + dataOffset, this.isBigEndian);
          data = Integer.valueOf(value);
        }
        else {
          values = new Vector();
          for (int j = 0; j < count; j++) {
            values.add(Integer.valueOf(this.getLong(base + dataOffset,
              this.isBigEndian)));
            dataOffset += 4;
          }
          data = values;
        }
        break;

      case ExifTagInfo.TYPE_SRATIONAL:
        // TODO: Check sign
        if (count == 1) {
          num = this.getLong(base + dataOffset, this.isBigEndian);
          den = this.getLong(base + dataOffset + 4, this.isBigEndian);

          HashMap map = new HashMap();            
          map.put("val", Double.valueOf((double)num / (double)den));
          map.put("num", Integer.valueOf(num));
          map.put("den", Integer.valueOf(den));
          data = map;
        }
        else {
          values = new Vector();
          for (int j = 0; j < count; j++) {
            num = this.getLong(base + dataOffset, this.isBigEndian);
            den = this.getLong(base + dataOffset + 4, this.isBigEndian);
            dataOffset += 8;

            HashMap map = new HashMap();
            map.put("val", Double.valueOf((double)num / (double)den));
            map.put("num", Integer.valueOf(num));
            map.put("den", Integer.valueOf(den));

            values.add(map);
          }
          data = values;
        }
        break;

      case ExifTagInfo.TYPE_FLOAT:
        throw new JpegException("Unexpected data, unexpected 'float' in ifd");

      case ExifTagInfo.TYPE_DFLOAT:
        throw new JpegException("Unexpected data, unexpected 'dfloat' in ifd");

      default:
        throw new JpegException("Unexpected data, unknown ifd entry type");
      }

      if (mode.equals("ifd0") && (tag == 0x8769)) { // ExifIFDOffset
        this.readIFD(base, value, "exif");
      }
      else if (mode.equals("ifd0") && (tag == 0x8825)) { // GPSIFDOffset
        this.readIFD(base, value, "gps");
      }
      else if (mode.equals("ifd1") && (tag == 0x0111)) { // TIFFStripOffsets
        exifTIFFOffset = value;
      }
      else if (mode.equals("ifd1") && (tag == 0x0117)) { // TIFFStripByteCounts
        exifTIFFLength = value;
      }
      else if (mode.equals("ifd1") && (tag == 0x0201)) { // TIFFJFIFOffset
        exifThumbnailOffset = value;
      }
      else if (mode.equals("ifd1") && (tag == 0x0202)) { // TIFFJFIFLength
        exifThumbnailLength = value;
      }
      else if (mode.equals("exif") && (tag == 0xA005)) { // InteropIFDOffset
        this.readIFD(base, value, "interop");
      }
      else {
        tagInfo = tags.getTagById(tag);

        if (tagInfo != null) {
          if (this.exifData.containsKey(tagInfo.name)) {
            Object obj = this.exifData.get(tagInfo.name);
            Vector v;
            if (obj instanceof Vector) {
              v = (Vector) obj;
              v.add(data);
            }
            else {
              v = new Vector();
              v.add(obj);
              this.exifData.remove(tagInfo.name);
              this.exifData.put(tagInfo.name, v);
            }
          }
          else {
            this.exifData.put(tagInfo.name, data);
          }
        }
        else {
          //throw new JpegException("Unexpected data, unknwon EXIF tag " 
          //  + tag + " (" + mode + ")");
        	
        }
      }
    }
    
    if ((exifThumbnailOffset > 0) && (exifThumbnailLength > 0)) {
      this.exifData.put("JFIFThumbnail", 
        this.getFixedString(base + exifThumbnailOffset, exifThumbnailLength));
    }

    if ((exifTIFFOffset > 0) && (exifTIFFLength > 0)) {
      this.exifData.put("TIFFStrips", 
        this.getFixedString(base + exifTIFFOffset, exifTIFFLength));
    }
    
    return this.getLong(base + offset, this.isBigEndian);
  }
  
  //===========================================================================
  public int writeIFD(int pos, int offsetBase, String mode) 
  throws JpegException {
    ExifTagList tags = new ExifTagList(mode);
    int entryCount = 0;
    
    // First pass... find out how many entries we will have
    for (int i = 0; i < tags.getTagCount(); i++) {
      ExifTagInfo tag = tags.getTagAt(i);
      // Check for special cases where we saved the data using different names 
      // than the original tag
      if (mode.equals("ifd0") && (tag.id == 0x8769)) { // ExifIFDOffset
        if (this.exifData.get("EXIFVersion") != null)
          entryCount++;
      }
      else if(mode.equals("ifd0") && (tag.id == 0x8825)) { // GPSIFDOffset
        if (this.exifData.get("GPSVersionID") != null)
          entryCount++;
      }
      else if(mode.equals("ifd1") && (tag.id == 0x0111)) { // TIFFStripOffset
        if (this.exifData.get("TIFFStrips") != null)
          entryCount++;
      }
      else if(mode.equals("ifd1") && (tag.id == 0x0117)) { // TIFFStripByteCounts
        if (this.exifData.get("TIFFStrips") != null)
          entryCount++;
      }
      else if(mode.equals("ifd1") && (tag.id == 0x0201)) { // TIFFJFIFOffset
        if (this.exifData.get("JFIFThumbnail") != null)
          entryCount++;
      }
      else if(mode.equals("ifd1") && (tag.id == 0x0202)) { // TIFFJFIFLength
        if (this.exifData.get("JFIFThumbnail") != null)
          entryCount++;
      }
      else if(mode.equals("exif") && (tag.id == 0xA005)) { // InteropIFDOffset
        if (this.exifData.get("InteroperabilityIndex") != null)
          entryCount++;
      }
      else {
        if (this.exifData.get(tag.name) != null)
          entryCount++;
      }
    }

    this.putShort(pos, entryCount, this.isBigEndian);
    pos += 2;
    
    int dataPos = pos + (entryCount * 12) + 4;
    Object value;
    int aux;
    String auxString;
    Vector auxVector;
    HashMap auxMap;
    
    // Second pass... actually save the entries
    for (int i = 0; i < tags.getTagCount(); i++) {
      ExifTagInfo tag = tags.getTagAt(i);
      value = null;
      
      // Check for special cases where we saved the data using different names 
      // than the original tag
      if (mode.equals("ifd0") && (tag.id == 0x8769)) { // ExifIFDOffset
        if (this.exifData.get("EXIFVersion") != null) {
          value = Integer.valueOf(dataPos - offsetBase);
          dataPos = this.writeIFD(dataPos, offsetBase, "exif");
        }
      }
      else if(mode.equals("ifd0") && (tag.id == 0x8825)) { // GPSIFDOffset
        if (this.exifData.get("GPSVersionID") != null) {
          value = Integer.valueOf(dataPos - offsetBase);
          dataPos = this.writeIFD(dataPos, offsetBase, "gps");
        }
      }
      else if(mode.equals("ifd1") && (tag.id == 0x0111)) { // TIFFStripOffset
        if (this.exifData.get("TIFFStrips") != null) {
          value = Integer.valueOf(dataPos - offsetBase);
          auxString = (String)this.exifData.get("TIFFStrips");
          this.putString(dataPos, auxString);
          dataPos += auxString.length();
        }
      }
      else if(mode.equals("ifd1") && (tag.id == 0x0117)) { // TIFFStripByteCounts
        if (this.exifData.get("TIFFStrips") != null) {
          auxString = (String)this.exifData.get("TIFFStrips");
          value = Integer.valueOf(auxString.length());
        }
      }
      else if(mode.equals("ifd1") && (tag.id == 0x0201)) { // TIFFJFIFOffset
        if (this.exifData.get("JFIFThumbnail") != null) {
          value = Integer.valueOf(dataPos - offsetBase);
          auxString = (String)this.exifData.get("JFIFThumbnail");
          this.putString(dataPos, auxString);
          dataPos += auxString.length();
        }
      }
      else if(mode.equals("ifd1") && (tag.id == 0x0202)) { // TIFFJFIFLength
        if (this.exifData.get("JFIFThumbnail") != null) {
          auxString = (String)this.exifData.get("JFIFThumbnail");
          value = Integer.valueOf(auxString.length());
        }
      }
      else if(mode.equals("exif") && (tag.id == 0xA005)) { // InteropIFDOffset
        if (this.exifData.get("InteroperabilityIndex") != null) {
          value = Integer.valueOf(dataPos - offsetBase);
          dataPos = this.writeIFD(dataPos, offsetBase, "interop");
        }
      }
      else {
        value = this.exifData.get(tag.name);
      }
      
      if (value != null) {
        int itemCount;
        
        if (value instanceof Vector) {
          itemCount = ((Vector)value).size();
        }
        else if (value instanceof String) {
          itemCount = ((String)value).length();
        }
        else {
          itemCount = 1;
        }
        
        int itemSize = itemCount * ExifTagInfo.getUnitLength(tag.type);
        
        this.putShort(pos, tag.id, this.isBigEndian);
        pos += 2;
        this.putShort(pos, tag.type, this.isBigEndian);
        pos += 2;
        this.putLong(pos, itemCount, this.isBigEndian);
        pos += 4;
        
        int itemPos;
        if (itemSize > 4) {
          this.putLong(pos, dataPos - offsetBase, this.isBigEndian);
          pos += 4;
          itemPos = dataPos;
          dataPos += itemSize;
        }
        else {
          itemPos = pos;
          pos += 4;
        }
        
        switch (tag.type) {
        case ExifTagInfo.TYPE_BYTE:
          // TODO: Check sign
          if (itemCount == 1) {
            this.putByte(itemPos, ((Integer)value).intValue());
          }
          else {
            auxVector = (Vector)value;
            for (int j = 0; j < itemCount; j++) {
              this.putByte(itemPos, 
                ((Integer)(auxVector.elementAt(i))).intValue());
              itemPos += 1;
            }
          }
          break;
  
        case ExifTagInfo.TYPE_ASCII:
          this.putString(itemPos, ((String)value));
          break;
  
        case ExifTagInfo.TYPE_SHORT:
          // TODO: Check sign
          if (itemCount == 1) {
            this.putShort(itemPos, ((Integer)value).intValue(), 
              this.isBigEndian);
          }
          else {
            auxVector = (Vector)value;
            for (int j = 0; j < itemCount; j++) {
              this.putShort(itemPos, 
                ((Integer)(auxVector.elementAt(i))).intValue(), 
                this.isBigEndian);
              itemPos += 2;
            }
          }
          break;
  
        case ExifTagInfo.TYPE_LONG:
          // TODO: Check sign
          if (itemCount == 1) {
            this.putLong(itemPos, ((Integer)value).intValue(), 
              this.isBigEndian);
          }
          else {
            auxVector = (Vector)value;
            for (int j = 0; j < itemCount; j++) {
              this.putLong(itemPos, 
                ((Integer)(auxVector.elementAt(i))).intValue(), 
                this.isBigEndian);
              itemPos += 4;
            }
          }
          break;
  
        case ExifTagInfo.TYPE_RATIONAL:
          // TODO: Check sign
          if (itemCount == 1) {
            auxMap = (HashMap)value;
            this.putLong(itemPos, ((Integer)(auxMap.get("num"))).intValue(), 
              this.isBigEndian);
            itemPos += 4;
            this.putLong(itemPos, ((Integer)(auxMap.get("den"))).intValue(), 
              this.isBigEndian);
          }
          else {
            auxVector = (Vector)value;
            for (int j = 0; j < itemCount; j++) {
              auxMap = (HashMap)auxVector.elementAt(i);
              this.putLong(itemPos, ((Integer)(auxMap.get("num"))).intValue(), 
                this.isBigEndian);
              itemPos += 4;
              this.putLong(itemPos, ((Integer)(auxMap.get("den"))).intValue(), 
                this.isBigEndian);
              itemPos += 4;
            }
          }
          break;
  
        case ExifTagInfo.TYPE_SBYTE:
          // TODO: Check sign
          if (itemCount == 1) {
            this.putByte(itemPos, ((Integer)value).intValue());
          }
          else {
            auxVector = (Vector)value;
            for (int j = 0; j < itemCount; j++) {
              this.putByte(itemPos, 
                ((Integer)(auxVector.elementAt(i))).intValue());
              itemPos += 1;
            }
          }
          break;
  
        case ExifTagInfo.TYPE_UNDEFINED:
          this.putString(itemPos, ((String)value));
          break;
  
        case ExifTagInfo.TYPE_SSHORT:
          // TODO: Check sign
          if (itemCount == 1) {
            this.putShort(itemPos, ((Integer)value).intValue(), 
              this.isBigEndian);
          }
          else {
            auxVector = (Vector)value;
            for (int j = 0; j < itemCount; j++) {
              this.putShort(itemPos, 
                ((Integer)(auxVector.elementAt(i))).intValue(), 
                this.isBigEndian);
              itemPos += 2;
            }
          }
          break;
  
        case ExifTagInfo.TYPE_SLONG:
          // TODO: Check sign
          if (itemCount == 1) {
            this.putLong(itemPos, ((Integer)value).intValue(), 
              this.isBigEndian);
          }
          else {
            auxVector = (Vector)value;
            for (int j = 0; j < itemCount; j++) {
              this.putLong(itemPos, 
                ((Integer)(auxVector.elementAt(i))).intValue(), 
                this.isBigEndian);
              itemPos += 4;
            }
          }
          break;
  
        case ExifTagInfo.TYPE_SRATIONAL:
          // TODO: Check sign
          if (itemCount == 1) {
            auxMap = (HashMap)value;
            this.putLong(itemPos, ((Integer)(auxMap.get("num"))).intValue(), 
              this.isBigEndian);
            itemPos += 4;
            this.putLong(itemPos, ((Integer)(auxMap.get("den"))).intValue(), 
              this.isBigEndian);
          }
          else {
            auxVector = (Vector)value;
            for (int j = 0; j < itemCount; j++) {
              auxMap = (HashMap)auxVector.elementAt(i);
              this.putLong(itemPos, ((Integer)(auxMap.get("num"))).intValue(), 
                this.isBigEndian);
              itemPos += 4;
              this.putLong(itemPos, ((Integer)(auxMap.get("den"))).intValue(), 
                this.isBigEndian);
              itemPos += 4;
            }
          }
          break;
  
        case ExifTagInfo.TYPE_FLOAT:
          throw new JpegException("Unexpected data: 'float' in ifd");
  
        case ExifTagInfo.TYPE_DFLOAT:
          throw new JpegException("Unexpected data: 'dfloat' in ifd");
  
        default:
          throw new JpegException("Unexpected data: unknown ifd entry type");
        }
        
      }
    }
    
    if (mode.equals("ifd0")) {
      this.putLong(pos, dataPos - offsetBase, this.isBigEndian);
      pos += 4;
    }
    else {
      this.putLong(pos, 0, this.isBigEndian);
      pos += 4;
    }

    return dataPos;
  }

  //===========================================================================
  protected HashMap doubleToRational(double value, int decimals) {
    long num, den;
    
    if (value == 0) {
      num = 0;
      den = 1;
    }
    else {
      boolean negative = false;
      
      if (value < 0) {
        negative = true;
        value = -value;
      }
      
      int i = 0;
      boolean done = false;
      num = 0;
      den = 0;
      while (!done && (i <= decimals)) {
        if (den == 0)
          den = 1;
        else
          den = den * 10;
        num = Math.round((value*(double)den) - (double)0.5f);
        if (((double)num / (double)den) == value) {
          done = true;
        }
        else {
          i++;
        }
      }
      
      
      if (negative)
        num = -num;
    }

    HashMap map = new HashMap();
    map.put("num", Integer.valueOf((int)num));
    map.put("den", Integer.valueOf((int)den));
    map.put("val", Double.valueOf((double)num / (double)den));
    
    return map;
  }
//=============================================================================
//=============================================================================
}


//=============================================================================
//=============================================================================
class ExifTagInfo {
  public int id;
  public String name;
  public int type;
  public int size;
  
  public final static int TYPE_BYTE = 1;
  public final static int TYPE_ASCII = 2;
  public final static int TYPE_SHORT = 3;
  public final static int TYPE_LONG = 4;
  public final static int TYPE_RATIONAL = 5;
  public final static int TYPE_SBYTE = 6;
  public final static int TYPE_UNDEFINED = 7;
  public final static int TYPE_SSHORT = 8;
  public final static int TYPE_SLONG = 9;
  public final static int TYPE_SRATIONAL = 10;
  public final static int TYPE_FLOAT = 11;
  public final static int TYPE_DFLOAT = 12;
  
  public ExifTagInfo(int id, String name, int type, int size) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.size = size;
  }
  
  public static int getUnitLength(int type) {
    switch (type) {
    case ExifTagInfo.TYPE_BYTE:
      return 1;
    case ExifTagInfo.TYPE_ASCII:
      return 1;
    case ExifTagInfo.TYPE_SHORT:
      return 2;
    case ExifTagInfo.TYPE_LONG:
      return 4;
    case ExifTagInfo.TYPE_RATIONAL:
      return 8;
    case ExifTagInfo.TYPE_SBYTE:
      return 1;
    case ExifTagInfo.TYPE_UNDEFINED:
      return 1;
    case ExifTagInfo.TYPE_SSHORT:
      return 2;
    case ExifTagInfo.TYPE_SLONG:
      return 4;
    case ExifTagInfo.TYPE_SRATIONAL:
      return 8;
    case ExifTagInfo.TYPE_FLOAT:
      return 4;
    case ExifTagInfo.TYPE_DFLOAT:
      return 8;
    }
    
    return 0;
  }
//=============================================================================
//=============================================================================
}

//=============================================================================
//=============================================================================
class ExifTagList {
  protected HashMap ids;
  protected HashMap names;
  protected Vector order;
  
  //===========================================================================
  public ExifTagList(String mode) {
    this.ids = new HashMap();
    this.names = new HashMap();
    this.order = new Vector();

    if (mode.equals("ifd0")) {
      this.put(new ExifTagInfo(0x010E, "ImageDescription", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x010F, "Make", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x0110, "Model", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x0112, "Orientation", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x011A, "XResolution", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x011B, "YResolution", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x0128, "ResolutionUnit", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0131, "Software", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x0132, "DateTime", ExifTagInfo.TYPE_ASCII, 20));
      this.put(new ExifTagInfo(0x013B, "Artist", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x013E, "WhitePoint", ExifTagInfo.TYPE_RATIONAL, 2));
      this.put(new ExifTagInfo(0x013F, "PrimaryChromaticities", ExifTagInfo.TYPE_RATIONAL, 6));
      this.put(new ExifTagInfo(0x0211, "YCbCrCoefficients", ExifTagInfo.TYPE_RATIONAL, 3));
      this.put(new ExifTagInfo(0x0212, "YCbCrSubSampling", ExifTagInfo.TYPE_SHORT, 2));
      this.put(new ExifTagInfo(0x0213, "YCbCrPositioning", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0214, "ReferenceBlackWhite", ExifTagInfo.TYPE_RATIONAL, 6));
      this.put(new ExifTagInfo(0x8298, "Copyright", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x8769, "ExifIFDOffset", ExifTagInfo.TYPE_LONG, 1));
      this.put(new ExifTagInfo(0x8825, "GPSIFDOffset", ExifTagInfo.TYPE_LONG, 1));
    }
    else if (mode.equals("ifd1")) {
      this.put(new ExifTagInfo(0x00FE, "TIFFNewSubfileType", ExifTagInfo.TYPE_LONG, 1));
      this.put(new ExifTagInfo(0x00FF, "TIFFSubfileType", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0100, "TIFFImageWidth", ExifTagInfo.TYPE_LONG, 1));
      this.put(new ExifTagInfo(0x0101, "TIFFImageHeight", ExifTagInfo.TYPE_LONG, 1));
      this.put(new ExifTagInfo(0x0102, "TIFFBitsPerSample", ExifTagInfo.TYPE_SHORT, 3));
      this.put(new ExifTagInfo(0x0103, "TIFFCompression", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0106, "TIFFPhotometricInterpretation", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0107, "TIFFThreshholding", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0108, "TIFFCellWidth", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0109, "TIFFCellLength", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x010A, "TIFFFillOrder", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x010E, "TIFFImageDescription", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x010F, "TIFFMake", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x0110, "TIFFModel", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x0111, "TIFFStripOffsets", ExifTagInfo.TYPE_LONG, 0)); // or SHORT, Any (One per strip)
      this.put(new ExifTagInfo(0x0112, "TIFFOrientation", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0115, "TIFFSamplesPerPixel", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0116, "TIFFRowsPerStrip", ExifTagInfo.TYPE_LONG, 1)); // or SHORT
      this.put(new ExifTagInfo(0x0117, "TIFFStripByteCounts", ExifTagInfo.TYPE_LONG, 0)); // or SHORT, Any (One per strip)
      this.put(new ExifTagInfo(0x0118, "TIFFMinSampleValue", ExifTagInfo.TYPE_SHORT, 0)); // Any (SamplesPerPixel)
      this.put(new ExifTagInfo(0x0119, "TIFFMaxSampleValue", ExifTagInfo.TYPE_SHORT, 0)); // Any (SamplesPerPixel)
      this.put(new ExifTagInfo(0x011A, "TIFFXResolution", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x011B, "TIFFYResolution", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x011C, "TIFFPlanarConfiguration", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0122, "TIFFGrayResponseUnit", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0123, "TIFFGrayResponseCurve", ExifTagInfo.TYPE_SHORT, 0)); // Any (2^BitsPerSample)
      this.put(new ExifTagInfo(0x0128, "TIFFResolutionUnit", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0131, "TIFFSoftware", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x0132, "TIFFDateTime", ExifTagInfo.TYPE_ASCII, 20));
      this.put(new ExifTagInfo(0x013B, "TIFFArtist", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x013C, "TIFFHostComputer", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x0140, "TIFFColorMap", ExifTagInfo.TYPE_SHORT, 0)); // Any (3 * 2^BitsPerSample)
      this.put(new ExifTagInfo(0x0152, "TIFFExtraSamples", ExifTagInfo.TYPE_SHORT, 0)); // Any (SamplesPerPixel - 3)
      this.put(new ExifTagInfo(0x0201, "TIFFJFIFOffset", ExifTagInfo.TYPE_LONG, 1));
      this.put(new ExifTagInfo(0x0202, "TIFFJFIFLength", ExifTagInfo.TYPE_LONG, 1));
      this.put(new ExifTagInfo(0x0211, "TIFFYCbCrCoefficients", ExifTagInfo.TYPE_RATIONAL, 3));
      this.put(new ExifTagInfo(0x0212, "TIFFYCbCrSubSampling", ExifTagInfo.TYPE_SHORT, 2));
      this.put(new ExifTagInfo(0x0213, "TIFFYCbCrPositioning", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x0214, "TIFFReferenceBlackWhite", ExifTagInfo.TYPE_RATIONAL, 6));
      this.put(new ExifTagInfo(0x8298, "TIFFCopyright", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x9286, "TIFFUserComment", ExifTagInfo.TYPE_ASCII, 0));
    }
    else if (mode.equals("exif")) {
      this.put(new ExifTagInfo(0x829A, "ExposureTime", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x829D, "FNumber", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x8822, "ExposureProgram", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x8824, "SpectralSensitivity", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x8827, "ISOSpeedRatings", ExifTagInfo.TYPE_SHORT, 0));
      this.put(new ExifTagInfo(0x8828, "OECF", ExifTagInfo.TYPE_UNDEFINED, 0));
      this.put(new ExifTagInfo(0x9000, "EXIFVersion", ExifTagInfo.TYPE_UNDEFINED, 4));
      this.put(new ExifTagInfo(0x9003, "DatetimeOriginal", ExifTagInfo.TYPE_ASCII, 20));
      this.put(new ExifTagInfo(0x9004, "DatetimeDigitized", ExifTagInfo.TYPE_ASCII, 20));
      this.put(new ExifTagInfo(0x9101, "ComponentsConfiguration", ExifTagInfo.TYPE_UNDEFINED, 4));
      this.put(new ExifTagInfo(0x9102, "CompressedBitsPerPixel", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x9201, "ShutterSpeedValue", ExifTagInfo.TYPE_SRATIONAL, 1));
      this.put(new ExifTagInfo(0x9202, "ApertureValue", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x9203, "BrightnessValue", ExifTagInfo.TYPE_SRATIONAL, 1));
      this.put(new ExifTagInfo(0x9204, "ExposureBiasValue", ExifTagInfo.TYPE_SRATIONAL, 1));
      this.put(new ExifTagInfo(0x9205, "MaxApertureValue", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x9206, "SubjectDistance", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x9207, "MeteringMode", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x9208, "LightSource", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x9209, "Flash", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0x920A, "FocalLength", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x927C, "MakerNote", ExifTagInfo.TYPE_UNDEFINED, 0));
      this.put(new ExifTagInfo(0x9286, "UserComment", ExifTagInfo.TYPE_UNDEFINED, 0));
      this.put(new ExifTagInfo(0x9290, "SubSecTime", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x9291, "SubSecTimeOriginal", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x9292, "SubSecTimeDigitized", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0xA000, "FlashPixVersion", ExifTagInfo.TYPE_UNDEFINED, 4));
      this.put(new ExifTagInfo(0xA001, "ColorSpace", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0xA002, "PixelXDimension", ExifTagInfo.TYPE_LONG, 1)); // or SHORT
      this.put(new ExifTagInfo(0xA003, "PixelYDimension", ExifTagInfo.TYPE_LONG, 1)); // or SHORT
      this.put(new ExifTagInfo(0xA004, "RelatedSoundFile", ExifTagInfo.TYPE_ASCII, 13));
      this.put(new ExifTagInfo(0xA005, "InteropIFDOffset", ExifTagInfo.TYPE_LONG, 1));
      this.put(new ExifTagInfo(0xA20B, "FlashEnergy", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0xA20C, "SpatialFrequencyResponse", ExifTagInfo.TYPE_UNDEFINED, 0));
      this.put(new ExifTagInfo(0xA20E, "FocalPlaneXResolution", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0xA20F, "FocalPlaneYResolution", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0xA210, "FocalPlaneResolutionUnit", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0xA214, "SubjectLocation", ExifTagInfo.TYPE_SHORT, 2));
      this.put(new ExifTagInfo(0xA215, "ExposureIndex", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0xA217, "SensingMethod", ExifTagInfo.TYPE_SHORT, 1));
      this.put(new ExifTagInfo(0xA300, "FileSource", ExifTagInfo.TYPE_UNDEFINED, 1));
      this.put(new ExifTagInfo(0xA301, "SceneType", ExifTagInfo.TYPE_UNDEFINED, 1));
      this.put(new ExifTagInfo(0xA302, "CFAPattern", ExifTagInfo.TYPE_UNDEFINED, 0));
    }
    else if (mode.equals("interop")) {
      this.put(new ExifTagInfo(0x0001, "InteroperabilityIndex", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x0002, "InteroperabilityVersion", ExifTagInfo.TYPE_UNDEFINED, 4));
      this.put(new ExifTagInfo(0x1000, "RelatedImageFileFormat", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x1001, "RelatedImageWidth", ExifTagInfo.TYPE_LONG, 1));
      this.put(new ExifTagInfo(0x1002, "RelatedImageLength", ExifTagInfo.TYPE_LONG, 1));
    }
    else if (mode.equals("gps")) {
      this.put(new ExifTagInfo(0x0000, "GPSVersionID", ExifTagInfo.TYPE_BYTE, 4));
      this.put(new ExifTagInfo(0x0001, "GPSLatitudeRef", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x0002, "GPSLatitude", ExifTagInfo.TYPE_RATIONAL, 3));
      this.put(new ExifTagInfo(0x0003, "GPSLongitudeRef", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x0004, "GPSLongitude", ExifTagInfo.TYPE_RATIONAL, 3));
      this.put(new ExifTagInfo(0x0005, "GPSAltitudeRef", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x0006, "GPSAltitude", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x0007, "GPSTimeStamp", ExifTagInfo.TYPE_RATIONAL, 3));
      this.put(new ExifTagInfo(0x0008, "GPSSatellites", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x0009, "GPSStatus", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x000A, "GPSMeasureMode", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x000B, "GPSDOP", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x000C, "GPSSpeedRef", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x000D, "GPSSpeed", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x000E, "GPSTrackRef", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x000F, "GPSTrack", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x0010, "GPSImgDirectionRef", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x0011, "GPSImgDirection", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x0012, "GPSMapDatum", ExifTagInfo.TYPE_ASCII, 0));
      this.put(new ExifTagInfo(0x0013, "GPSDestLatitudeRef", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x0014, "GPSDestLatitude", ExifTagInfo.TYPE_RATIONAL, 3));
      this.put(new ExifTagInfo(0x0015, "GPSDestLongitudeRef", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x0016, "GPSDestLongitude", ExifTagInfo.TYPE_RATIONAL, 3));
      this.put(new ExifTagInfo(0x0017, "GPSDestBearingRef", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x0018, "GPSDestBearing", ExifTagInfo.TYPE_RATIONAL, 1));
      this.put(new ExifTagInfo(0x0019, "GPSDestDistanceRef", ExifTagInfo.TYPE_ASCII, 2));
      this.put(new ExifTagInfo(0x001A, "GPSDestDistance", ExifTagInfo.TYPE_RATIONAL, 1));
    }
  }
  
  //===========================================================================
  public void put(ExifTagInfo tag) {
    this.ids.put(Integer.valueOf(tag.id), tag);
    this.names.put(tag.name, tag);
    this.order.add(tag);
  }
  
  //===========================================================================
  public ExifTagInfo getTagByName(String name) {
    return (ExifTagInfo) this.names.get(name);
  }
  
  //===========================================================================
  public ExifTagInfo getTagById(int id) {
    return (ExifTagInfo) this.ids.get(Integer.valueOf(id));
  }

  //===========================================================================
  public int getTagCount() {
    return this.order.size();
  }
  
  //===========================================================================
  public ExifTagInfo getTagAt(int pos) {
    return (ExifTagInfo) this.order.elementAt(pos);
  }
  
//=============================================================================
//=============================================================================
}