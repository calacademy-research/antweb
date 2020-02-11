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

package com.zonageek.jpeg.codec;

import java.util.HashMap;
import java.util.Iterator;

//=============================================================================
//=============================================================================
public class JpegCodec {
  public final static int ENCODING_UNKNOWN = -1;
  public final static int ENCODING_BASELINE = 0;
  public final static int ENCODING_EXTENDED_SEQUENTIAL = 1;
  public final static int ENCODING_PROGRESSIVE = 2;
  public final static int ENCODING_OTHER = -2;

  public final static int DCT_BLOCK_SIZE = 8;
  public final static int DCT_ELEMENT_COUNT = DCT_BLOCK_SIZE * DCT_BLOCK_SIZE;

  public final static int HUFFMAN_DC_TABLE = 0;
  public final static int HUFFMAN_AC_TABLE = 1;
  
  public final static int MAX_QTABLES = 4;

  public final static int[] JPEG_NATURAL_ORDER = {
     0,  1,  8, 16,  9,  2,  3, 10,
    17, 24, 32, 25, 18, 11,  4,  5,
    12, 19, 26, 33, 40, 48, 41, 34,
    27, 20, 13,  6,  7, 14, 21, 28,
    35, 42, 49, 56, 57, 50, 43, 36,
    29, 22, 15, 23, 30, 37, 44, 51,
    58, 59, 52, 45, 38, 31, 39, 46,
    53, 60, 61, 54, 47, 55, 62, 63,
    63, 63, 63, 63, 63, 63, 63, 63, /* extra entries for safety in decoder */
    63, 63, 63, 63, 63, 63, 63, 63
  };
  
  
  protected int encoding;
  protected int width;
  protected int height;
  protected int channelCount;
  protected int bitsPerChannel;

  protected HashMap channels;
  protected HashMap qTables;
  protected HashMap acTableBits;
  protected HashMap acTableValues;
  protected HashMap dcTableBits;
  protected HashMap dcTableValues;

  protected int maxHSamples;
  protected int maxVSamples;

  protected int parameterSs;
  protected int parameterSe;
  protected int parameterAh;
  protected int parameterAl;
    
  //===========================================================================
  public JpegCodec() {
    this.encoding = JpegCodec.ENCODING_UNKNOWN;
    this.width = 0;
    this.height = 0;
    this.bitsPerChannel = 0;
    this.channelCount = 0;
    this.maxHSamples = 0;
    this.maxVSamples = 0;
    this.parameterSs = 0;
    this.parameterSe = 0;
    this.parameterAh = 0;
    this.parameterAl = 0;
    
    this.channels = new HashMap();
    this.qTables = new HashMap();
    this.acTableBits = new HashMap();
    this.acTableValues = new HashMap();
    this.dcTableBits = new HashMap();
    this.dcTableValues = new HashMap();
  }
  
  //===========================================================================
  public HashMap getInformation() {
    HashMap info = new HashMap();
    
    switch (this.encoding) {
    case JpegCodec.ENCODING_BASELINE:
      info.put("Image Encoding", "Baseline");
      break;
    case JpegCodec.ENCODING_EXTENDED_SEQUENTIAL:
      info.put("Image Encoding", "Extended Sequential (Non-Baseline)");
      break;
    case JpegCodec.ENCODING_PROGRESSIVE:
      info.put("Image Encoding", "Progressive");
      break;
    case JpegCodec.ENCODING_OTHER:
      info.put("Image Encoding", "Other");
      break;
    case JpegCodec.ENCODING_UNKNOWN:
    default:
      info.put("Image Encoding", "Unknown");
      break;
    }
    info.put("Image Width", new Integer(this.width));
    info.put("Image Height", new Integer(this.height));
    info.put("Channels", new Integer(this.channelCount));
    info.put("Bits Per Channel", new Integer(this.bitsPerChannel));

    Iterator values;    
    for (values = this.channels.values().iterator(); values.hasNext(); ) {
      JpegChannel channel = (JpegChannel)values.next();
      info.put("Channel #" + channel.id, "HSamples:" + channel.hSamples + " VSamples:" 
        + channel.vSamples + " QTable:" + channel.qTableId);
    }

    Iterator keys;
    for (keys = this.qTables.keySet().iterator(); keys.hasNext(); ) {
      Object key = keys.next();
      int[] table = (int[])this.qTables.get(key);
      int id = ((Integer)key).intValue();
      String str = "\n";
      for (int m = 0; m < JpegCodec.DCT_BLOCK_SIZE; m++) {
        for (int n = 0; n < JpegCodec.DCT_BLOCK_SIZE; n++) {
          int v = table[(n * JpegCodec.DCT_BLOCK_SIZE) + m];
          if (v < 100)
            str += " ";
          if (v < 10)
            str += " ";
          str += v;
        }
        str += "\n";
      }
      info.put("QTable #" + id, str);
    }

    for (keys = this.acTableBits.keySet().iterator(); keys.hasNext(); ) {
      Object key = keys.next();
      int [] intArray;
      intArray = (int[])this.acTableBits.get(key);
      int sum = 0;
      String str = "\nbits ";
      for (int m = 0; m < intArray.length; m++) {
        str += intArray[m] + " ";
        sum += intArray[m];
      }
      str += "(" + sum + ")";
      intArray = (int[])this.acTableValues.get(key);
      str += "\nvalues ";
      for (int m = 0; m < intArray.length; m++) {
        str += m + ":" + intArray[m] + " ";
      }
      
      info.put("Huffman AC Table: " + key, str);
    }

    for (keys = this.dcTableBits.keySet().iterator(); keys.hasNext(); ) {
      Object key = keys.next();
      int [] intArray;
      intArray = (int[])this.dcTableBits.get(key);
      int sum = 0;
      String str = "\nbits ";
      for (int m = 0; m < intArray.length; m++) {
        str += intArray[m] + " ";
        sum += intArray[m];
      }
      str += "(" + sum + ")";
      intArray = (int[])this.dcTableValues.get(key);
      str += "\nvalues ";
      for (int m = 0; m < intArray.length; m++) {
        str += m + ":" + intArray[m] + " ";
      }
      
      info.put("Huffman DC Table: " + key, str);
    }
    
    info.put("Parameters", " Ss:" + this.parameterSs 
      + " Se:" + this.parameterSe
      + " Ah:" + this.parameterAh
      + " Al:" + this.parameterAl);
    
    return info;
  }

