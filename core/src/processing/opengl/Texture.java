/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-21 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.opengl;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.opengl.PGraphicsOpenGL.GLResourceTexture;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * This class wraps an OpenGL texture.
 * By Andres Colubri
 *
 */
public class Texture implements PConstants {
  /**
   * Texture with normalized UV.
   */
  protected static final int TEX2D   = 0;
  /**
   * Texture with un-normalized UV.
   */
  protected static final int TEXRECT = 1;

  /** Point sampling: both magnification and minification filtering are set
   * to nearest */
  protected static final int POINT = 2;
  /** Linear sampling: magnification filtering is nearest, minification set
   * to linear */
  protected static final int LINEAR = 3;
  /** Bilinear sampling: both magnification filtering is set to linear and
   * minification either to linear-mipmap-nearest (linear interpolation is used
   * within a mipmap, but not between different mipmaps). */
  protected static final int BILINEAR = 4;
  /** Trilinear sampling: magnification filtering set to linear, minification to
   * linear-mipmap-linear, which offers the best mipmap quality since linear
   * interpolation to compute the value in each of two maps and then
   * interpolates linearly between these two values. */
  protected static final int TRILINEAR = 5;


  // This constant controls how many times pixelBuffer and rgbaPixels can be
  // accessed before they are not released anymore. The idea is that if they
  // have been used only a few times, it doesn't make sense to keep them around.
  protected static final int MAX_UPDATES = 10;

  // The minimum amount of free JVM's memory (in MB) before pixelBuffer and
  // rgbaPixels are released every time after they are used.
  protected static final int MIN_MEMORY = 5;

  public int width, height;

  public int glName;
  public int glTarget;
  public int glFormat;
  public int glMinFilter;
  public int glMagFilter;
  public int glWrapS;
  public int glWrapT;
  public int glWidth;
  public int glHeight;
  private GLResourceTexture glres;

  protected PGraphicsOpenGL pg;
  protected PGL pgl;                // The interface between Processing and OpenGL.
  protected int context;            // The context that created this texture.
  protected boolean colorBuffer;    // true if it is the color attachment of
                                    // FrameBuffer object.

  protected boolean usingMipmaps;
  protected boolean usingRepeat;
  protected float maxTexcoordU;
  protected float maxTexcoordV;
  protected boolean bound;

  protected boolean invertedX;
  protected boolean invertedY;

  protected int[] rgbaPixels = null;
  protected IntBuffer pixelBuffer = null;

  protected int[] edgePixels = null;
  protected IntBuffer edgeBuffer = null;

  protected FrameBuffer tempFbo = null;
  protected int pixBufUpdateCount = 0;
  protected int rgbaPixUpdateCount = 0;

  /** Modified portion of the texture */
  protected boolean modified;
  protected int mx1, my1, mx2, my2;

  protected Object bufferSource;
  protected LinkedList<BufferData> bufferCache = null;
  protected LinkedList<BufferData> usedBuffers = null;
  protected Method disposeBufferMethod;
  public static final int MAX_BUFFER_CACHE_SIZE = 3;

  ////////////////////////////////////////////////////////////

  // Constructors.


  public Texture(PGraphicsOpenGL pg) {
    this.pg = pg;
    pgl = pg.pgl;
    context = pgl.createEmptyContext();

    colorBuffer = false;

    glName = 0;
  }


  /**
   * Creates an instance of PTexture with size width x height. The texture is
   * initialized (empty) to that size.
   * @param width  int
   * @param height  int
   */
  public Texture(PGraphicsOpenGL pg, int width, int height) {
    this(pg, width, height, new Parameters());
  }


  /**
   * Creates an instance of PTexture with size width x height and with the
   * specified parameters. The texture is initialized (empty) to that size.
   * @param width int
   * @param height int
   * @param params Parameters
   */
  public Texture(PGraphicsOpenGL pg, int width, int height, Object params) {
    this.pg = pg;
    pgl = pg.pgl;
    context = pgl.createEmptyContext();

    colorBuffer = false;

    glName = 0;

    init(width, height, (Parameters)params);
  }


  ////////////////////////////////////////////////////////////

  // Init, resize methods


  /**
   * Sets the size of the image and texture to width x height. If the texture is
   * already initialized, it first destroys the current OpenGL texture object
   * and then creates a new one with the specified size.
   * @param width int
   * @param height int
   */
  public void init(int width, int height) {
    Parameters params;
    if (0 < glName) {
      // Re-initializing a pre-existing texture.
      // We use the current parameters as default:
      params = getParameters();
    } else {
      // Just built-in default parameters otherwise:
      params = new Parameters();
    }
    init(width, height, params);
  }


  /**
   * Sets the size of the image and texture to width x height, and the
   * parameters of the texture to params. If the texture is already initialized,
   * it first destroys the current OpenGL texture object and then creates a new
   * one with the specified size.
   * @param width int
   * @param height int
   * @param params GLTextureParameters
   */
  public void init(int width, int height, Parameters params)  {
    setParameters(params);
    setSize(width, height);
    allocate();
  }


  /**
   * Initializes the texture using GL parameters
   */
  public void init(int width, int height,
                   int glName, int glTarget, int glFormat,
                   int glWidth, int glHeight,
                   int glMinFilter, int glMagFilter,
                   int glWrapS, int glWrapT) {
    this.width = width;
    this.height = height;

    this.glName = glName;
    this.glTarget = glTarget;
    this.glFormat = glFormat;
    this.glWidth = glWidth;
    this.glHeight = glHeight;
    this.glMinFilter = glMinFilter;
    this.glMagFilter = glMagFilter;
    this.glWrapS = glWrapS;
    this.glWrapT = glWrapT;

    maxTexcoordU = (float)width / glWidth;
    maxTexcoordV = (float)height / glHeight;

    usingMipmaps = glMinFilter == PGL.LINEAR_MIPMAP_NEAREST ||
                   glMinFilter == PGL.LINEAR_MIPMAP_LINEAR;

    usingRepeat = glWrapS == PGL.REPEAT || glWrapT == PGL.REPEAT;
  }


