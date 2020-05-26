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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import com.zonageek.jpeg.codec.JpegCodec;

//=============================================================================
//=============================================================================
public class Jpeg {
  public final static int MARKER_HEADER = 0xFF; // All markers start with this

  public final static int MARKER_SOI   = 0xD8; // Start Of Image
  public final static int MARKER_EOI   = 0xD9; // End Of Image
  public final static int MARKER_SOS   = 0xDA; // Start Of Scan

  // Common SOF (Start Of Frame) markers
  public final static int MARKER_SOF0  = 0xC0; // Baseline DCT
  public final static int MARKER_SOF1  = 0xC1; // Extended sequential DCT
  public final static int MARKER_SOF2  = 0xC2; // Progressive DCT
  // Other SOF markers
  public final static int MARKER_SOF3  = 0xC3; // Spatial sequential lossless
  public final static int MARKER_SOF5  = 0xC5; // Differential Huffman Sequential DCT
  public final static int MARKER_SOF6  = 0xC6; // Differential Huffman Progressive DCT
  public final static int MARKER_SOF7  = 0xC7; // Differential Huffman Spatial
  public final static int MARKER_SOF8  = 0xC8; // Reserved
  public final static int MARKER_SOF9  = 0xC9; // Extended Sequential DCT
  public final static int MARKER_SOF10 = 0xCA; // Progressive DCT
  public final static int MARKER_SOF11 = 0xCB; // Spatial Sequential Lossless
  public final static int MARKER_SOF13 = 0xCD; // Differential Sequential DCT
  public final static int MARKER_SOF14 = 0xCE; // Differential Progressive DCT
  public final static int MARKER_SOF15 = 0xCF; // Differential Spatial

  // Misc markers
  public final static int MARKER_DHT   = 0xC4; // Define Huffman Table
  public final static int MARKER_DAC   = 0xCC; // Define Arithmetic Conditioning
  public final static int MARKER_RST0  = 0xD0;
  public final static int MARKER_RST1  = 0xD1;
  public final static int MARKER_RST2  = 0xD2;
  public final static int MARKER_RST3  = 0xD3;
  public final static int MARKER_RST4  = 0xD4;
  public final static int MARKER_RST5  = 0xD5;
  public final static int MARKER_RST6  = 0xD6;
  public final static int MARKER_RST7  = 0xD7;
  public final static int MARKER_DQT   = 0xDB; // Define Quantization Table
  public final static int MARKER_DNL   = 0xDC; // Define Number Of Lines
  public final static int MARKER_DRI   = 0xDD; // Define Restart Interval
  public final static int MARKER_DHP   = 0xDE; // Define Hierachical Progression
  public final static int MARKER_EXP   = 0xDF; // Expand Reference Image

  // Application Data markers
  public final static int MARKER_APP0  = 0xE0; // Usually JFIF
  public final static int MARKER_APP1  = 0xE1; // Usually EXIF
  public final static int MARKER_APP2  = 0xE2; // Usually ICC Profile
  public final static int MARKER_APP3  = 0xE3;
  public final static int MARKER_APP4  = 0xE4;
  public final static int MARKER_APP5  = 0xE5;
  public final static int MARKER_APP6  = 0xE6;
  public final static int MARKER_APP7  = 0xE7;
  public final static int MARKER_APP8  = 0xE8;
  public final static int MARKER_APP9  = 0xE9;
  public final static int MARKER_APP10 = 0xEA;
  public final static int MARKER_APP11 = 0xEB;
  public final static int MARKER_APP12 = 0xEC;
  public final static int MARKER_APP13 = 0xED; // Usually Adobe
  public final static int MARKER_APP14 = 0xEE;
  public final static int MARKER_APP15 = 0xEF;

  public final static int MARKER_COM   = 0xFE; // Comment

  public final static int DATA_NONE = 0;
  public final static int DATA_USER = 1;
  public final static int DATA_PARTIAL = 2;
  public final static int DATA_COMPLETE = 3;

  protected Vector blocks;
  protected JpegCodec codec;
  
  //===========================================================================
  public Jpeg() {
    this.blocks = new Vector();
    this.codec = null;
  }
  
/**
 * Method activateCodec.
 * 
 * If the codec is not "activated", then the parsing will be limited to
 * metadata. This is somewhat faster, so only call activateCodec if you want to
 * use the image data.
 */
  public void activateCodec() {
    this.codec = new JpegCodec();
  }
  
  //===========================================================================
  public JpegCodec getCodec() {
    return this.codec;
  }
  