  //===========================================================================
  public void setEncoding(int encoding) {
    this.encoding = encoding;
  }
  
  //===========================================================================
  public int getEncoding() {
    return this.encoding;
  }

  //===========================================================================
  public void setImageDimensions(int w, int h) {
    this.width = w;
    this.height = h;
  }
  
  //===========================================================================
  public void setChannelCount(int count) {
    this.channelCount = count;
    this.channels.clear();
  }
  
  //===========================================================================
  public void setBitsPerChannel(int bpc) {
    this.bitsPerChannel = bpc;
  }

  //===========================================================================
  public void setChannelInfo(int id, int hSamples, 
  int vSamples, int qTableId) {
    JpegChannel channel = (JpegChannel)this.channels.get(new Integer(id));
    if (channel == null) {
      channel = new JpegChannel(id, this);
      this.channels.put(new Integer(id), channel);
    }
    
    channel.hSamples = hSamples;
    channel.vSamples = vSamples;
    channel.qTableId = qTableId;
    if (hSamples > this.maxHSamples)
      this.maxHSamples = hSamples;
    if (vSamples > this.maxVSamples)
      this.maxVSamples = vSamples;
  }

  //===========================================================================
  public void setChannelTables(int id, int dcTableId, int acTableId) {
    JpegChannel channel = (JpegChannel)this.channels.get(new Integer(id));
    if (channel == null) {
      channel = new JpegChannel(id, this);
      this.channels.put(new Integer(id), channel);
    }

    channel.dcTableId = dcTableId;
    channel.acTableId = acTableId;
  }
  
  //===========================================================================
  public void setQTable(int id, int[] table) {
    this.qTables.put(new Integer(id), table);
    
    for (Iterator values = this.channels.values().iterator(); values.hasNext(); ) {
      JpegChannel channel = (JpegChannel)values.next();
      if (channel.qTableId == id)
        channel.qTable = table;
    }
  }

  //===========================================================================
  public int[] getQTable(int id) {
    return (int []) this.qTables.get(new Integer(id));
  }

  //===========================================================================
  public HashMap getQTables() {
    return this.qTables;
  }

  //===========================================================================
  public void setHuffmanTable(int type, int id, int[] bits, int[] values) {
    if (type == JpegCodec.HUFFMAN_DC_TABLE) {
      this.dcTableBits.put(new Integer(id), bits);
      this.dcTableValues.put(new Integer(id), values);
    }
    else {
      this.acTableBits.put(new Integer(id), bits);
      this.acTableValues.put(new Integer(id), values);
    }
  }
  
  //===========================================================================
  public void setParameters(int ss, int se, int ah, int al) {
    this.parameterSs = ss;
    this.parameterSe = se;
    this.parameterAh = ah;
    this.parameterAl = al;
  }
  
  //===========================================================================
  //===========================================================================
  //===========================================================================
  //===========================================================================
  public void transposeTable(int id) {
    int[] table = (int[]) this.getQTable(id);

    if (table != null) {
      int[] transposed = new int[table.length];
      
      for (int i = 0; i < JpegCodec.DCT_BLOCK_SIZE; i++) {
        for (int j = 0; j < JpegCodec.DCT_BLOCK_SIZE; j++) {
          transposed[(j * JpegCodec.DCT_BLOCK_SIZE) + i]
            = table[(i * JpegCodec.DCT_BLOCK_SIZE) + j];
        }
      }
      this.setQTable(id, transposed);
    }
  }

  //===========================================================================
  public void invertTableH(int id) {
    int[] table = (int[]) this.getQTable(id);

    if (table != null) {
      int[] inverted = new int[table.length];
      
      for (int i = 0; i < JpegCodec.DCT_BLOCK_SIZE; i++) {
        for (int j = 0; j < JpegCodec.DCT_BLOCK_SIZE; j++) {
          inverted[(i * JpegCodec.DCT_BLOCK_SIZE) + (JpegCodec.DCT_BLOCK_SIZE - j - 1)]
            = table[(i * JpegCodec.DCT_BLOCK_SIZE) + j];
        }
      }
      this.setQTable(id, inverted);
    }
  }
  
  //===========================================================================
  public void invertTableV(int id) {
    int[] table = (int[]) this.getQTable(id);

    if (table != null) {
      int[] inverted = new int[table.length];
      
      for (int i = 0; i < JpegCodec.DCT_BLOCK_SIZE; i++) {
        for (int j = 0; j < JpegCodec.DCT_BLOCK_SIZE; j++) {
          inverted[((JpegCodec.DCT_BLOCK_SIZE - i - 1) * JpegCodec.DCT_BLOCK_SIZE) + j]
            = table[(i * JpegCodec.DCT_BLOCK_SIZE) + j];
        }
      }
      this.setQTable(id, inverted);
    }
  }

  //===========================================================================
  public static int divRoundUp(int a, int b) {
    int val = a / b;
    if ((a % b) > 0)
      val += 1;
    return val;
  }

//=============================================================================
//=============================================================================
}