  public void resize(int wide, int high) {
    // Disposing current resources.
    dispose();

    // Creating new texture with the appropriate size.
    Texture tex = new Texture(pg, wide, high, getParameters());

    // Copying the contents of this texture into tex.
    tex.set(this);

    // Now, overwriting "this" with tex.
    copyObject(tex);

    // Nullifying some utility objects so they are recreated with the
    // appropriate size when needed.
    tempFbo = null;
  }


  /**
   * Returns true if the texture has been initialized.
   * @return boolean
   */
  public boolean available()  {
    return 0 < glName;
  }


  ////////////////////////////////////////////////////////////

  // Set methods


  public void set(Texture tex) {
    copyTexture(tex, 0, 0, tex.width, tex.height, true);
  }


  public void set(Texture tex, int x, int y, int w, int h) {
    copyTexture(tex, x, y, w, h, true);
  }


  public void set(int texTarget, int texName, int texWidth, int texHeight,
                  int w, int h) {
    copyTexture(texTarget, texName, texWidth, texHeight, 0, 0, w, h, true);
  }


  public void set(int texTarget, int texName, int texWidth, int texHeight,
                  int target, int tex, int x, int y, int w, int h) {
    copyTexture(texTarget, texName, texWidth, texHeight, x, y, w, h, true);
  }


  public void set(int[] pixels) {
    set(pixels, 0, 0, width, height, ARGB);
  }


  public void set(int[] pixels, int format) {
    set(pixels, 0, 0, width, height, format);
  }


  public void set(int[] pixels, int x, int y, int w, int h) {
    set(pixels, x, y, w, h, ARGB);
  }


  public void set(int[] pixels, int x, int y, int w, int h, int format) {
    if (pixels == null) {
      PGraphics.showWarning("The pixels array is null.");
      return;
    }
    if (pixels.length < w * h) {
      PGraphics.showWarning("The pixel array has a length of " +
                            pixels.length + ", but it should be at least " +
                            w * h);
      return;
    }

    if (pixels.length == 0 || w == 0 || h == 0) {
      return;
    }

    boolean enabledTex = false;
    if (!pgl.texturingIsEnabled(glTarget)) {
      pgl.enableTexturing(glTarget);
      enabledTex = true;
    }
    pgl.bindTexture(glTarget, glName);

    loadPixels(w * h);
    convertToRGBA(pixels, format, w, h);
    if (invertedX) flipArrayOnX(rgbaPixels, 1);
    if (invertedY) flipArrayOnY(rgbaPixels, 1);
    updatePixelBuffer(rgbaPixels);
    pgl.texSubImage2D(glTarget, 0, x, y, w, h, PGL.RGBA, PGL.UNSIGNED_BYTE,
                      pixelBuffer);
    fillEdges(x, y, w, h);

    if (usingMipmaps) {
      if (PGraphicsOpenGL.autoMipmapGenSupported) {
        pgl.generateMipmap(glTarget);
      } else {
        manualMipmap();
      }
    }

    pgl.bindTexture(glTarget, 0);
    if (enabledTex) {
      pgl.disableTexturing(glTarget);
    }

    releasePixelBuffer();
    releaseRGBAPixels();

    updateTexels(x, y, w, h);
  }


  ////////////////////////////////////////////////////////////

  // Native set methods


  public void setNative(int[] pixels) {
    setNative(pixels, 0, 0, width, height);
  }


  public void setNative(int[] pixels, int x, int y, int w, int h) {
    updatePixelBuffer(pixels);
    setNative(pixelBuffer, x, y, w, h);
    releasePixelBuffer();
  }


  public void setNative(IntBuffer pixBuf, int x, int y, int w, int h) {
    if (pixBuf == null) {
      pixBuf = null;
      PGraphics.showWarning("The pixel buffer is null.");
      return;
    }
    if (pixBuf.capacity() < w * h) {
      PGraphics.showWarning("The pixel bufer has a length of " +
                            pixBuf.capacity()  + ", but it should be at least " +
                            w * h);
      return;
    }

    if (pixBuf.capacity()  == 0) {
      // Nothing to do (means that w == h == 0) but not an erroneous situation
      return;
    }

    boolean enabledTex = false;
    if (!pgl.texturingIsEnabled(glTarget)) {
      pgl.enableTexturing(glTarget);
      enabledTex = true;
    }
    pgl.bindTexture(glTarget, glName);

    pgl.texSubImage2D(glTarget, 0, x, y, w, h, PGL.RGBA, PGL.UNSIGNED_BYTE,
                      pixBuf);
    fillEdges(x, y, w, h);

    if (usingMipmaps) {
      if (PGraphicsOpenGL.autoMipmapGenSupported) {
        pgl.generateMipmap(glTarget);
      } else {
        manualMipmap();
      }
    }
    pgl.bindTexture(glTarget, 0);
    if (enabledTex) {
      pgl.disableTexturing(glTarget);
    }

    updateTexels(x, y, w, h);
  }


  ////////////////////////////////////////////////////////////

  // Get methods


  /**
   * Copy texture to pixels. Involves video memory to main memory transfer (slow).
   */
  public void get(int[] pixels) {
    if (pixels == null) {
      throw new RuntimeException("Trying to copy texture to null pixels array");
    }
    if (pixels.length != width * height) {
      throw new RuntimeException("Trying to copy texture to pixels array of " +
                                 "wrong size");
    }

    if (tempFbo == null) {
      tempFbo = new FrameBuffer(pg, glWidth, glHeight);
    }

    // Attaching the texture to the color buffer of a FBO, binding the FBO and
    // reading the pixels from the current draw buffer (which is the color
    // buffer of the FBO).
    tempFbo.setColorBuffer(this);
    pg.pushFramebuffer();
    pg.setFramebuffer(tempFbo);
    tempFbo.readPixels();
    pg.popFramebuffer();

    tempFbo.getPixels(pixels);
    convertToARGB(pixels);

    if (invertedX) flipArrayOnX(pixels, 1);
    if (invertedY) flipArrayOnY(pixels, 1);
  }