  //===========================================================================
  public Vector getBlocks() {
    return this.blocks;
  }
  
  //===========================================================================
  public JpegBlock getJpegBlock(int marker) {
    for (int i = 0; i < this.blocks.size(); i++) {
      JpegBlock block = (JpegBlock) this.blocks.elementAt(i);
      if (block.marker == marker) {
        return block;
      }
    }
    
    return null;
  }

  //===========================================================================
  public Vector getJpegBlocks(int marker) {
    return this.getJpegBlocks(marker, null);
  }
  
  //===========================================================================
  public Vector getJpegBlocks(int marker, String signature) {
    Vector blocks = new Vector();

    for (int i = 0; i < this.blocks.size(); i++) {
      JpegBlock block = (JpegBlock) this.blocks.elementAt(i);
      if (block.marker == marker) {
        if (signature != null) {
          // Hey... we're not refreshing the data, so the signature might be
          // wrong... but in 99.999% of the cases, the refreshed data will have
          // the same signature, so this should be ok
          if (block.getFixedString(0, signature.length()).equals(signature)) {
            blocks.add(block);
          }
        }
        else {
          blocks.add(block);
        }
      }
    }
    
    return blocks;
  }

  //===========================================================================
  public SofBlock getSofBlock() {
    for (int i = 0; i < this.blocks.size(); i++) {
      JpegBlock block = (JpegBlock) this.blocks.elementAt(i);
      if (block instanceof SofBlock) {
        return (SofBlock) block;
      }
    }
    return null;
  }
  
  //===========================================================================
  public JfifBlock getJfifBlock() {
    for (int i = 0; i < this.blocks.size(); i++) {
      JpegBlock block = (JpegBlock) this.blocks.elementAt(i);
      if (block instanceof JfifBlock) {
        return (JfifBlock) block;
      }
    }
    return null;
  }

  //===========================================================================
  public ExifBlock getExifBlock() {
    for (int i = 0; i < this.blocks.size(); i++) {
      JpegBlock block = (JpegBlock) this.blocks.elementAt(i);
      if (block instanceof ExifBlock) {
        return (ExifBlock) block;
      }
    }
    return null;
  }

  //===========================================================================
  public AdobeBlock getAdobeBlock() {
    for (int i = 0; i < this.blocks.size(); i++) {
      JpegBlock block = (JpegBlock) this.blocks.elementAt(i);
      if (block instanceof AdobeBlock) {
        return (AdobeBlock) block;
      }
    }
    return null;
  }

  //===========================================================================
  public void removeJpegBlock(JpegBlock block) {
    this.blocks.remove(block);
  }
  
  //===========================================================================
  public void removeJpegBlocks(int marker) {
    for (int i = 0; i < this.blocks.size(); i++) {
      JpegBlock block = (JpegBlock) this.blocks.elementAt(i);
      if (block.marker == marker) {
        this.blocks.remove(i);
        i--; // So we don't skip an iteration, since we removed one element
      }
    }
  }
  
