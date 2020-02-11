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

import java.util.HashMap;
import com.zonageek.jpeg.codec.JpegCodec;

//=============================================================================
//=============================================================================
public class JpegBlock {
  public final static int BUFFER_SIZE = 64 * 1024;
  
  protected Jpeg jpeg;
  protected int marker;
  protected int length;
  protected byte[] data;
  protected boolean dataInSync;
  
  //===========================================================================
  protected JpegBlock(int marker) {
    this(marker, null);
  }

  //===========================================================================
  protected JpegBlock(int marker, Jpeg jpeg) {
    this.marker = marker;
    this.jpeg = jpeg;

    this.length = 0;
    this.data = new byte[JpegBlock.BUFFER_SIZE];

    this.dataInSync = true;
  }

  //===========================================================================
  protected JpegBlock(int marker, byte[] data, Jpeg jpeg) 
  throws JpegException {
    this.marker = marker;
    this.jpeg = jpeg;
    
    this.setData(data);
  }

  //===========================================================================
  public Jpeg getJpeg() {
    return this.jpeg;
  }
  
  //===========================================================================
  public JpegCodec getCodec() {
    if (this.jpeg != null)
      return this.jpeg.getCodec();
    else
      return null;
  }

  //===========================================================================
  public int getMarker() {
    return this.marker;
  }
  
  //===========================================================================
  public void setMarker(int marker) {
    this.marker = marker;
  }

  //===========================================================================
  public int getLength() {
    return this.length;
  }
  
  //===========================================================================
  public HashMap getInformation() {
    HashMap info = new HashMap();
    
    info.put("Jpeg Block", "Marker " 
      + JpegShortcuts.byte2hex(this.getMarker()));
    info.put("Jpeg Block Length", "" + this.length + " bytes");
    
    return info;
  }
  
  //===========================================================================
  public byte[] getData() throws JpegException {
    if (!this.dataInSync) {
      this.createData();
    }
    return this.data;
  }
  
  //===========================================================================
  public void setData(byte[] data) throws JpegException {
    this.data = data;
    if (this.data != null) {
      this.length = data.length;
      this.parseData();
    }
    else {
      this.length = 0;
    }
  }
    
  //===========================================================================
  protected void parseData() throws JpegException {
  }
  
  //===========================================================================
  protected void createData() throws JpegException {
    // Subclasses that allow changes to the data shoud expand this method
    this.dataInSync = true;
    this.trimData();
  }
  
  //===========================================================================
  public void newData() throws JpegException {
    this.length = 0;
    
    this.data = new byte[JpegBlock.BUFFER_SIZE];
  }
  
  //===========================================================================
  public void copyData(JpegBlock block)  throws JpegException {
    block.getData(); 
    // Just in case it needs refreshing before getting the length
    
    byte[] newData = new byte[block.getLength()];
    System.arraycopy(block.getData(), 0, newData, 0, block.getLength());
    this.setData(newData);
  }

  //===========================================================================
  protected void trimData() {
    if (this.length != this.data.length) {
      byte[] newData = new byte[this.length];
      
      System.arraycopy(this.data, 0, newData, 0, this.length);
      this.data = newData;
    }
  }
  
  //===========================================================================
  //===========================================================================
  //===========================================================================
  //===========================================================================
  public int getByte(int pos) {
    return (this.data[pos] & 0xFF);
  }
  
  //===========================================================================
  public void putByte(int pos, int value) {
    this.data[pos] = (byte)(value & 0x000000FF);
    if ((pos + 1) > this.length)
      this.length = pos + 1;
  }

  //===========================================================================
  public int getShort(int pos) {
    return this.getShort(pos, true);
  }

  //===========================================================================
  public int getShort(int pos, boolean isBigEndian) {
    if (isBigEndian) {
      return ((this.data[pos + 0] & 0xFF) << 8)
           + ((this.data[pos + 1] & 0xFF) << 0);
    }
    else {
      return ((this.data[pos + 1] & 0xFF) << 8)
           + ((this.data[pos + 0] & 0xFF) << 0);
    }
  }
  
  //===========================================================================
  public void putShort(int pos, int value) {
    this.putShort(pos, value, true);
  }

  //===========================================================================
  public void putShort(int pos, int value, boolean isBigEndian) {
    if (isBigEndian) {
      this.data[pos + 0] = (byte)((value & 0x0000FF00) >> 8);
      this.data[pos + 1] = (byte)((value & 0x000000FF) >> 0);
    }
    else {
      this.data[pos + 0] = (byte)((value & 0x000000FF) >> 0);
      this.data[pos + 1] = (byte)((value & 0x0000FF00) >> 8);
    }
    if ((pos + 2) > this.length)
      this.length = pos + 2;
  }

  //===========================================================================
  public int getLong(int pos) {
    return this.getLong(pos, true);
  }

  //===========================================================================
  public int getLong(int pos, boolean isBigEndian) {
    if (isBigEndian) {
      return ((this.data[pos + 0] & 0xFF) << 24)
           + ((this.data[pos + 1] & 0xFF) << 16)
           + ((this.data[pos + 2] & 0xFF) << 8)
           + ((this.data[pos + 3] & 0xFF) << 0);
    }
    else {
      return ((this.data[pos + 3] & 0xFF) << 24)
           + ((this.data[pos + 2] & 0xFF) << 16)
           + ((this.data[pos + 1] & 0xFF) << 8)
           + ((this.data[pos + 0] & 0xFF) << 0);
    }
  }

  //===========================================================================
  public void putLong(int pos, int value) {
    this.putLong(pos, value, true);
  }

  //===========================================================================
  public void putLong(int pos, int value, boolean isBigEndian) {
    if (isBigEndian) {
      this.data[pos + 0] = (byte)((value & 0xFF000000) >> 24);
      this.data[pos + 1] = (byte)((value & 0x00FF0000) >> 16);
      this.data[pos + 2] = (byte)((value & 0x0000FF00) >> 8);
      this.data[pos + 3] = (byte)((value & 0x000000FF) >> 0);
    }
    else {
      this.data[pos + 0] = (byte)((value & 0x000000FF) >> 0);
      this.data[pos + 1] = (byte)((value & 0x0000FF00) >> 8);
      this.data[pos + 2] = (byte)((value & 0x00FF0000) >> 16);
      this.data[pos + 3] = (byte)((value & 0xFF000000) >> 24);
    }
    if ((pos + 4) > this.length)
      this.length = pos + 4;
  }

  //===========================================================================
  public String getFixedString(int pos, int length) {
    try {
      return new String(this.data, pos, length);
    }
    catch (Exception ex) {
      return "";
    }
  }
  
  //===========================================================================
  public void putString(int pos, String value) {
    System.arraycopy(value.getBytes(), 0, this.data, pos, value.length());

    if ((pos + value.length()) > this.length)
      this.length = pos + value.length();
  }
  
  //===========================================================================
  public byte[] getByteArray(int pos, int length) {
    byte[] bytes = new byte[length];
    System.arraycopy(this.data, pos, bytes, 0, length);
    
    return bytes;
  }

  //===========================================================================
  public void putByteArray(int pos, byte[] value) {
    System.arraycopy(value, 0, this.data, pos, value.length);

    if ((pos + value.length) > this.length)
      this.length = pos + value.length;
  }

//=============================================================================
//=============================================================================
}