  ////////////////////////////////////////////////////////////

  // Put methods (the source texture is not resized to cover the entire
  // destination).


  public void put(Texture tex) {
    copyTexture(tex, 0, 0, tex.width, tex.height, false);
  }


  public void put(Texture tex, int x, int y, int w, int h) {
    copyTexture(tex, x, y, w, h, false);
  }


  public void put(int texTarget, int texName, int texWidth, int texHeight,
                  int w, int h) {
    copyTexture(texTarget, texName, texWidth, texHeight, 0, 0, w, h, false);
  }


  public void put(int texTarget, int texName, int texWidth, int texHeight,
                  int target, int tex, int x, int y, int w, int h) {
    copyTexture(texTarget, texName, texWidth, texHeight, x, y, w, h, false);
  }


  ////////////////////////////////////////////////////////////

  // Get OpenGL parameters


  /**
   * Returns true or false whether or not the texture is using mipmaps.
   * @return boolean
   */
  public boolean usingMipmaps()  {
    return usingMipmaps;
  }


  public void usingMipmaps(boolean mipmaps, int sampling)  {
    int glMagFilter0 = glMagFilter;
    int glMinFilter0 = glMinFilter;
    if (mipmaps) {
      if (sampling == POINT) {
        glMagFilter = PGL.NEAREST;
        glMinFilter = PGL.NEAREST;
        usingMipmaps = false;
      } else if (sampling == LINEAR)  {
        glMagFilter = PGL.NEAREST;
        glMinFilter =
          PGL.MIPMAPS_ENABLED ? PGL.LINEAR_MIPMAP_NEAREST : PGL.LINEAR;
        usingMipmaps = true;
      } else if (sampling == BILINEAR)  {
        glMagFilter = PGL.LINEAR;
        glMinFilter =
          PGL.MIPMAPS_ENABLED ? PGL.LINEAR_MIPMAP_NEAREST : PGL.LINEAR;
        usingMipmaps = true;
      } else if (sampling == TRILINEAR)  {
        glMagFilter = PGL.LINEAR;
        glMinFilter =
          PGL.MIPMAPS_ENABLED ? PGL.LINEAR_MIPMAP_LINEAR : PGL.LINEAR;
        usingMipmaps = true;
      } else {
        throw new RuntimeException("Unknown texture filtering mode");
      }
    } else {
      usingMipmaps = false;
      if (sampling == POINT) {
        glMagFilter = PGL.NEAREST;
        glMinFilter = PGL.NEAREST;
      } else if (sampling == LINEAR)  {
        glMagFilter = PGL.NEAREST;
        glMinFilter = PGL.LINEAR;
      } else if (sampling == BILINEAR || sampling == TRILINEAR)  {
        glMagFilter = PGL.LINEAR;
        glMinFilter = PGL.LINEAR;
      } else {
        throw new RuntimeException("Unknown texture filtering mode");
      }
    }

    if (glMagFilter0 != glMagFilter || glMinFilter0 != glMinFilter) {
      bind();
      pgl.texParameteri(glTarget, PGL.TEXTURE_MIN_FILTER, glMinFilter);
      pgl.texParameteri(glTarget, PGL.TEXTURE_MAG_FILTER, glMagFilter);
      if (usingMipmaps) {
        if (PGraphicsOpenGL.autoMipmapGenSupported) {
          pgl.generateMipmap(glTarget);
        } else {
          manualMipmap();
        }
      }
      unbind();
    }
  }


  /**
   * Returns true or false whether or not the texture is using repeat wrap mode
   * along either U or V directions.
   * @return boolean
   */
  public boolean usingRepeat()  {
    return usingRepeat;
  }


  public void usingRepeat(boolean repeat)  {
    if (repeat) {
      glWrapS = PGL.REPEAT;
      glWrapT = PGL.REPEAT;
      usingRepeat = true;
    } else {
      glWrapS = PGL.CLAMP_TO_EDGE;
      glWrapT = PGL.CLAMP_TO_EDGE;
      usingRepeat = false;
    }

    bind();
    pgl.texParameteri(glTarget, PGL.TEXTURE_WRAP_S, glWrapS);
    pgl.texParameteri(glTarget, PGL.TEXTURE_WRAP_T, glWrapT);
    unbind();
  }


  /**
   * Returns the maximum possible value for the texture coordinate U
   * (horizontal).
   * @return float
   */
  public float maxTexcoordU() {
    return maxTexcoordU;
  }


  /**
   * Returns the maximum possible value for the texture coordinate V (vertical).
   * @return float
   */
  public float maxTexcoordV() {
    return maxTexcoordV;
  }


  /**
   * Returns true if the texture is inverted along the horizontal direction.
   * @return boolean;
   */
  public boolean invertedX() {
    return invertedX;
  }


  /**
   * Sets the texture as inverted or not along the horizontal direction.
   * @param v boolean;
   */
  public void invertedX(boolean v) {
    invertedX = v;
  }


  /**
   * Returns true if the texture is inverted along the vertical direction.
   * @return boolean;
   */
  public boolean invertedY() {
    return invertedY;
  }


  /**
   * Sets the texture as inverted or not along the vertical direction.
   * @param v boolean;
   */
  public void invertedY(boolean v) {
    invertedY = v;
  }


  public int currentSampling() {
    if (glMagFilter == PGL.NEAREST && glMinFilter == PGL.NEAREST) {
      return POINT;
    } else if (glMagFilter == PGL.NEAREST &&
               glMinFilter == (PGL.MIPMAPS_ENABLED ? PGL.LINEAR_MIPMAP_NEAREST : PGL.LINEAR)) {
      return LINEAR;
    } else if (glMagFilter == PGL.LINEAR &&
               glMinFilter == (PGL.MIPMAPS_ENABLED ? PGL.LINEAR_MIPMAP_NEAREST : PGL.LINEAR)) {
      return BILINEAR;
    } else if (glMagFilter == PGL.LINEAR &&
               glMinFilter == PGL.LINEAR_MIPMAP_LINEAR) {
      return TRILINEAR;
    } else {
      return -1;
    }
  }