  //===========================================================================
  public void addJpegBlock(JpegBlock block) {
    int i;
    JpegBlock old;
    boolean inserted, duplicate;
    
    switch (block.marker) {
    case Jpeg.MARKER_SOS:
    // SOS is always last and can't be duplicated
      this.removeJpegBlocks(Jpeg.MARKER_SOS);
      this.blocks.add(block);
      break;
      
    case Jpeg.MARKER_SOF0:
    case Jpeg.MARKER_SOF1:
    case Jpeg.MARKER_SOF2:
    case Jpeg.MARKER_SOF3:
  //case Jpeg.MARKER_SOF4:  there is no such marker
    case Jpeg.MARKER_SOF5:
    case Jpeg.MARKER_SOF6:
    case Jpeg.MARKER_SOF7:
    case Jpeg.MARKER_SOF8:
    case Jpeg.MARKER_SOF9:
    case Jpeg.MARKER_SOF10:
    case Jpeg.MARKER_SOF11:
  //case Jpeg.MARKER_SOF12:  there is no such marker
    case Jpeg.MARKER_SOF13:
    case Jpeg.MARKER_SOF14:
    case Jpeg.MARKER_SOF15:
    // SOFx go just before SOS, and there can be only one SOFx
      this.removeJpegBlocks(Jpeg.MARKER_SOF0);
      this.removeJpegBlocks(Jpeg.MARKER_SOF1);
      this.removeJpegBlocks(Jpeg.MARKER_SOF2);
      this.removeJpegBlocks(Jpeg.MARKER_SOF3);
      this.removeJpegBlocks(Jpeg.MARKER_SOF5);
      this.removeJpegBlocks(Jpeg.MARKER_SOF6);
      this.removeJpegBlocks(Jpeg.MARKER_SOF7);
      this.removeJpegBlocks(Jpeg.MARKER_SOF8);
      this.removeJpegBlocks(Jpeg.MARKER_SOF9);
      this.removeJpegBlocks(Jpeg.MARKER_SOF10);
      this.removeJpegBlocks(Jpeg.MARKER_SOF11);
      this.removeJpegBlocks(Jpeg.MARKER_SOF13);
      this.removeJpegBlocks(Jpeg.MARKER_SOF14);
      this.removeJpegBlocks(Jpeg.MARKER_SOF15);
      
      if (this.blocks.size() > 0) {
        old = (JpegBlock) this.blocks.elementAt(this.blocks.size() - 1);
        if (old.marker == Jpeg.MARKER_SOS) {
          this.blocks.insertElementAt(block, this.blocks.size() - 2);
        }
        else {
          this.blocks.add(block);
        }
      }
      else {
        this.blocks.add(block);
      }
      break;
    
    case Jpeg.MARKER_APP0:
    case Jpeg.MARKER_APP1:
    case Jpeg.MARKER_APP2:
    case Jpeg.MARKER_APP3:
    case Jpeg.MARKER_APP4:
    case Jpeg.MARKER_APP5:
    case Jpeg.MARKER_APP6:
    case Jpeg.MARKER_APP7:
    case Jpeg.MARKER_APP8:
    case Jpeg.MARKER_APP9:
    case Jpeg.MARKER_APP10:
    case Jpeg.MARKER_APP11:
    case Jpeg.MARKER_APP12:
    case Jpeg.MARKER_APP13:
    case Jpeg.MARKER_APP14:
    case Jpeg.MARKER_APP15:
    // APPx go in order, and are the first blocks
    // Duplicate markers go in order of insertion
      inserted = false;
      loop: for (i = 0; i < this.blocks.size(); i++) {
        old = (JpegBlock) this.blocks.elementAt(i);
        switch (old.marker) {
        case Jpeg.MARKER_APP0:
        case Jpeg.MARKER_APP1:
        case Jpeg.MARKER_APP2:
        case Jpeg.MARKER_APP3:
        case Jpeg.MARKER_APP4:
        case Jpeg.MARKER_APP5:
        case Jpeg.MARKER_APP6:
        case Jpeg.MARKER_APP7:
        case Jpeg.MARKER_APP8:
        case Jpeg.MARKER_APP9:
        case Jpeg.MARKER_APP10:
        case Jpeg.MARKER_APP11:
        case Jpeg.MARKER_APP12:
        case Jpeg.MARKER_APP13:
        case Jpeg.MARKER_APP14:
        case Jpeg.MARKER_APP15:
          if (block.marker < old.marker) {
            this.blocks.insertElementAt(block, i);
            inserted = true;
            break loop;
          }
          break;
        default:
          this.blocks.insertElementAt(block, i);
          inserted = true;
          break loop;
        }
      }
      if (!inserted) {
        this.blocks.add(block);
      }
      break;
    
    default:
    // Anything else just goes just before any SOFx
    // But right after other blocks with the same marker if there's any
      duplicate = false;
      inserted = false;
      loop: for (i = 0; i < this.blocks.size(); i++) {
        old = (JpegBlock) this.blocks.elementAt(i);
        if (old.marker == block.marker) {
          duplicate = true;
        }
        else if (duplicate && (old.marker != block.marker)) {
          this.blocks.insertElementAt(block, i);
        }
        else {
          switch (old.marker) {
          case Jpeg.MARKER_SOF0:
          case Jpeg.MARKER_SOF1:
          case Jpeg.MARKER_SOF2:
          case Jpeg.MARKER_SOF3:
        //case Jpeg.MARKER_SOF4:  there is no such marker
          case Jpeg.MARKER_SOF5:
          case Jpeg.MARKER_SOF6:
          case Jpeg.MARKER_SOF7:
          case Jpeg.MARKER_SOF8:
          case Jpeg.MARKER_SOF9:
          case Jpeg.MARKER_SOF10:
          case Jpeg.MARKER_SOF11:
        //case Jpeg.MARKER_SOF12:  there is no such marker
          case Jpeg.MARKER_SOF13:
          case Jpeg.MARKER_SOF14:
          case Jpeg.MARKER_SOF15:
          case Jpeg.MARKER_SOS:
            this.blocks.insertElementAt(block, i);
            inserted = true;
            break loop;
          }
        }
      }
      if (!inserted)
        this.blocks.add(block);
      break;
    }
  }

