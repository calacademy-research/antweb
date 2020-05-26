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
public class SofBlock extends JpegBlock {
  public final static int ENCODING_UNKNOWN = -1;
  public final static int ENCODING_BASELINE = 0;
  public final static int ENCODING_EXTENDED_SEQUENTIAL = 1;
  public final static int ENCODING_PROGRESSIVE = 2;
  public final static int ENCODING_OTHER = -2;
  
  protected int sofEncoding;
  protected int sofImageWidth;
  protected int sofImageHeight;
  protected int sofChannelCount;
  protected int sofBitsPerChannel;
  
  //===========================================================================
  public SofBlock() throws JpegException {
    super(Jpeg.MARKER_SOF0);
    throw new JpegException("Can't create a new SOFx block");
  }
  
  //===========================================================================
  public SofBlock(int marker, byte[] data, Jpeg jpeg) throws JpegException {
    super(marker, data, jpeg);
  }

  //===========================================================================
  public HashMap getInformation() {
    HashMap info = super.getInformation();
    
    info.put("Jpeg Block", "SOF" + (this.getMarker() & ~Jpeg.MARKER_SOF0));
    //info.remove("Jpeg Block Length");
    switch (this.sofEncoding) {
    case SofBlock.ENCODING_BASELINE:
      info.put("Image Encoding", "Baseline");
      break;
    case SofBlock.ENCODING_EXTENDED_SEQUENTIAL:
      info.put("Image Encoding", "Extended Sequential (Non-Baseline)");
      break;
    case SofBlock.ENCODING_PROGRESSIVE:
      info.put("Image Encoding", "Progressive");
      break;
    case SofBlock.ENCODING_OTHER:
      info.put("Image Encoding", "Other");
      break;
    case SofBlock.ENCODING_UNKNOWN:
    default:
      info.put("Image Encoding", "Unknown");
      break;
    }
     
    info.put("Image Width", Integer.valueOf(this.sofImageWidth));
    info.put("Image Height", Integer.valueOf(this.sofImageHeight));
    info.put("Channels", Integer.valueOf(this.sofChannelCount));
    info.put("Bits Per Channel", Integer.valueOf(this.sofBitsPerChannel));

    return info;
  }

  //===========================================================================
  public int getEncoding() {
    return this.sofEncoding;
  }
  
  //===========================================================================
  public int getImageWidth() {
    return this.sofImageWidth;
  }

  //===========================================================================
  public int getImageHeight() {
    return this.sofImageHeight;
  }

  //===========================================================================
  public int getChannelCount() {
    return this.sofChannelCount;
  }

  //===========================================================================
  public int bitsPerChannel() {
    return this.sofBitsPerChannel;
  }

  //===========================================================================
  public void parseData() throws JpegException {
    switch (this.getMarker()) {
    case Jpeg.MARKER_SOF0:
      this.sofEncoding = SofBlock.ENCODING_BASELINE;
      break;
    case Jpeg.MARKER_SOF1:
      this.sofEncoding = SofBlock.ENCODING_EXTENDED_SEQUENTIAL;
      break;
    case Jpeg.MARKER_SOF2:
      this.sofEncoding = SofBlock.ENCODING_PROGRESSIVE;
      break;
    case Jpeg.MARKER_SOF3:
    //case Jpeg.MARKER_SOF4:   there is no such marker
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
      this.sofEncoding = SofBlock.ENCODING_OTHER;
      break;
    default:
      this.sofEncoding = SofBlock.ENCODING_UNKNOWN;
      break;
    }

    this.sofBitsPerChannel = this.getByte(0);
    this.sofImageHeight = this.getShort(1);
    this.sofImageWidth = this.getShort(3);
    this.sofChannelCount = this.getByte(5);

    int pos = 6;
    JpegCodec codec = this.getCodec();
    if (codec != null) {
      codec.setEncoding(this.sofEncoding);
      codec.setChannelCount(this.sofChannelCount);
      codec.setBitsPerChannel(this.sofBitsPerChannel);
      codec.setImageDimensions(this.sofImageWidth, this.sofImageHeight);

      if (this.sofChannelCount > 0) {
        for (int i = 0; i < this.sofChannelCount; i++) {
          int channelId = this.getByte(pos + (i * 3));
          int b = this.getByte(pos + (i * 3) + 1);
          int hSamples = (b & 0x00F0) >> 4;
          int vSamples = (b & 0x000F) >> 0;
          int qTableId = this.getByte( pos + (i * 3) + 2);
          codec.setChannelInfo(channelId, hSamples, vSamples, qTableId);
        }
      }
    }    
  }

//=============================================================================
//=============================================================================
}