  ////////////////////////////////////////////////////////////

  // Bind/unbind


  public void bind() {
    // Binding a texture automatically enables texturing for the
    // texture target from that moment onwards. Unbinding the texture
    // won't disable texturing.

    if (!pgl.texturingIsEnabled(glTarget)) {
      pgl.enableTexturing(glTarget);
    }
    pgl.bindTexture(glTarget, glName);
    bound = true;
  }


  public void unbind() {
    if (pgl.textureIsBound(glTarget, glName)) {
      // We don't want to unbind another texture
      // that might be bound instead of this one.
      if (!pgl.texturingIsEnabled(glTarget)) {
        pgl.enableTexturing(glTarget);
        pgl.bindTexture(glTarget, 0);
        pgl.disableTexturing(glTarget);
      } else {
        pgl.bindTexture(glTarget, 0);
      }
    }
    bound = false;
  }


  public boolean bound() {
    // A true result might not necessarily mean that texturing is enabled
    // (a texture can be bound to the target, but texturing is disabled).
    return bound;
  }


  //////////////////////////////////////////////////////////////

  // Modified flag


  public boolean isModified() {
    return modified;
  }


  public void setModified() {
    modified = true;
  }


  public void setModified(boolean m) {
    modified = m;
  }


  public int getModifiedX1() {
    return mx1;
  }


  public int getModifiedX2() {
    return mx2;
  }


  public int getModifiedY1() {
    return my1;
  }


  public int getModifiedY2() {
    return my2;
  }


  public void updateTexels() {
    updateTexelsImpl(0, 0, width, height);
  }


  public void updateTexels(int x, int y, int w, int h) {
    updateTexelsImpl(x, y, w, h);
  }


  protected void updateTexelsImpl(int x, int y, int w, int h) {
    int x2 = x + w;
    int y2 = y + h;

    if (!modified) {
      mx1 = PApplet.max(0, x);
      mx2 = PApplet.min(width - 1, x2);
      my1 = PApplet.max(0, y);
      my2 = PApplet.min(height - 1, y2);
      modified = true;

    } else {
      if (x < mx1) mx1 = PApplet.max(0, x);
      if (x > mx2) mx2 = PApplet.min(width - 1, x);
      if (y < my1) my1 = PApplet.max(0, y);
      if (y > my2) my2 = y;

      if (x2 < mx1) mx1 = PApplet.max(0, x2);
      if (x2 > mx2) mx2 = PApplet.min(width - 1, x2);
      if (y2 < my1) my1 = PApplet.max(0, y2);
      if (y2 > my2) my2 = PApplet.min(height - 1, y2);
    }
  }


  protected void loadPixels(int len) {
    if (rgbaPixels == null || rgbaPixels.length < len) {
      rgbaPixels = new int[len];
    }
  }


  protected void updatePixelBuffer(int[] pixels) {
    pixelBuffer = PGL.updateIntBuffer(pixelBuffer, pixels, true);
    pixBufUpdateCount++;
  }


  protected void manualMipmap() {
    // TODO: finish manual mipmap generation,
    // https://github.com/processing/processing/issues/3335
  }


  ////////////////////////////////////////////////////////////

  // Buffer sink interface.


  public void setBufferSource(Object source) {
    bufferSource = source;
    getSourceMethods();
  }


  public void copyBufferFromSource(Object natRef, ByteBuffer byteBuf,
                                   int w, int h) {
    if (bufferCache == null) {
      bufferCache = new LinkedList<>();
    }

    if (bufferCache.size() + 1 <= MAX_BUFFER_CACHE_SIZE) {
      bufferCache.add(new BufferData(natRef, byteBuf.asIntBuffer(), w, h));
    } else {
      // The buffer cache reached the maximum size, so we just dispose
      // the new buffer by adding it to the list of used buffers.
      if (usedBuffers == null) {
        usedBuffers = new LinkedList<>();
      }
      usedBuffers.add(new BufferData(natRef, byteBuf.asIntBuffer(), w, h));
    }
  }


  public void disposeSourceBuffer() {
    if (usedBuffers == null) return;

    while (0 < usedBuffers.size()) {
      BufferData data = null;
      try {
        data = usedBuffers.remove(0);
      } catch (NoSuchElementException ex) {
        PGraphics.showWarning("Cannot remove used buffer");
      }
      if (data != null) {
        data.dispose();
      }
    }
  }

  public void getBufferPixels(int[] pixels) {
    // We get the buffer either from the used buffers or the cache, giving
    // priority to the used buffers. Why? Because the used buffer was already
    // transferred to the texture, so the pixels should be in sync with the
    // texture.
    BufferData data = null;
    if (usedBuffers != null && 0 < usedBuffers.size()) {
      data = usedBuffers.getLast();
    } else if (bufferCache != null && 0 < bufferCache.size()) {
      data = bufferCache.getLast();
    }
    if (data != null) {
      if ((data.w != width) || (data.h != height)) {
        init(data.w, data.h);
      }

      data.rgbBuf.rewind();
      data.rgbBuf.get(pixels);
      convertToARGB(pixels);

      // In order to avoid a cached buffer to overwrite the texture when the
      // renderer draws the texture, and hence put the pixels put of sync, we
      // simply empty the cache.
      if (usedBuffers == null) {
        usedBuffers = new LinkedList<>();
      }
      while (0 < bufferCache.size()) {
        data = bufferCache.remove(0);
        usedBuffers.add(data);
      }
    }
  }


  public boolean hasBufferSource() {
    return bufferSource != null;
  }


  public boolean hasBuffers() {
    return bufferSource != null && bufferCache != null &&
           0 < bufferCache.size();
  }