  //===========================================================================
  public void copyMinimumBlocksFrom(Jpeg source) throws JpegException {
  // Just copy any Non APPx, COM or RSTx blocks
  // (That is: SOFx, SOS and Table markers)
  // And then, if the source has a JFIF block, copy it, else create a new one
    Vector sourceBlocks = source.getBlocks();
    JpegBlock block;
    
    this.blocks.clear();

    block = (JpegBlock) sourceBlocks.elementAt(0);
    if (block instanceof JfifBlock) {
      this.blocks.add(new JfifBlock(block.getData(), this));
    }
    else {
      this.blocks.add(new JfifBlock(this));
    }
    
    for (int i = 0; i < sourceBlocks.size(); i++) {
      block = (JpegBlock) sourceBlocks.elementAt(i);
      switch(block.getMarker()) {
      case Jpeg.MARKER_DHT:
      case Jpeg.MARKER_DAC:
      case Jpeg.MARKER_DQT:
      case Jpeg.MARKER_DNL:
      case Jpeg.MARKER_DRI:
      case Jpeg.MARKER_DHP:
      case Jpeg.MARKER_EXP:
      case Jpeg.MARKER_SOS:
        this.blocks.add(new JpegBlock(block.getMarker(), 
          block.getData(), this));
        break;
      case Jpeg.MARKER_SOF0:
      case Jpeg.MARKER_SOF1:
      case Jpeg.MARKER_SOF2:
      case Jpeg.MARKER_SOF3:
    //case Jpeg.MARKER_SOF4:  there is no such marker
      case Jpeg.MARKER_SOF5:
      case Jpeg.MARKER_SOF6:
      case Jpeg.MARKER_SOF7:
      case Jpeg.MARKER_SOF8:
      case Jpeg.MARKER_SOF9:
      case Jpeg.MARKER_SOF10:
      case Jpeg.MARKER_SOF11:
    //case Jpeg.MARKER_SOF12:  there is no such marker
      case Jpeg.MARKER_SOF13:
      case Jpeg.MARKER_SOF14:
      case Jpeg.MARKER_SOF15:
        this.blocks.add(new SofBlock(block.getMarker(), 
          block.getData(), this));
        break;
      }
    }
  }
  
  //===========================================================================
  public void read(InputStream input) throws IOException, JpegException {
    int c1, c2;

    this.blocks.clear();

    // Look for the JPEG signature
    c1 = input.read();
    c2 = input.read();
    if (c1 != Jpeg.MARKER_HEADER || c2 != Jpeg.MARKER_SOI) {   // (0xFF + SOI)
      throw new JpegException("File is not a JPEG");
    }

    // And read all the blocks
    JpegBlock block;
    int marker, length;
    byte[] data;
    do {
      // First, check for the MARKER_HEADER (0xFF) byte(s), 
      // and then look for the next byte (the marker itself)
      marker = input.read();
      if (marker == Jpeg.MARKER_HEADER) {
        while (marker == Jpeg.MARKER_HEADER) {
          marker = input.read();
        }
      }
      else {
        throw new JpegException("Extraneous data");
      }

      if (marker == -1) {
        throw new JpegException("Unexpected EOF");
      }
      
      if (marker == Jpeg.MARKER_SOS) { 
      // This is the last field, so it doesn't has a length
        Vector buffers;
        Vector lengths;
        buffers = new Vector();
        lengths = new Vector();
    
        int actual;
        byte[] buffer;
        do {
          buffer = new byte[64 * 1024];
          actual = input.read(buffer);
          if (actual != -1) {
            buffers.add(buffer);
            lengths.add(Integer.valueOf(actual));
          }
        } while (actual != -1);
    
        length = 0;
        for (int i = 0; i < lengths.size(); i++) {
          length += ((Integer)lengths.elementAt(i)).intValue();
        }
    
        data = new byte[length];
        int pos = 0;
        for (int i = 0; i < buffers.size(); i++) {
          buffer = (byte[])buffers.elementAt(i);
          actual = ((Integer)lengths.elementAt(i)).intValue();
          System.arraycopy(buffer, 0, data, pos, actual);
          pos += actual;
        }
      }
      else {
        length = (input.read() * 256) + input.read();
        if (length < 2) {
          throw new JpegException("Extraneous data");
        }
        length = length - 2; // The length counts itself (2 bytes)
        data = new byte[length];
        int actual = input.read(data);
        if (actual != length) {
          throw new JpegException("Unexpected EOF");
        }
      }

      block = null;
      switch (marker) {
      case Jpeg.MARKER_SOF0:
      case Jpeg.MARKER_SOF1:
      case Jpeg.MARKER_SOF2:
      case Jpeg.MARKER_SOF3:
    //case Jpeg.MARKER_SOF4:  there is no such marker
      case Jpeg.MARKER_SOF5:
      case Jpeg.MARKER_SOF6:
      case Jpeg.MARKER_SOF7:
      case Jpeg.MARKER_SOF8:
      case Jpeg.MARKER_SOF9:
      case Jpeg.MARKER_SOF10:
      case Jpeg.MARKER_SOF11:
    //case Jpeg.MARKER_SOF12:  there is no such marker
      case Jpeg.MARKER_SOF13:
      case Jpeg.MARKER_SOF14:
      case Jpeg.MARKER_SOF15:
        block = new SofBlock(marker, data, this);
        break;
      case Jpeg.MARKER_DQT:
        block = new DqtBlock(data, this);
        break;
      case Jpeg.MARKER_DHT:
        block = new DhtBlock(data, this);
        break;
      case Jpeg.MARKER_SOS:
        block = new SosBlock(data, this);
        break;
      case Jpeg.MARKER_APP0: // JFIF\0
        if ((data[0] == 'J') && (data[1] == 'F') && (data[2] == 'I')
        && (data[3] == 'F') && (data[4] == 0)) {
          block = new JfifBlock(data, this);
        }
        break;
      case Jpeg.MARKER_APP1: // Exif\0\0
        if ((data[0] == 'E') && (data[1] == 'x') && (data[2] == 'i')
        && (data[3] == 'f') && (data[4] == 0) && (data[5] == 0)) {
          block = new ExifBlock(data, this);
        }
        break;
      case Jpeg.MARKER_APP13: // Photoshop 3.0\0
        if ((data[0] == 'P') && (data[1] == 'h') && (data[2] == 'o')
        && (data[3] == 't') && (data[4] == 'o') && (data[5] == 's')
        && (data[6] == 'h') && (data[7] == 'o') && (data[8] == 'p')
        && (data[9] == ' ') && (data[10] == '3') && (data[11] == '.')
        && (data[12] == '0') && (data[13] == 0)) {
          block = new AdobeBlock(data, this);
        }
        break;
      }
      
      if (block == null) {
        block = new JpegBlock(marker, data, this);
      }
      
      this.blocks.add(block);
    } while (block.marker != Jpeg.MARKER_SOS);
  }

  //===========================================================================
  public boolean write(OutputStream output) throws IOException, JpegException {
    output.write(Jpeg.MARKER_HEADER);
    output.write(Jpeg.MARKER_SOI);

    int i = 0;
    JpegBlock block;

    while (i < this.blocks.size()) {
      block = (JpegBlock) this.blocks.elementAt(i);
      output.write(Jpeg.MARKER_HEADER);
      output.write(block.marker);
      
      byte[] data = block.getData();
      if (block.marker != Jpeg.MARKER_SOS) {  
        // SOS: Start of scan... last marker... has no length
        output.write(((data.length + 2) & 0x0000FF00) >> 8);
        output.write(((data.length + 2) & 0x000000FF) >> 0);
      }
      output.write(data);
      i++;
    }
    
        
    return false;
  }
  
//=============================================================================
//=============================================================================
}


//=============================================================================
//=============================================================================
class JpegShortcuts {
  //===========================================================================
  public static String byte2hex(int i) {
    char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 
                     'A', 'B', 'C', 'D', 'E', 'F'};

    char[] result = new char[4];
    result[0] = '0';
    result[1] = 'x';
    result[2] = digits[(i & 0x00F0) >> 4];
    result[3] = digits[(i & 0x000F) >> 0];
    
    return (new String(result));
  }

  //===========================================================================
  public static String long2hex(int i) {
    char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 
                     'A', 'B', 'C', 'D', 'E', 'F'};

    char[] result = new char[6];
    result[0] = '0';
    result[1] = 'x';
    result[2] = digits[(i & 0xF000) >> 12];
    result[3] = digits[(i & 0x0F00) >> 8];
    result[4] = digits[(i & 0x00F0) >> 4];
    result[5] = digits[(i & 0x000F) >> 0];
    
    return (new String(result));
  }

//=============================================================================
//=============================================================================
}