  protected boolean bufferUpdate() {
    BufferData data = null;
    try {
      data = bufferCache.remove(0);
    } catch (NoSuchElementException ex) {
      PGraphics.showWarning("Don't have pixel data to copy to texture");
    }

    if (data != null) {
      if ((data.w != width) || (data.h != height)) {
        init(data.w, data.h);
      }
      data.rgbBuf.rewind();
      setNative(data.rgbBuf, 0, 0, width, height);

      // Putting the buffer in the used buffers list to dispose at the end of
      // draw.
      if (usedBuffers == null) {
        usedBuffers = new LinkedList<>();
      }
      usedBuffers.add(data);

      return true;
    } else {
      return false;
    }
  }


  protected void getSourceMethods() {
    try {
      disposeBufferMethod = bufferSource.getClass().
        getMethod("disposeBuffer", Object.class);
    } catch (Exception e) {
      throw new RuntimeException("Provided source object doesn't have a " +
                                 "disposeBuffer method.");
    }
  }


  ////////////////////////////////////////////////////////////

  // Utilities


  /**
   * Flips intArray along the X axis.
   * @param intArray int[]
   * @param mult int
   */
  protected void flipArrayOnX(int[] intArray, int mult)  {
    int index = 0;
    int xindex = mult * (width - 1);
    for (int x = 0; x < width / 2; x++) {
      for (int y = 0; y < height; y++)  {
        int i = index + mult * y * width;
        int j = xindex + mult * y * width;

        for (int c = 0; c < mult; c++) {
          int temp = intArray[i];
          intArray[i] = intArray[j];
          intArray[j] = temp;

          i++;
          j++;
        }

      }
      index += mult;
      xindex -= mult;
    }
  }


  /**
   * Flips intArray along the Y axis.
   * @param intArray int[]
   * @param mult int
   */
  protected void flipArrayOnY(int[] intArray, int mult) {
    int index = 0;
    int yindex = mult * (height - 1) * width;
    for (int y = 0; y < height / 2; y++) {
      for (int x = 0; x < mult * width; x++) {
        int temp = intArray[index];
        intArray[index] = intArray[yindex];
        intArray[yindex] = temp;

        index++;
        yindex++;
      }
      yindex -= mult * width * 2;
    }
  }


  /**
   * Reorders a pixel array in the given format into the order required by
   * OpenGL (RGBA) and stores it into rgbaPixels. The width and height
   * parameters are used in the YUV420 to RBGBA conversion.
   * @param pixels int[]
   * @param format int
   * @param w int
   * @param h int
   */
  protected void convertToRGBA(int[] pixels, int format, int w, int h)  {
    if (PGL.BIG_ENDIAN)  {
      switch (format) {
      case ALPHA:
        // Converting from xxxA into RGBA. RGB is set to white
        // (0xFFFFFF, i.e.: (255, 255, 255))
        for (int i = 0; i< pixels.length; i++) {
          rgbaPixels[i] = 0xFFFFFF00 | pixels[i];
        }
        break;
      case RGB:
        // Converting xRGB into RGBA. A is set to 0xFF (255, full opacity).
        for (int i = 0; i< pixels.length; i++) {
          int pixel = pixels[i];
          rgbaPixels[i] = (pixel << 8) | 0xFF;
        }
        break;
      case ARGB:
        // Converting ARGB into RGBA. Shifting RGB to 8 bits to the left,
        // and bringing A to the first byte.
        for (int i = 0; i< pixels.length; i++) {
          int pixel = pixels[i];
          rgbaPixels[i] = (pixel << 8) | ((pixel >> 24) & 0xFF);
        }
        break;
      }
    } else {
      // LITTLE_ENDIAN
      // ARGB native, and RGBA opengl means ABGR on windows
      // for the most part just need to swap two components here
      // the sun.cpu.endian here might be "false", oddly enough..
      // (that's why just using an "else", rather than check for "little")
      switch (format)  {
      case ALPHA:
        // Converting xxxA into ARGB, with RGB set to white.
        for (int i = 0; i< pixels.length; i++) {
          rgbaPixels[i] = (pixels[i] << 24) | 0x00FFFFFF;
        }
        break;
      case RGB:
        // We need to convert xRGB into ABGR,
        // so R and B must be swapped, and the x just made 0xFF.
        for (int i = 0; i< pixels.length; i++) {
          int pixel = pixels[i];
          rgbaPixels[i] = 0xFF000000 |
                          ((pixel & 0xFF) << 16) | ((pixel & 0xFF0000) >> 16) |
                          (pixel & 0x0000FF00);
        }
        break;
      case ARGB:
        // We need to convert ARGB into ABGR,
        // so R and B must be swapped, A and G just brought back in.
        for (int i = 0; i < pixels.length; i++) {
          int pixel = pixels[i];
          rgbaPixels[i] = ((pixel & 0xFF) << 16) | ((pixel & 0xFF0000) >> 16) |
                          (pixel & 0xFF00FF00);
        }
        break;
      }
    }
    rgbaPixUpdateCount++;
  }


  /**
   * Reorders an OpenGL pixel array (RGBA) into ARGB. The array must be
   * of size width * height.
   * @param pixels int[]
   */
  protected void convertToARGB(int[] pixels) {
    int t = 0;
    int p = 0;
    if (PGL.BIG_ENDIAN) {
      // RGBA to ARGB conversion: shifting RGB 8 bits to the right,
      // and placing A 24 bits to the left.
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int pixel = pixels[p++];
          pixels[t++] = (pixel >>> 8) | ((pixel << 24) & 0xFF000000);
        }
      }
    } else {
      // We have to convert ABGR into ARGB, so R and B must be swapped,
      // A and G just brought back in.
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int pixel = pixels[p++];
          pixels[t++] = ((pixel & 0xFF) << 16) | ((pixel & 0xFF0000) >> 16) |
                          (pixel & 0xFF00FF00);
        }
      }
    }
  }


  ///////////////////////////////////////////////////////////

  // Allocate/release texture.


  protected void setSize(int w, int h) {
    width = w;
    height = h;

    if (PGraphicsOpenGL.npotTexSupported) {
      glWidth = w;
      glHeight = h;
    } else {
      glWidth = PGL.nextPowerOfTwo(w);
      glHeight = PGL.nextPowerOfTwo(h);
    }

    if (glWidth > PGraphicsOpenGL.maxTextureSize ||
        glHeight > PGraphicsOpenGL.maxTextureSize) {
      glWidth = glHeight = 0;
      throw new RuntimeException("Image width and height cannot be" +
                                 " larger than " +
                                 PGraphicsOpenGL.maxTextureSize +
                                 " with this graphics card.");
    }

    // If non-power-of-two textures are not supported, and the specified width
    // or height is non-power-of-two, then glWidth (glHeight) will be greater
    // than w (h) because it is chosen to be the next power of two, and this
    // quotient will give the appropriate maximum texture coordinate value given
    // this situation.
    maxTexcoordU = (float)width / glWidth;
    maxTexcoordV = (float)height / glHeight;
  }


  /**
   * Allocates the opengl texture object.
   */
  protected void allocate() {
    dispose(); // Just in the case this object is being re-allocated.

    boolean enabledTex = false;
    if (!pgl.texturingIsEnabled(glTarget)) {
      pgl.enableTexturing(glTarget);
      enabledTex = true;
    }

    context = pgl.getCurrentContext();
    glres = new GLResourceTexture(this);

    pgl.bindTexture(glTarget, glName);
    pgl.texParameteri(glTarget, PGL.TEXTURE_MIN_FILTER, glMinFilter);
    pgl.texParameteri(glTarget, PGL.TEXTURE_MAG_FILTER, glMagFilter);
    pgl.texParameteri(glTarget, PGL.TEXTURE_WRAP_S, glWrapS);
    pgl.texParameteri(glTarget, PGL.TEXTURE_WRAP_T, glWrapT);
    if (PGraphicsOpenGL.anisoSamplingSupported) {
      pgl.texParameterf(glTarget, PGL.TEXTURE_MAX_ANISOTROPY,
                        PGraphicsOpenGL.maxAnisoAmount);
    }

    // First, we use glTexImage2D to set the full size of the texture (glW/glH
    // might be diff from w/h in the case that the GPU doesn't support NPOT
    // textures)
    pgl.texImage2D(glTarget, 0, glFormat, glWidth, glHeight, 0,
                   PGL.RGBA, PGL.UNSIGNED_BYTE, null);

    // Makes sure that the texture buffer in video memory doesn't contain
    // any garbage.
    pgl.initTexture(glTarget, PGL.RGBA, width, height);

    pgl.bindTexture(glTarget, 0);
    if (enabledTex) {
      pgl.disableTexturing(glTarget);
    }
    bound = false;
  }


  /**
   * Marks the texture object for deletion.
   */
  protected void dispose() {
    if (glres != null) {
      glres.dispose();
      glres = null;
      glName = 0;
    }
  }


  protected boolean contextIsOutdated() {
    boolean outdated = !pgl.contextIsCurrent(context);
    if (outdated) {
      dispose();
    }
    return outdated;
  }


  public void colorBuffer(boolean value) {
    colorBuffer = value;
  }


  public boolean colorBuffer() {
    return colorBuffer;
  }


  ///////////////////////////////////////////////////////////

  // Utilities.


  // Copies source texture tex into this.
  protected void copyTexture(Texture tex, int x, int y, int w, int h,
                             boolean scale) {
    if (tex == null) {
      throw new RuntimeException("Source texture is null");
    }

    if (tempFbo == null) {
      tempFbo = new FrameBuffer(pg, glWidth, glHeight);
    }

    // This texture is the color (destination) buffer of the FBO.
    tempFbo.setColorBuffer(this);
    tempFbo.disableDepthTest();

    // FBO copy:
    pg.pushFramebuffer();
    pg.setFramebuffer(tempFbo);
    // Replaces anything that this texture might contain in the area being
    // replaced by the new one.
    pg.pushStyle();
    pg.blendMode(REPLACE);
    if (scale) {
      // Rendering tex into "this", and scaling the source rectangle
      // to cover the entire destination region.
      pgl.drawTexture(tex.glTarget, tex.glName, tex.glWidth, tex.glHeight,
                      0, 0, tempFbo.width, tempFbo.height, 1,
                      x, y, x + w, y + h, 0, 0, width, height);

    } else {
      // Rendering tex into "this" but without scaling so the contents
      // of the source texture fall in the corresponding texels of the
      // destination.
      pgl.drawTexture(tex.glTarget, tex.glName, tex.glWidth, tex.glHeight,
                      0, 0, tempFbo.width, tempFbo.height, 1,
                      x, y, x + w, y + h, x, y, x + w, y + h);
    }
    pgl.flush(); // Needed to make sure that the change in this texture is
                 // available immediately.
    pg.popStyle();
    pg.popFramebuffer();
    updateTexels(x, y, w, h);
  }


  // Copies source texture tex into this.
  protected void copyTexture(int texTarget, int texName,
                             int texWidth, int texHeight,
                             int x, int y, int w, int h, boolean scale) {
    if (tempFbo == null) {
      tempFbo = new FrameBuffer(pg, glWidth, glHeight);
    }

    // This texture is the color (destination) buffer of the FBO.
    tempFbo.setColorBuffer(this);
    tempFbo.disableDepthTest();

    // FBO copy:
    pg.pushFramebuffer();
    pg.setFramebuffer(tempFbo);
    // Replaces anything that this texture might contain in the area being
    // replaced by the new one.
    pg.pushStyle();
    pg.blendMode(REPLACE);
    if (scale) {
      // Rendering tex into "this", and scaling the source rectangle
      // to cover the entire destination region.
      pgl.drawTexture(texTarget, texName, texWidth, texHeight,
                      0, 0, tempFbo.width, tempFbo.height,
                      x, y, w, h, 0, 0, width, height);

    } else {
      // Rendering tex into "this" but without scaling so the contents
      // of the source texture fall in the corresponding texels of the
      // destination.
      pgl.drawTexture(texTarget, texName, texWidth, texHeight,
                      0, 0, tempFbo.width, tempFbo.height,
                      x, y, w, h, x, y, w, h);
    }
    pgl.flush(); // Needed to make sure that the change in this texture is
                 // available immediately.
    pg.popStyle();
    pg.popFramebuffer();
    updateTexels(x, y, w, h);
  }


  protected void copyObject(Texture src) {
    // The OpenGL texture of this object is replaced with the one from the
    // source object, so we delete the former to avoid resource wasting.
    dispose();

    width = src.width;
    height = src.height;

    glName = src.glName;
    glTarget = src.glTarget;
    glFormat = src.glFormat;
    glMinFilter = src.glMinFilter;
    glMagFilter = src.glMagFilter;

    glWidth= src.glWidth;
    glHeight = src.glHeight;

    usingMipmaps = src.usingMipmaps;
    usingRepeat = src.usingRepeat;
    maxTexcoordU = src.maxTexcoordU;
    maxTexcoordV = src.maxTexcoordV;

    invertedX = src.invertedX;
    invertedY = src.invertedY;
  }


  // Releases the memory used by pixelBuffer either if the buffer hasn't been
  // used many times yet, or if the JVM is running low in free memory.
  protected void releasePixelBuffer() {
    double freeMB = Runtime.getRuntime().freeMemory() / 1E6;
    if (pixBufUpdateCount < MAX_UPDATES || freeMB < MIN_MEMORY) {
      pixelBuffer = null;
    }
  }


  // Releases the memory used by rgbaPixels either if the array hasn't been
  // used many times yet, or if the JVM is running low in free memory.
  protected void releaseRGBAPixels() {
    double freeMB = Runtime.getRuntime().freeMemory() / 1E6;
    if (rgbaPixUpdateCount < MAX_UPDATES || freeMB < MIN_MEMORY) {
      rgbaPixels = null;
    }
  }


  ///////////////////////////////////////////////////////////

  // Parameter handling


  public Parameters getParameters() {
    Parameters res = new Parameters();

    if (glTarget == PGL.TEXTURE_2D)  {
      res.target = TEX2D;
    }

    if (glFormat == PGL.RGB)  {
      res.format = RGB;
    } else  if (glFormat == PGL.RGBA) {
      res.format = ARGB;
    } else  if (glFormat == PGL.ALPHA) {
      res.format = ALPHA;
    }

    if (glMagFilter == PGL.NEAREST && glMinFilter == PGL.NEAREST) {
      res.sampling = POINT;
      res.mipmaps = false;
    } else if (glMagFilter == PGL.NEAREST && glMinFilter == PGL.LINEAR)  {
      res.sampling = LINEAR;
      res.mipmaps = false;
    } else if (glMagFilter == PGL.NEAREST &&
               glMinFilter == PGL.LINEAR_MIPMAP_NEAREST)  {
      res.sampling = LINEAR;
      res.mipmaps = true;
    } else if (glMagFilter == PGL.LINEAR && glMinFilter == PGL.LINEAR)  {
      res.sampling = BILINEAR;
      res.mipmaps = false;
    } else if (glMagFilter == PGL.LINEAR &&
               glMinFilter == PGL.LINEAR_MIPMAP_NEAREST)  {
      res.sampling = BILINEAR;
      res.mipmaps = true;
    } else if (glMagFilter == PGL.LINEAR &&
               glMinFilter == PGL.LINEAR_MIPMAP_LINEAR) {
      res.sampling = TRILINEAR;
      res.mipmaps = true;
    }

    if (glWrapS == PGL.CLAMP_TO_EDGE) {
      res.wrapU = CLAMP;
    } else if (glWrapS == PGL.REPEAT) {
      res.wrapU = REPEAT;
    }

    if (glWrapT == PGL.CLAMP_TO_EDGE) {
      res.wrapV = CLAMP;
    } else if (glWrapT == PGL.REPEAT) {
      res.wrapV = REPEAT;
    }

    return res;
  }


  /**
   * Sets texture target and internal format according to the target and
   * type specified.
   * @param target int
   * @param params GLTextureParameters
   */
  protected void setParameters(Parameters params) {
    if (params.target == TEX2D)  {
        glTarget = PGL.TEXTURE_2D;
    } else {
      throw new RuntimeException("Unknown texture target");
    }

    if (params.format == RGB)  {
      glFormat = PGL.RGB;
    } else  if (params.format == ARGB) {
      glFormat = PGL.RGBA;
    } else  if (params.format == ALPHA) {
      glFormat = PGL.ALPHA;
    } else {
      throw new RuntimeException("Unknown texture format");
    }

    boolean mipmaps = params.mipmaps && PGL.MIPMAPS_ENABLED;
    if (mipmaps && !PGraphicsOpenGL.autoMipmapGenSupported) {
      PGraphics.showWarning("Mipmaps were requested but automatic mipmap " +
                            "generation is not supported and manual " +
                            "generation still not implemented, so mipmaps " +
                            "will be disabled.");
      mipmaps = false;
    }

    if (params.sampling == POINT) {
      glMagFilter = PGL.NEAREST;
      glMinFilter = PGL.NEAREST;
    } else if (params.sampling == LINEAR)  {
      glMagFilter = PGL.NEAREST;
      glMinFilter = mipmaps ? PGL.LINEAR_MIPMAP_NEAREST : PGL.LINEAR;
    } else if (params.sampling == BILINEAR)  {
      glMagFilter = PGL.LINEAR;
      glMinFilter = mipmaps ? PGL.LINEAR_MIPMAP_NEAREST : PGL.LINEAR;
    } else if (params.sampling == TRILINEAR)  {
      glMagFilter = PGL.LINEAR;
      glMinFilter = mipmaps ? PGL.LINEAR_MIPMAP_LINEAR : PGL.LINEAR;
    } else {
      throw new RuntimeException("Unknown texture filtering mode");
    }

    if (params.wrapU == CLAMP) {
      glWrapS = PGL.CLAMP_TO_EDGE;
    } else if (params.wrapU == REPEAT)  {
      glWrapS = PGL.REPEAT;
    } else {
      throw new RuntimeException("Unknown wrapping mode");
    }

    if (params.wrapV == CLAMP) {
      glWrapT = PGL.CLAMP_TO_EDGE;
    } else if (params.wrapV == REPEAT)  {
      glWrapT = PGL.REPEAT;
    } else {
      throw new RuntimeException("Unknown wrapping mode");
    }

    usingMipmaps = glMinFilter == PGL.LINEAR_MIPMAP_NEAREST ||
                   glMinFilter == PGL.LINEAR_MIPMAP_LINEAR;

    usingRepeat = glWrapS == PGL.REPEAT || glWrapT == PGL.REPEAT;

    invertedX = false;
    invertedY = false;
  }


  protected void fillEdges(int x, int y, int w, int h) {
    if ((width < glWidth || height < glHeight) && (x + w == width || y + h == height)) {
      if (x + w == width) {
        int ew = glWidth - width;
        edgePixels = new int[h * ew];
        for (int i = 0; i < h; i++) {
          int c = rgbaPixels[i * w + (w - 1)];
          Arrays.fill(edgePixels, i * ew, (i + 1) * ew, c);
        }
        edgeBuffer = PGL.updateIntBuffer(edgeBuffer, edgePixels, true);
        pgl.texSubImage2D(glTarget, 0, width, y, ew, h, PGL.RGBA,
                          PGL.UNSIGNED_BYTE, edgeBuffer);
      }

      if (y + h == height) {
        int eh = glHeight - height;
        edgePixels = new int[eh * w];
        for (int i = 0; i < eh; i++) {
          System.arraycopy(rgbaPixels, (h - 1) * w, edgePixels, i * w, w);
        }
        edgeBuffer = PGL.updateIntBuffer(edgeBuffer, edgePixels, true);
        pgl.texSubImage2D(glTarget, 0, x, height, w, eh, PGL.RGBA,
                          PGL.UNSIGNED_BYTE, edgeBuffer);
      }

      if (x + w == width && y + h == height) {
        int ew = glWidth - width;
        int eh = glHeight - height;
        int c = rgbaPixels[w * h - 1];
        edgePixels = new int[eh * ew];
        Arrays.fill(edgePixels, 0, eh * ew, c);
        edgeBuffer = PGL.updateIntBuffer(edgeBuffer, edgePixels, true);
        pgl.texSubImage2D(glTarget, 0, width, height, ew, eh, PGL.RGBA,
                          PGL.UNSIGNED_BYTE, edgeBuffer);
      }
    }
  }

  ///////////////////////////////////////////////////////////////////////////

  // Parameters object


  /**
   * This class stores the parameters for a texture: target, internal format,
   * minimization filter and magnification filter.
   */
  static public class Parameters {
    /**
     * Texture target.
     */
    public int target;

    /**
     * Texture internal format.
     */
    public int format;

    /**
     * Texture filtering (POINT, LINEAR, BILINEAR or TRILINEAR).
     */
    public int sampling;

    /**
     * Use mipmaps or not.
     */
    public boolean mipmaps;

    /**
     * Wrapping mode along U.
     */
    public int wrapU;

    /**
     * Wrapping mode along V.
     */
    public int wrapV;

    /**
     * Sets all the parameters to default values.
     */
    public Parameters() {
      this.target = TEX2D;
      this.format = ARGB;
      this.sampling = BILINEAR;
      this.mipmaps = true;
      this.wrapU = CLAMP;
      this.wrapV = CLAMP;
    }

    public Parameters(int format) {
      this.target = TEX2D;
      this.format = format;
      this.sampling = BILINEAR;
      this.mipmaps = true;
      this.wrapU = CLAMP;
      this.wrapV = CLAMP;
    }

    public Parameters(int format, int sampling) {
      this.target = TEX2D;
      this.format = format;
      this.sampling = sampling;
      this.mipmaps = true;
      this.wrapU = CLAMP;
      this.wrapV = CLAMP;
    }

    public Parameters(int format, int sampling, boolean mipmaps) {
      this.target = TEX2D;
      this.format = format;
      this.mipmaps = mipmaps;
      if (sampling == TRILINEAR && !mipmaps) {
        this.sampling = BILINEAR;
      } else {
        this.sampling = sampling;
      }
      this.wrapU = CLAMP;
      this.wrapV = CLAMP;
    }

    public Parameters(int format, int sampling, boolean mipmaps, int wrap) {
      this.target = TEX2D;
      this.format = format;
      this.mipmaps = mipmaps;
      if (sampling == TRILINEAR && !mipmaps) {
        this.sampling = BILINEAR;
      } else {
        this.sampling = sampling;
      }
      this.wrapU = wrap;
      this.wrapV = wrap;
    }

    public Parameters(Parameters src) {
      set(src);
    }

    public void set(int format) {
      this.format = format;
    }

    public void set(int format, int sampling) {
      this.format = format;
      this.sampling = sampling;
    }

    public void set(int format, int sampling, boolean mipmaps) {
      this.format = format;
      this.sampling = sampling;
      this.mipmaps = mipmaps;
    }

    public void set(Parameters src) {
      this.target = src.target;
      this.format = src.format;
      this.sampling = src.sampling;
      this.mipmaps = src.mipmaps;
      this.wrapU = src.wrapU;
      this.wrapV = src.wrapV;
    }
  }

  /**
   * This class stores a buffer copied from the buffer source.
   *
   */
  protected class BufferData {
    int w, h;
    // Native buffer object.
    Object natBuf;
    // Buffer viewed as int.
    IntBuffer rgbBuf;

    BufferData(Object nat, IntBuffer rgb, int w, int h) {
      natBuf = nat;
      rgbBuf = rgb;
      this.w = w;
      this.h = h;
    }

    void dispose() {
      try {
        // Disposing the native buffer.
        disposeBufferMethod.invoke(bufferSource, natBuf);
        natBuf = null;
        rgbBuf = null;
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
