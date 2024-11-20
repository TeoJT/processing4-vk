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

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.PathIterator;
import java.io.IOException;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import com.jogamp.common.util.VersionNumber;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.GL3ES3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLCapabilitiesImmutable;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawable;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.GLRendererQuirks;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUtessellator;
import com.jogamp.opengl.glu.GLUtessellatorCallbackAdapter;

import processing.GL2VK.Util;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PMatrix3D;
import processing.core.PSurface;
import processing.vulkan.PVK;


public class PJOGL extends PGL {
  // OpenGL profile to use (2, 3 or 4)
  public static int profile = 2;

  public static int pjoglCounter = 0;

  // User-provided icons to override defaults
  protected static String[] icons = null;

  // The two windowing toolkits available to use in JOGL:
  public static final int AWT  = 0; // http://jogamp.org/wiki/index.php/Using_JOGL_in_AWT_SWT_and_Swing
  public static final int NEWT = 1; // http://jogamp.org/jogl/doc/NEWT-Overview.html

  // ........................................................

  // Public members to access the underlying GL objects and context

  /** Basic GL functionality, common to all profiles */
  public GL gl;

  /** GLU interface **/
  public GLU glu;

  /** The rendering context (holds rendering state info) */
  public GLContext context;

  // ........................................................

  // Protected JOGL-specific objects needed to access the GL profiles

  /** The capabilities of the OpenGL rendering surface */
  protected GLCapabilitiesImmutable capabilities;

  /** The rendering surface */
  protected GLDrawable drawable;

  /** GLES2 functionality (shaders, etc) */
  protected GL2ES2 gl2;

  /** GL3 interface */
  protected GL2GL3 gl3;

  /**
   * GL2 desktop functionality (blit framebuffer, map buffer range,
   * multi-sampled render buffers)
   */
  protected GL2 gl2x;

  /** GL3ES3 interface */
  protected GL3ES3 gl3es3;

  // ........................................................

  // Utility arrays to copy projection/modelview matrices to GL

  protected float[] projMatrix;
  protected float[] mvMatrix;

  // ........................................................

  // Static initialization for some parameters that need to be different for
  // JOGL

  static {
    MIN_DIRECT_BUFFER_SIZE = 2;
    INDEX_TYPE             = GL.GL_UNSIGNED_SHORT;
  }


  private static boolean forceSharedObjectSync = true;

  ///////////////////////////////////////////////////////////////

  // Initialization, finalization


  long before = System.nanoTime();
  long after = System.nanoTime();
  long total = 0;

  public PJOGL(PGraphicsOpenGL pg) {
    super(pg);
    report("PJOGL");
    glu = new GLU();
    pjoglCounter++;
    if (pjoglCounter > 1) {
      System.err.println("WARNING  MORE THAN ONE PJOGL CREATED");
    }
  }

  private static HashMap<String, Integer> funReports = new HashMap<>();


  private static void report(String name) {
//    System.out.println(name);
//    if (funReports.containsKey(name)) {
//      // Increment count by one.
//      funReports.replace(name, funReports.get(name)+1);
//    }
//    // Never been called before.
//    else {
//      funReports.put(name, 1);
//    }


  }



  public static String getReport() {
    String mssg = "--- PJOGL USAGE REPORT ---\n";
    for (Map.Entry<String, Integer> entry : funReports.entrySet()) {
      String funName = entry.getKey();
      mssg += funName;

      mssg += entry.getValue().toString()+"\n";
    }
    mssg += "--- end report ---";
    return mssg;
  }


  @Override
  public Object getNative() {
        report("getNative");
    return sketch.getSurface().getNative();
  }


  @Override
  protected void setFrameRate(float fps) {}


  @Override
  protected void initSurface(int antialias) {}


  @Override
  protected void reinitSurface() {}


  @Override
  protected void registerListeners() {}


  static public void setIcon(String... icons) {
        report("setIcon");
    PJOGL.icons = new String[icons.length];
    PApplet.arrayCopy(icons, PJOGL.icons);
  }


  ///////////////////////////////////////////////////////////////

  // Public methods to get/set renderer's properties


  public void setCaps(GLCapabilities caps) {
        report("setCaps");
    reqNumSamples = caps.getNumSamples();
    capabilities = caps;
  }


  public GLCapabilitiesImmutable getCaps() {
        report("getCaps");
    return capabilities;
  }


  public boolean needSharedObjectSync() {
        report("needSharedObjectSync");
    before = System.nanoTime();
    boolean result = forceSharedObjectSync || gl.getContext().hasRendererQuirk(GLRendererQuirks.NeedSharedObjectSync);
    after = System.nanoTime();
    total += after-before;
    return result;
  }


  public void setFps(float fps) {
        report("setFps");
    before = System.nanoTime();
    if (!setFps || targetFps != fps) {
      if (60 < fps) {
        // Disables v-sync
        gl.setSwapInterval(0);
      } else if (30 < fps) {
        gl.setSwapInterval(1);
      } else {
        gl.setSwapInterval(2);
      }
      targetFps = currentFps = fps;
      setFps = true;
    }

    after = System.nanoTime();
    total += after-before;
  }


  @Override
  protected int getDepthBits() {
    report("getDepthBits");
    before = System.nanoTime();
    int bits = capabilities.getDepthBits();
    after = System.nanoTime();
    total += after-before;
    return bits;
  }


  @Override
  protected int getStencilBits() {
    before = System.nanoTime();
    report("getStencilBits");
    after = System.nanoTime();
    int bits = capabilities.getStencilBits();
    total += after-before;
    return bits;
  }


  @Override
  protected float getPixelScale() {
        report("getPixelScale");
    PSurface surf = sketch.getSurface();
    if (surf == null) {
      return graphics.pixelDensity;
    } else if (surf instanceof PSurfaceJOGL) {
      return ((PSurfaceJOGL)surf).getPixelScale();
    } else {
      throw new RuntimeException("Renderer cannot find a JOGL surface");
    }
  }


  @Override
  protected void getGL(PGL pgl) {
        report("getGL");
        before = System.nanoTime();
    PJOGL pjogl = (PJOGL)pgl;

    this.drawable = pjogl.drawable;
    this.context = pjogl.context;
    this.glContext = pjogl.glContext;
    setThread(pjogl.glThread);

    this.gl = pjogl.gl;
    this.gl2 = pjogl.gl2;
    this.gl2x = pjogl.gl2x;
    this.gl3 = pjogl.gl3;
    this.gl3es3 = pjogl.gl3es3;
    after = System.nanoTime();
    total += after-before;

  }


  public void getGL(GLAutoDrawable glDrawable) {
        report("getGL");


        before = System.nanoTime();


    context = glDrawable.getContext();
    glContext = context.hashCode();
    setThread(Thread.currentThread());

    gl = context.getGL();
    gl2 = gl.getGL2ES2();
    try {
      gl2x = gl.getGL2();
    } catch (com.jogamp.opengl.GLException e) {
      gl2x = null;
    }
    try {
      gl3 = gl.getGL2GL3();
    } catch (com.jogamp.opengl.GLException e) {
      gl3 = null;
    }
    try {
      gl3es3 = gl.getGL3ES3();
    } catch (com.jogamp.opengl.GLException e) {
      gl3es3 = null;
    }

    after = System.nanoTime();
    total += after-before;
  }


  @Override
  protected boolean canDraw() { return true; }


  @Override
  protected  void requestFocus() {}

  @Override
  protected  void requestDraw() {}


  @Override
  protected void swapBuffers()  {
        report("swapBuffers");

    PSurfaceJOGL surf = (PSurfaceJOGL)sketch.getSurface();
    surf.window.swapBuffers();


  }


  @Override
  protected void initFBOLayer() {

        report("initFBOLayer");
    if (0 < sketch.frameCount) {
      if (isES()) initFBOLayerES();
      else initFBOLayerGL();
    }

  }


  private void initFBOLayerES() {
        report("initFBOLayerES");
    IntBuffer buf = allocateDirectIntBuffer(fboWidth * fboHeight);

    if (hasReadBuffer()) readBuffer(BACK);
    readPixelsImpl(0, 0, fboWidth, fboHeight, RGBA, UNSIGNED_BYTE, buf);
    bindTexture(TEXTURE_2D, glColorTex.get(frontTex));
    texSubImage2D(TEXTURE_2D, 0, 0, 0, fboWidth, fboHeight, RGBA, UNSIGNED_BYTE, buf);

    bindTexture(TEXTURE_2D, glColorTex.get(backTex));
    texSubImage2D(TEXTURE_2D, 0, 0, 0, fboWidth, fboHeight, RGBA, UNSIGNED_BYTE, buf);

    bindTexture(TEXTURE_2D, 0);
    bindFramebufferImpl(FRAMEBUFFER, 0);
  }


  private void initFBOLayerGL() {
        report("initFBOLayerGL");
    // Copy the contents of the front and back screen buffers to the textures
    // of the FBO, so they are properly initialized. Note that the front buffer
    // of the default framebuffer (the screen) contains the previous frame:
    // https://www.opengl.org/wiki/Default_Framebuffer
    // so it is copied to the front texture of the FBO layer:
    if (pclearColor || 0 < pgeomCount || !sketch.isLooping()) {
      if (hasReadBuffer()) readBuffer(FRONT);
    } else {
      // ...except when the previous frame has not been cleared and nothing was
      // rendered while looping. In this case the back buffer, which holds the
      // initial state of the previous frame, still contains the most up-to-date
      // screen state.
      readBuffer(BACK);
    }
    bindFramebufferImpl(DRAW_FRAMEBUFFER, glColorFbo.get(0));
    framebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0,
                         TEXTURE_2D, glColorTex.get(frontTex), 0);
    if (hasDrawBuffer()) drawBuffer(COLOR_ATTACHMENT0);
    blitFramebuffer(0, 0, fboWidth, fboHeight,
                    0, 0, fboWidth, fboHeight,
                    COLOR_BUFFER_BIT, NEAREST);

    readBuffer(BACK);
    bindFramebufferImpl(DRAW_FRAMEBUFFER, glColorFbo.get(0));
    framebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0,
                         TEXTURE_2D, glColorTex.get(backTex), 0);
    drawBuffer(COLOR_ATTACHMENT0);
    blitFramebuffer(0, 0, fboWidth, fboHeight,
                    0, 0, fboWidth, fboHeight,
                    COLOR_BUFFER_BIT, NEAREST);

    bindFramebufferImpl(FRAMEBUFFER, 0);
  }


  @Override
  protected void beginGL() {
        report("beginGL");
    PMatrix3D proj = graphics.projection;
    PMatrix3D mdl = graphics.modelview;
    if (gl2x != null) {
      if (projMatrix == null) {
        projMatrix = new float[16];
      }
      gl2x.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
      projMatrix[ 0] = proj.m00;
      projMatrix[ 1] = proj.m10;
      projMatrix[ 2] = proj.m20;
      projMatrix[ 3] = proj.m30;
      projMatrix[ 4] = proj.m01;
      projMatrix[ 5] = proj.m11;
      projMatrix[ 6] = proj.m21;
      projMatrix[ 7] = proj.m31;
      projMatrix[ 8] = proj.m02;
      projMatrix[ 9] = proj.m12;
      projMatrix[10] = proj.m22;
      projMatrix[11] = proj.m32;
      projMatrix[12] = proj.m03;
      projMatrix[13] = proj.m13;
      projMatrix[14] = proj.m23;
      projMatrix[15] = proj.m33;
      gl2x.glLoadMatrixf(projMatrix, 0);

      if (mvMatrix == null) {
        mvMatrix = new float[16];
      }
      gl2x.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
      mvMatrix[ 0] = mdl.m00;
      mvMatrix[ 1] = mdl.m10;
      mvMatrix[ 2] = mdl.m20;
      mvMatrix[ 3] = mdl.m30;
      mvMatrix[ 4] = mdl.m01;
      mvMatrix[ 5] = mdl.m11;
      mvMatrix[ 6] = mdl.m21;
      mvMatrix[ 7] = mdl.m31;
      mvMatrix[ 8] = mdl.m02;
      mvMatrix[ 9] = mdl.m12;
      mvMatrix[10] = mdl.m22;
      mvMatrix[11] = mdl.m32;
      mvMatrix[12] = mdl.m03;
      mvMatrix[13] = mdl.m13;
      mvMatrix[14] = mdl.m23;
      mvMatrix[15] = mdl.m33;
      gl2x.glLoadMatrixf(mvMatrix, 0);
    }
  }


  @Override
  protected boolean hasFBOs() {
        report("hasFBOs");
    if (context.hasBasicFBOSupport()) {
      return true;
    }
    else {
      return super.hasFBOs();
    }
  }


  @Override
  protected boolean hasShaders() {
        report("hasShaders");
    if (context.hasGLSL()) {
      return true;
    }
    else {
      return super.hasShaders();
    }
  }


  public void init(GLAutoDrawable glDrawable) {
        report("init");
    capabilities = glDrawable.getChosenGLCapabilities();
    if (!hasFBOs()) {
      throw new RuntimeException(MISSING_FBO_ERROR);
    }
    if (!hasShaders()) {
      throw new RuntimeException(MISSING_GLSL_ERROR);
    }
  }


  ///////////////////////////////////////////////////////////

  // Utility functions


  @Override
  protected void enableTexturing(int target) {
        report("enableTexturing");
    if (target == TEXTURE_2D) {
      texturingTargets[0] = true;
    } else if (target == TEXTURE_RECTANGLE) {
      texturingTargets[1] = true;
    }
  }


  @Override
  protected void disableTexturing(int target) {
        report("disableTexturing");
    if (target == TEXTURE_2D) {
      texturingTargets[0] = false;
    } else if (target == TEXTURE_RECTANGLE) {
      texturingTargets[1] = false;
    }
  }


  /**
   * Convenience method to get a legit FontMetrics object. Where possible,
   * override this any renderer subclass so that you're not using what's
   * returned by getDefaultToolkit() to get your metrics.
   */
  @SuppressWarnings("deprecation")
  private FontMetrics getFontMetrics(Font font) {  // ignore
        report("getFontMetrics");
    return Toolkit.getDefaultToolkit().getFontMetrics(font);
  }


  /**
   * Convenience method to jump through some Java2D hoops and get an FRC.
   */
  private FontRenderContext getFontRenderContext(Font font) {  // ignore
        report("getFontRenderContext");
    return getFontMetrics(font).getFontRenderContext();
  }


  /*
  @Override
  protected int getFontAscent(Object font) {
        report("getFontAscent");
    return getFontMetrics((Font) font).getAscent();
  }


  @Override
  protected int getFontDescent(Object font) {
        report("getFontDescent");
    return getFontMetrics((Font) font).getDescent();
  }
  */


  @Override
  protected int getTextWidth(Object font, char[] buffer, int start, int stop) {
        report("getTextWidth");
    // maybe should use one of the newer/fancier functions for this?
    int length = stop - start;
    FontMetrics metrics = getFontMetrics((Font) font);
    return metrics.charsWidth(buffer, start, length);
  }


  @Override
  protected Object getDerivedFont(Object font, float size) {
        report("getDerivedFont");
    return ((Font) font).deriveFont(size);
  }


  @Override
  protected int getGLSLVersion() {
        report("getGLSLVersion");
    VersionNumber vn = context.getGLSLVersionNumber();
    return vn.getMajor() * 100 + vn.getMinor();
  }


  @Override
  protected String getGLSLVersionSuffix() {
        report("getGLSLVersionSuffix");
    VersionNumber vn = context.getGLSLVersionNumber();
    if (context.isGLESProfile() && 1 < vn.getMajor()) {
      return " es";
    } else {
      return "";
    }
  }


  @Override
  protected String[] loadVertexShader(String filename) {
    return loadVertexShader(filename, getGLSLVersion(), getGLSLVersionSuffix());
  }


  @Override
  protected String[] loadFragmentShader(String filename) {
    return loadFragmentShader(filename, getGLSLVersion(), getGLSLVersionSuffix());
  }


  @Override
  protected String[] loadVertexShader(URL url) {
    return loadVertexShader(url, getGLSLVersion(), getGLSLVersionSuffix());
  }


  @Override
  protected String[] loadFragmentShader(URL url) {
    return loadFragmentShader(url, getGLSLVersion(), getGLSLVersionSuffix());
  }


  @Override
  protected String[] loadFragmentShader(String filename, int version, String versionSuffix) {
    String[] fragSrc0 = sketch.loadStrings(filename);
    return preprocessFragmentSource(fragSrc0, version, versionSuffix);
  }


  @Override
  protected String[] loadVertexShader(String filename, int version, String versionSuffix) {
    String[] vertSrc0 = sketch.loadStrings(filename);
    return preprocessVertexSource(vertSrc0, version, versionSuffix);
  }


  @Override
  protected String[] loadFragmentShader(URL url, int version, String versionSuffix) {
    try {
      String[] fragSrc0 = PApplet.loadStrings(url.openStream());
      return preprocessFragmentSource(fragSrc0, version, versionSuffix);
    } catch (IOException e) {
      PGraphics.showException("Cannot load fragment shader " + url.getFile());
    }
    return null;
  }


  @Override
  protected String[] loadVertexShader(URL url, int version, String versionSuffix) {
    try {
      String[] vertSrc0 = PApplet.loadStrings(url.openStream());
      return preprocessVertexSource(vertSrc0, version, versionSuffix);
    } catch (IOException e) {
      PGraphics.showException("Cannot load vertex shader " + url.getFile());
    }
    return null;
  }


  ///////////////////////////////////////////////////////////

  // Tessellator


  @Override
  protected Tessellator createTessellator(TessellatorCallback callback) {
        report("createTessellator");
    return new Tessellator(callback);
  }


  protected static class Tessellator implements PGL.Tessellator {
    protected GLUtessellator tess;
    protected TessellatorCallback callback;
    protected GLUCallback gluCallback;

    public Tessellator(TessellatorCallback callback) {
        report("Tessellator");
      this.callback = callback;
      tess = GLU.gluNewTess();
      gluCallback = new GLUCallback();

      GLU.gluTessCallback(tess, GLU.GLU_TESS_BEGIN, gluCallback);
      GLU.gluTessCallback(tess, GLU.GLU_TESS_END, gluCallback);
      GLU.gluTessCallback(tess, GLU.GLU_TESS_VERTEX, gluCallback);
      GLU.gluTessCallback(tess, GLU.GLU_TESS_COMBINE, gluCallback);
      GLU.gluTessCallback(tess, GLU.GLU_TESS_ERROR, gluCallback);
    }

    @Override
    public void setCallback(int flag) {
        report("setCallback");
      GLU.gluTessCallback(tess, flag, gluCallback);
    }

    @Override
    public void setWindingRule(int rule) {
        report("setWindingRule");
      setProperty(GLU.GLU_TESS_WINDING_RULE, rule);
    }

    public void setProperty(int property, int value) {
        report("setProperty");
      GLU.gluTessProperty(tess, property, value);
    }

    @Override
    public void beginPolygon() {
        report("beginPolygon");
      beginPolygon(null);
    }

    @Override
    public void beginPolygon(Object data) {
        report("beginPolygon");
      GLU.gluTessBeginPolygon(tess, data);
    }

    @Override
    public void endPolygon() {
        report("endPolygon");
      GLU.gluTessEndPolygon(tess);
    }

    @Override
    public void beginContour() {
        report("beginContour");
      GLU.gluTessBeginContour(tess);
    }

    @Override
    public void endContour() {
        report("endContour");
      GLU.gluTessEndContour(tess);
    }

    @Override
    public void addVertex(double[] v) {
        report("addVertex");
      addVertex(v, 0, v);
    }

    @Override
    public void addVertex(double[] v, int n, Object data) {
        report("addVertex");
      GLU.gluTessVertex(tess, v, n, data);
    }

    protected class GLUCallback extends GLUtessellatorCallbackAdapter {
      @Override
      public void begin(int type) {
        report("begin");
        callback.begin(type);
      }

      @Override
      public void end() {
        report("end");
        callback.end();
      }

      @Override
      public void vertex(Object data) {
        report("vertex");
        callback.vertex(data);
      }

      @Override
      public void combine(double[] coords, Object[] data,
                          float[] weight, Object[] outData) {
        callback.combine(coords, data, weight, outData);
      }

      @Override
      public void error(int errnum) {
        report("error");
        callback.error(errnum);
      }
    }
  }


  @Override
  protected String tessError(int err) {
        report("tessError");
    return glu.gluErrorString(err);
  }


  ///////////////////////////////////////////////////////////

  // Font outline


  static {
    SHAPE_TEXT_SUPPORTED = true;
    SEG_MOVETO  = PathIterator.SEG_MOVETO;
    SEG_LINETO  = PathIterator.SEG_LINETO;
    SEG_QUADTO  = PathIterator.SEG_QUADTO;
    SEG_CUBICTO = PathIterator.SEG_CUBICTO;
    SEG_CLOSE   = PathIterator.SEG_CLOSE;
  }


  @Override
  protected FontOutline createFontOutline(char ch, Object font) {
        report("createFontOutline");
    return new FontOutline(ch, (Font) font);
  }


  protected class FontOutline implements PGL.FontOutline {
    PathIterator iter;

    public FontOutline(char ch, Font font) {
        report("FontOutline");
      char[] textArray = new char[] { ch };
      FontRenderContext frc = getFontRenderContext(font);
      GlyphVector gv = font.createGlyphVector(frc, textArray);
      Shape shp = gv.getOutline();
      iter = shp.getPathIterator(null);
    }

    public boolean isDone() {
        report("isDone");
      return iter.isDone();
    }

    public int currentSegment(float[] coords) {
        report("currentSegment");
      return iter.currentSegment(coords);
    }

    public void next() {
        report("next");
      iter.next();
    }
  }


  ///////////////////////////////////////////////////////////

  // Constants

  protected static HashMap<Integer, String> valuesToEnums = new HashMap<>();

  public void showme(String method, int value) {
    System.out.println(method+" "+valuesToEnums.get(value) + " " + value);
  }

  static {
        FALSE = GL.GL_FALSE;    valuesToEnums.put(FALSE, "FALSE");
        TRUE  = GL.GL_TRUE;    valuesToEnums.put(TRUE, "TRUE");

        INT            = GL2ES2.GL_INT;    valuesToEnums.put(INT, "INT");
        BYTE           = GL.GL_BYTE;    valuesToEnums.put(BYTE, "BYTE");
        SHORT          = GL.GL_SHORT;    valuesToEnums.put(SHORT, "SHORT");
        FLOAT          = GL.GL_FLOAT;    valuesToEnums.put(FLOAT, "FLOAT");
        BOOL           = GL2ES2.GL_BOOL;    valuesToEnums.put(BOOL, "BOOL");
        UNSIGNED_INT   = GL.GL_UNSIGNED_INT;    valuesToEnums.put(UNSIGNED_INT, "UNSIGNED_INT");
        UNSIGNED_BYTE  = GL.GL_UNSIGNED_BYTE;    valuesToEnums.put(UNSIGNED_BYTE, "UNSIGNED_BYTE");
        UNSIGNED_SHORT = GL.GL_UNSIGNED_SHORT;    valuesToEnums.put(UNSIGNED_SHORT, "UNSIGNED_SHORT");

        RGB             = GL.GL_RGB;    valuesToEnums.put(RGB, "RGB");
        RGBA            = GL.GL_RGBA;    valuesToEnums.put(RGBA, "RGBA");
        ALPHA           = GL.GL_ALPHA;    valuesToEnums.put(ALPHA, "ALPHA");
        LUMINANCE       = GL.GL_LUMINANCE;    valuesToEnums.put(LUMINANCE, "LUMINANCE");
        LUMINANCE_ALPHA = GL.GL_LUMINANCE_ALPHA;    valuesToEnums.put(LUMINANCE_ALPHA, "LUMINANCE_ALPHA");

        UNSIGNED_SHORT_5_6_5   = GL.GL_UNSIGNED_SHORT_5_6_5;    valuesToEnums.put(UNSIGNED_SHORT_5_6_5, "UNSIGNED_SHORT_5_6_5");
        UNSIGNED_SHORT_4_4_4_4 = GL.GL_UNSIGNED_SHORT_4_4_4_4;    valuesToEnums.put(UNSIGNED_SHORT_4_4_4_4, "UNSIGNED_SHORT_4_4_4_4");
        UNSIGNED_SHORT_5_5_5_1 = GL.GL_UNSIGNED_SHORT_5_5_5_1;    valuesToEnums.put(UNSIGNED_SHORT_5_5_5_1, "UNSIGNED_SHORT_5_5_5_1");

        RGBA4   = GL.GL_RGBA4;    valuesToEnums.put(RGBA4, "RGBA4");
        RGB5_A1 = GL.GL_RGB5_A1;    valuesToEnums.put(RGB5_A1, "RGB5_A1");
        RGB565  = GL.GL_RGB565;    valuesToEnums.put(RGB565, "RGB565");
        RGB8    = GL.GL_RGB8;    valuesToEnums.put(RGB8, "RGB8");
        RGBA8   = GL.GL_RGBA8;    valuesToEnums.put(RGBA8, "RGBA8");
        ALPHA8  = GL.GL_ALPHA8;    valuesToEnums.put(ALPHA8, "ALPHA8");

        READ_ONLY  = GL2ES3.GL_READ_ONLY;    valuesToEnums.put(READ_ONLY, "READ_ONLY");
        WRITE_ONLY = GL.GL_WRITE_ONLY;    valuesToEnums.put(WRITE_ONLY, "WRITE_ONLY");
        READ_WRITE = GL2ES3.GL_READ_WRITE;    valuesToEnums.put(READ_WRITE, "READ_WRITE");

        TESS_WINDING_NONZERO = GLU.GLU_TESS_WINDING_NONZERO;    valuesToEnums.put(TESS_WINDING_NONZERO, "TESS_WINDING_NONZERO");
        TESS_WINDING_ODD     = GLU.GLU_TESS_WINDING_ODD;    valuesToEnums.put(TESS_WINDING_ODD, "TESS_WINDING_ODD");
        TESS_EDGE_FLAG       = GLU.GLU_TESS_EDGE_FLAG;    valuesToEnums.put(TESS_EDGE_FLAG, "TESS_EDGE_FLAG");

        GENERATE_MIPMAP_HINT = GL.GL_GENERATE_MIPMAP_HINT;    valuesToEnums.put(GENERATE_MIPMAP_HINT, "GENERATE_MIPMAP_HINT");
        FASTEST              = GL.GL_FASTEST;    valuesToEnums.put(FASTEST, "FASTEST");
        NICEST               = GL.GL_NICEST;    valuesToEnums.put(NICEST, "NICEST");
        DONT_CARE            = GL.GL_DONT_CARE;    valuesToEnums.put(DONT_CARE, "DONT_CARE");

        VENDOR                   = GL.GL_VENDOR;    valuesToEnums.put(VENDOR, "VENDOR");
        RENDERER                 = GL.GL_RENDERER;    valuesToEnums.put(RENDERER, "RENDERER");
        VERSION                  = GL.GL_VERSION;    valuesToEnums.put(VERSION, "VERSION");
        EXTENSIONS               = GL.GL_EXTENSIONS;    valuesToEnums.put(EXTENSIONS, "EXTENSIONS");
        SHADING_LANGUAGE_VERSION = GL2ES2.GL_SHADING_LANGUAGE_VERSION;    valuesToEnums.put(SHADING_LANGUAGE_VERSION, "SHADING_LANGUAGE_VERSION");

        MAX_SAMPLES = GL.GL_MAX_SAMPLES;    valuesToEnums.put(MAX_SAMPLES, "MAX_SAMPLES");
        SAMPLES     = GL.GL_SAMPLES;    valuesToEnums.put(SAMPLES, "SAMPLES");

        ALIASED_LINE_WIDTH_RANGE = GL.GL_ALIASED_LINE_WIDTH_RANGE;    valuesToEnums.put(ALIASED_LINE_WIDTH_RANGE, "ALIASED_LINE_WIDTH_RANGE");
        ALIASED_POINT_SIZE_RANGE = GL.GL_ALIASED_POINT_SIZE_RANGE;    valuesToEnums.put(ALIASED_POINT_SIZE_RANGE, "ALIASED_POINT_SIZE_RANGE");

        DEPTH_BITS   = GL.GL_DEPTH_BITS;    valuesToEnums.put(DEPTH_BITS, "DEPTH_BITS");
        STENCIL_BITS = GL.GL_STENCIL_BITS;    valuesToEnums.put(STENCIL_BITS, "STENCIL_BITS");

        CCW = GL.GL_CCW;    valuesToEnums.put(CCW, "CCW");
        CW  = GL.GL_CW;    valuesToEnums.put(CW, "CW");

        VIEWPORT = GL.GL_VIEWPORT;    valuesToEnums.put(VIEWPORT, "VIEWPORT");

        ARRAY_BUFFER         = GL.GL_ARRAY_BUFFER;    valuesToEnums.put(ARRAY_BUFFER, "ARRAY_BUFFER");
        ELEMENT_ARRAY_BUFFER = GL.GL_ELEMENT_ARRAY_BUFFER;    valuesToEnums.put(ELEMENT_ARRAY_BUFFER, "ELEMENT_ARRAY_BUFFER");
        PIXEL_PACK_BUFFER    = GL2ES3.GL_PIXEL_PACK_BUFFER;    valuesToEnums.put(PIXEL_PACK_BUFFER, "PIXEL_PACK_BUFFER");

        MAX_VERTEX_ATTRIBS  = GL2ES2.GL_MAX_VERTEX_ATTRIBS;    valuesToEnums.put(MAX_VERTEX_ATTRIBS, "MAX_VERTEX_ATTRIBS");

        STATIC_DRAW  = GL.GL_STATIC_DRAW;    valuesToEnums.put(STATIC_DRAW, "STATIC_DRAW");
        DYNAMIC_DRAW = GL.GL_DYNAMIC_DRAW;    valuesToEnums.put(DYNAMIC_DRAW, "DYNAMIC_DRAW");
        STREAM_DRAW  = GL2ES2.GL_STREAM_DRAW;    valuesToEnums.put(STREAM_DRAW, "STREAM_DRAW");
        STREAM_READ  = GL2ES3.GL_STREAM_READ;    valuesToEnums.put(STREAM_READ, "STREAM_READ");

        BUFFER_SIZE  = GL.GL_BUFFER_SIZE;    valuesToEnums.put(BUFFER_SIZE, "BUFFER_SIZE");
        BUFFER_USAGE = GL.GL_BUFFER_USAGE;    valuesToEnums.put(BUFFER_USAGE, "BUFFER_USAGE");

        POINTS         = GL.GL_POINTS;    valuesToEnums.put(POINTS, "POINTS");
        LINE_STRIP     = GL.GL_LINE_STRIP;    valuesToEnums.put(LINE_STRIP, "LINE_STRIP");
        LINE_LOOP      = GL.GL_LINE_LOOP;    valuesToEnums.put(LINE_LOOP, "LINE_LOOP");
        LINES          = GL.GL_LINES;    valuesToEnums.put(LINES, "LINES");
        TRIANGLE_FAN   = GL.GL_TRIANGLE_FAN;    valuesToEnums.put(TRIANGLE_FAN, "TRIANGLE_FAN");
        TRIANGLE_STRIP = GL.GL_TRIANGLE_STRIP;    valuesToEnums.put(TRIANGLE_STRIP, "TRIANGLE_STRIP");
        TRIANGLES      = GL.GL_TRIANGLES;    valuesToEnums.put(TRIANGLES, "TRIANGLES");

        CULL_FACE      = GL.GL_CULL_FACE;    valuesToEnums.put(CULL_FACE, "CULL_FACE");
        FRONT          = GL.GL_FRONT;    valuesToEnums.put(FRONT, "FRONT");
        BACK           = GL.GL_BACK;    valuesToEnums.put(BACK, "BACK");
        FRONT_AND_BACK = GL.GL_FRONT_AND_BACK;    valuesToEnums.put(FRONT_AND_BACK, "FRONT_AND_BACK");

        POLYGON_OFFSET_FILL = GL.GL_POLYGON_OFFSET_FILL;    valuesToEnums.put(POLYGON_OFFSET_FILL, "POLYGON_OFFSET_FILL");

        UNPACK_ALIGNMENT = GL.GL_UNPACK_ALIGNMENT;    valuesToEnums.put(UNPACK_ALIGNMENT, "UNPACK_ALIGNMENT");
        PACK_ALIGNMENT   = GL.GL_PACK_ALIGNMENT;    valuesToEnums.put(PACK_ALIGNMENT, "PACK_ALIGNMENT");

        TEXTURE_2D        = GL.GL_TEXTURE_2D;    valuesToEnums.put(TEXTURE_2D, "TEXTURE_2D");
        TEXTURE_RECTANGLE = GL2GL3.GL_TEXTURE_RECTANGLE;    valuesToEnums.put(TEXTURE_RECTANGLE, "TEXTURE_RECTANGLE");

        TEXTURE_BINDING_2D        = GL.GL_TEXTURE_BINDING_2D;    valuesToEnums.put(TEXTURE_BINDING_2D, "TEXTURE_BINDING_2D");
        TEXTURE_BINDING_RECTANGLE = GL2GL3.GL_TEXTURE_BINDING_RECTANGLE;    valuesToEnums.put(TEXTURE_BINDING_RECTANGLE, "TEXTURE_BINDING_RECTANGLE");

        MAX_TEXTURE_SIZE           = GL.GL_MAX_TEXTURE_SIZE;    valuesToEnums.put(MAX_TEXTURE_SIZE, "MAX_TEXTURE_SIZE");
        TEXTURE_MAX_ANISOTROPY     = GL.GL_TEXTURE_MAX_ANISOTROPY_EXT;    valuesToEnums.put(TEXTURE_MAX_ANISOTROPY, "TEXTURE_MAX_ANISOTROPY");
        MAX_TEXTURE_MAX_ANISOTROPY = GL.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT;    valuesToEnums.put(MAX_TEXTURE_MAX_ANISOTROPY, "MAX_TEXTURE_MAX_ANISOTROPY");

        MAX_VERTEX_TEXTURE_IMAGE_UNITS   = GL2ES2.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS;    valuesToEnums.put(MAX_VERTEX_TEXTURE_IMAGE_UNITS, "MAX_VERTEX_TEXTURE_IMAGE_UNITS");
        MAX_TEXTURE_IMAGE_UNITS          = GL2ES2.GL_MAX_TEXTURE_IMAGE_UNITS;    valuesToEnums.put(MAX_TEXTURE_IMAGE_UNITS, "MAX_TEXTURE_IMAGE_UNITS");
        MAX_COMBINED_TEXTURE_IMAGE_UNITS = GL2ES2.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;    valuesToEnums.put(MAX_COMBINED_TEXTURE_IMAGE_UNITS, "MAX_COMBINED_TEXTURE_IMAGE_UNITS");

        NUM_COMPRESSED_TEXTURE_FORMATS = GL.GL_NUM_COMPRESSED_TEXTURE_FORMATS;    valuesToEnums.put(NUM_COMPRESSED_TEXTURE_FORMATS, "NUM_COMPRESSED_TEXTURE_FORMATS");
        COMPRESSED_TEXTURE_FORMATS     = GL.GL_COMPRESSED_TEXTURE_FORMATS;    valuesToEnums.put(COMPRESSED_TEXTURE_FORMATS, "COMPRESSED_TEXTURE_FORMATS");

        NEAREST               = GL.GL_NEAREST;    valuesToEnums.put(NEAREST, "NEAREST");
        LINEAR                = GL.GL_LINEAR;    valuesToEnums.put(LINEAR, "LINEAR");
        LINEAR_MIPMAP_NEAREST = GL.GL_LINEAR_MIPMAP_NEAREST;    valuesToEnums.put(LINEAR_MIPMAP_NEAREST, "LINEAR_MIPMAP_NEAREST");
        LINEAR_MIPMAP_LINEAR  = GL.GL_LINEAR_MIPMAP_LINEAR;    valuesToEnums.put(LINEAR_MIPMAP_LINEAR, "LINEAR_MIPMAP_LINEAR");

        CLAMP_TO_EDGE = GL.GL_CLAMP_TO_EDGE;    valuesToEnums.put(CLAMP_TO_EDGE, "CLAMP_TO_EDGE");
        REPEAT        = GL.GL_REPEAT;    valuesToEnums.put(REPEAT, "REPEAT");

        TEXTURE0           = GL.GL_TEXTURE0;    valuesToEnums.put(TEXTURE0, "TEXTURE0");
        TEXTURE1           = GL.GL_TEXTURE1;    valuesToEnums.put(TEXTURE1, "TEXTURE1");
        TEXTURE2           = GL.GL_TEXTURE2;    valuesToEnums.put(TEXTURE2, "TEXTURE2");
        TEXTURE3           = GL.GL_TEXTURE3;    valuesToEnums.put(TEXTURE3, "TEXTURE3");
        TEXTURE_MIN_FILTER = GL.GL_TEXTURE_MIN_FILTER;    valuesToEnums.put(TEXTURE_MIN_FILTER, "TEXTURE_MIN_FILTER");
        TEXTURE_MAG_FILTER = GL.GL_TEXTURE_MAG_FILTER;    valuesToEnums.put(TEXTURE_MAG_FILTER, "TEXTURE_MAG_FILTER");
        TEXTURE_WRAP_S     = GL.GL_TEXTURE_WRAP_S;    valuesToEnums.put(TEXTURE_WRAP_S, "TEXTURE_WRAP_S");
        TEXTURE_WRAP_T     = GL.GL_TEXTURE_WRAP_T;    valuesToEnums.put(TEXTURE_WRAP_T, "TEXTURE_WRAP_T");
        TEXTURE_WRAP_R     = GL2ES2.GL_TEXTURE_WRAP_R;    valuesToEnums.put(TEXTURE_WRAP_R, "TEXTURE_WRAP_R");

        TEXTURE_CUBE_MAP = GL.GL_TEXTURE_CUBE_MAP;    valuesToEnums.put(TEXTURE_CUBE_MAP, "TEXTURE_CUBE_MAP");
        TEXTURE_CUBE_MAP_POSITIVE_X = GL.GL_TEXTURE_CUBE_MAP_POSITIVE_X;    valuesToEnums.put(TEXTURE_CUBE_MAP_POSITIVE_X, "TEXTURE_CUBE_MAP_POSITIVE_X");
        TEXTURE_CUBE_MAP_POSITIVE_Y = GL.GL_TEXTURE_CUBE_MAP_POSITIVE_Y;    valuesToEnums.put(TEXTURE_CUBE_MAP_POSITIVE_Y, "TEXTURE_CUBE_MAP_POSITIVE_Y");
        TEXTURE_CUBE_MAP_POSITIVE_Z = GL.GL_TEXTURE_CUBE_MAP_POSITIVE_Z;    valuesToEnums.put(TEXTURE_CUBE_MAP_POSITIVE_Z, "TEXTURE_CUBE_MAP_POSITIVE_Z");
        TEXTURE_CUBE_MAP_NEGATIVE_X = GL.GL_TEXTURE_CUBE_MAP_NEGATIVE_X;    valuesToEnums.put(TEXTURE_CUBE_MAP_NEGATIVE_X, "TEXTURE_CUBE_MAP_NEGATIVE_X");
        TEXTURE_CUBE_MAP_NEGATIVE_Y = GL.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y;    valuesToEnums.put(TEXTURE_CUBE_MAP_NEGATIVE_Y, "TEXTURE_CUBE_MAP_NEGATIVE_Y");
        TEXTURE_CUBE_MAP_NEGATIVE_Z = GL.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z;    valuesToEnums.put(TEXTURE_CUBE_MAP_NEGATIVE_Z, "TEXTURE_CUBE_MAP_NEGATIVE_Z");

        VERTEX_SHADER        = GL2ES2.GL_VERTEX_SHADER;    valuesToEnums.put(VERTEX_SHADER, "VERTEX_SHADER");
        FRAGMENT_SHADER      = GL2ES2.GL_FRAGMENT_SHADER;    valuesToEnums.put(FRAGMENT_SHADER, "FRAGMENT_SHADER");
        INFO_LOG_LENGTH      = GL2ES2.GL_INFO_LOG_LENGTH;    valuesToEnums.put(INFO_LOG_LENGTH, "INFO_LOG_LENGTH");
        SHADER_SOURCE_LENGTH = GL2ES2.GL_SHADER_SOURCE_LENGTH;    valuesToEnums.put(SHADER_SOURCE_LENGTH, "SHADER_SOURCE_LENGTH");
        COMPILE_STATUS       = GL2ES2.GL_COMPILE_STATUS;    valuesToEnums.put(COMPILE_STATUS, "COMPILE_STATUS");
        LINK_STATUS          = GL2ES2.GL_LINK_STATUS;    valuesToEnums.put(LINK_STATUS, "LINK_STATUS");
        VALIDATE_STATUS      = GL2ES2.GL_VALIDATE_STATUS;    valuesToEnums.put(VALIDATE_STATUS, "VALIDATE_STATUS");
        SHADER_TYPE          = GL2ES2.GL_SHADER_TYPE;    valuesToEnums.put(SHADER_TYPE, "SHADER_TYPE");
        DELETE_STATUS        = GL2ES2.GL_DELETE_STATUS;    valuesToEnums.put(DELETE_STATUS, "DELETE_STATUS");

        FLOAT_VEC2   = GL2ES2.GL_FLOAT_VEC2;    valuesToEnums.put(FLOAT_VEC2, "FLOAT_VEC2");
        FLOAT_VEC3   = GL2ES2.GL_FLOAT_VEC3;    valuesToEnums.put(FLOAT_VEC3, "FLOAT_VEC3");
        FLOAT_VEC4   = GL2ES2.GL_FLOAT_VEC4;    valuesToEnums.put(FLOAT_VEC4, "FLOAT_VEC4");
        FLOAT_MAT2   = GL2ES2.GL_FLOAT_MAT2;    valuesToEnums.put(FLOAT_MAT2, "FLOAT_MAT2");
        FLOAT_MAT3   = GL2ES2.GL_FLOAT_MAT3;    valuesToEnums.put(FLOAT_MAT3, "FLOAT_MAT3");
        FLOAT_MAT4   = GL2ES2.GL_FLOAT_MAT4;    valuesToEnums.put(FLOAT_MAT4, "FLOAT_MAT4");
        INT_VEC2     = GL2ES2.GL_INT_VEC2;    valuesToEnums.put(INT_VEC2, "INT_VEC2");
        INT_VEC3     = GL2ES2.GL_INT_VEC3;    valuesToEnums.put(INT_VEC3, "INT_VEC3");
        INT_VEC4     = GL2ES2.GL_INT_VEC4;    valuesToEnums.put(INT_VEC4, "INT_VEC4");
        BOOL_VEC2    = GL2ES2.GL_BOOL_VEC2;    valuesToEnums.put(BOOL_VEC2, "BOOL_VEC2");
        BOOL_VEC3    = GL2ES2.GL_BOOL_VEC3;    valuesToEnums.put(BOOL_VEC3, "BOOL_VEC3");
        BOOL_VEC4    = GL2ES2.GL_BOOL_VEC4;    valuesToEnums.put(BOOL_VEC4, "BOOL_VEC4");
        SAMPLER_2D   = GL2ES2.GL_SAMPLER_2D;    valuesToEnums.put(SAMPLER_2D, "SAMPLER_2D");
        SAMPLER_CUBE = GL2ES2.GL_SAMPLER_CUBE;    valuesToEnums.put(SAMPLER_CUBE, "SAMPLER_CUBE");

        LOW_FLOAT    = GL2ES2.GL_LOW_FLOAT;    valuesToEnums.put(LOW_FLOAT, "LOW_FLOAT");
        MEDIUM_FLOAT = GL2ES2.GL_MEDIUM_FLOAT;    valuesToEnums.put(MEDIUM_FLOAT, "MEDIUM_FLOAT");
        HIGH_FLOAT   = GL2ES2.GL_HIGH_FLOAT;    valuesToEnums.put(HIGH_FLOAT, "HIGH_FLOAT");
        LOW_INT      = GL2ES2.GL_LOW_INT;    valuesToEnums.put(LOW_INT, "LOW_INT");
        MEDIUM_INT   = GL2ES2.GL_MEDIUM_INT;    valuesToEnums.put(MEDIUM_INT, "MEDIUM_INT");
        HIGH_INT     = GL2ES2.GL_HIGH_INT;    valuesToEnums.put(HIGH_INT, "HIGH_INT");

        CURRENT_VERTEX_ATTRIB = GL2ES2.GL_CURRENT_VERTEX_ATTRIB;    valuesToEnums.put(CURRENT_VERTEX_ATTRIB, "CURRENT_VERTEX_ATTRIB");

        VERTEX_ATTRIB_ARRAY_BUFFER_BINDING = GL2ES2.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING;    valuesToEnums.put(VERTEX_ATTRIB_ARRAY_BUFFER_BINDING, "VERTEX_ATTRIB_ARRAY_BUFFER_BINDING");
        VERTEX_ATTRIB_ARRAY_ENABLED        = GL2ES2.GL_VERTEX_ATTRIB_ARRAY_ENABLED;    valuesToEnums.put(VERTEX_ATTRIB_ARRAY_ENABLED, "VERTEX_ATTRIB_ARRAY_ENABLED");
        VERTEX_ATTRIB_ARRAY_SIZE           = GL2ES2.GL_VERTEX_ATTRIB_ARRAY_SIZE;    valuesToEnums.put(VERTEX_ATTRIB_ARRAY_SIZE, "VERTEX_ATTRIB_ARRAY_SIZE");
        VERTEX_ATTRIB_ARRAY_STRIDE         = GL2ES2.GL_VERTEX_ATTRIB_ARRAY_STRIDE;    valuesToEnums.put(VERTEX_ATTRIB_ARRAY_STRIDE, "VERTEX_ATTRIB_ARRAY_STRIDE");
        VERTEX_ATTRIB_ARRAY_TYPE           = GL2ES2.GL_VERTEX_ATTRIB_ARRAY_TYPE;    valuesToEnums.put(VERTEX_ATTRIB_ARRAY_TYPE, "VERTEX_ATTRIB_ARRAY_TYPE");
        VERTEX_ATTRIB_ARRAY_NORMALIZED     = GL2ES2.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED;    valuesToEnums.put(VERTEX_ATTRIB_ARRAY_NORMALIZED, "VERTEX_ATTRIB_ARRAY_NORMALIZED");
        VERTEX_ATTRIB_ARRAY_POINTER        = GL2ES2.GL_VERTEX_ATTRIB_ARRAY_POINTER;    valuesToEnums.put(VERTEX_ATTRIB_ARRAY_POINTER, "VERTEX_ATTRIB_ARRAY_POINTER");

        BLEND               = GL.GL_BLEND;    valuesToEnums.put(BLEND, "BLEND");
        ONE                 = GL.GL_ONE;    valuesToEnums.put(ONE, "ONE");
        ZERO                = GL.GL_ZERO;    valuesToEnums.put(ZERO, "ZERO");
        SRC_ALPHA           = GL.GL_SRC_ALPHA;    valuesToEnums.put(SRC_ALPHA, "SRC_ALPHA");
        DST_ALPHA           = GL.GL_DST_ALPHA;    valuesToEnums.put(DST_ALPHA, "DST_ALPHA");
        ONE_MINUS_SRC_ALPHA = GL.GL_ONE_MINUS_SRC_ALPHA;    valuesToEnums.put(ONE_MINUS_SRC_ALPHA, "ONE_MINUS_SRC_ALPHA");
        ONE_MINUS_DST_COLOR = GL.GL_ONE_MINUS_DST_COLOR;    valuesToEnums.put(ONE_MINUS_DST_COLOR, "ONE_MINUS_DST_COLOR");
        ONE_MINUS_SRC_COLOR = GL.GL_ONE_MINUS_SRC_COLOR;    valuesToEnums.put(ONE_MINUS_SRC_COLOR, "ONE_MINUS_SRC_COLOR");
        DST_COLOR           = GL.GL_DST_COLOR;    valuesToEnums.put(DST_COLOR, "DST_COLOR");
        SRC_COLOR           = GL.GL_SRC_COLOR;    valuesToEnums.put(SRC_COLOR, "SRC_COLOR");

        SAMPLE_ALPHA_TO_COVERAGE = GL.GL_SAMPLE_ALPHA_TO_COVERAGE;    valuesToEnums.put(SAMPLE_ALPHA_TO_COVERAGE, "SAMPLE_ALPHA_TO_COVERAGE");
        SAMPLE_COVERAGE          = GL.GL_SAMPLE_COVERAGE;    valuesToEnums.put(SAMPLE_COVERAGE, "SAMPLE_COVERAGE");

        KEEP      = GL.GL_KEEP;    valuesToEnums.put(KEEP, "KEEP");
        REPLACE   = GL.GL_REPLACE;    valuesToEnums.put(REPLACE, "REPLACE");
        INCR      = GL.GL_INCR;    valuesToEnums.put(INCR, "INCR");
        DECR      = GL.GL_DECR;    valuesToEnums.put(DECR, "DECR");
        INVERT    = GL.GL_INVERT;    valuesToEnums.put(INVERT, "INVERT");
        INCR_WRAP = GL.GL_INCR_WRAP;    valuesToEnums.put(INCR_WRAP, "INCR_WRAP");
        DECR_WRAP = GL.GL_DECR_WRAP;    valuesToEnums.put(DECR_WRAP, "DECR_WRAP");
        NEVER     = GL.GL_NEVER;    valuesToEnums.put(NEVER, "NEVER");
        ALWAYS    = GL.GL_ALWAYS;    valuesToEnums.put(ALWAYS, "ALWAYS");

        EQUAL    = GL.GL_EQUAL;    valuesToEnums.put(EQUAL, "EQUAL");
        LESS     = GL.GL_LESS;    valuesToEnums.put(LESS, "LESS");
        LEQUAL   = GL.GL_LEQUAL;    valuesToEnums.put(LEQUAL, "LEQUAL");
        GREATER  = GL.GL_GREATER;    valuesToEnums.put(GREATER, "GREATER");
        GEQUAL   = GL.GL_GEQUAL;    valuesToEnums.put(GEQUAL, "GEQUAL");
        NOTEQUAL = GL.GL_NOTEQUAL;    valuesToEnums.put(NOTEQUAL, "NOTEQUAL");

        FUNC_ADD              = GL.GL_FUNC_ADD;    valuesToEnums.put(FUNC_ADD, "FUNC_ADD");
        FUNC_MIN              = GL2ES3.GL_MIN;    valuesToEnums.put(FUNC_MIN, "FUNC_MIN");
        FUNC_MAX              = GL2ES3.GL_MAX;    valuesToEnums.put(FUNC_MAX, "FUNC_MAX");
        FUNC_REVERSE_SUBTRACT = GL.GL_FUNC_REVERSE_SUBTRACT;    valuesToEnums.put(FUNC_REVERSE_SUBTRACT, "FUNC_REVERSE_SUBTRACT");
        FUNC_SUBTRACT         = GL.GL_FUNC_SUBTRACT;    valuesToEnums.put(FUNC_SUBTRACT, "FUNC_SUBTRACT");

        DITHER = GL.GL_DITHER;    valuesToEnums.put(DITHER, "DITHER");

        CONSTANT_COLOR           = GL2ES2.GL_CONSTANT_COLOR;    valuesToEnums.put(CONSTANT_COLOR, "CONSTANT_COLOR");
        CONSTANT_ALPHA           = GL2ES2.GL_CONSTANT_ALPHA;    valuesToEnums.put(CONSTANT_ALPHA, "CONSTANT_ALPHA");
        ONE_MINUS_CONSTANT_COLOR = GL2ES2.GL_ONE_MINUS_CONSTANT_COLOR;    valuesToEnums.put(ONE_MINUS_CONSTANT_COLOR, "ONE_MINUS_CONSTANT_COLOR");
        ONE_MINUS_CONSTANT_ALPHA = GL2ES2.GL_ONE_MINUS_CONSTANT_ALPHA;    valuesToEnums.put(ONE_MINUS_CONSTANT_ALPHA, "ONE_MINUS_CONSTANT_ALPHA");
        SRC_ALPHA_SATURATE       = GL.GL_SRC_ALPHA_SATURATE;    valuesToEnums.put(SRC_ALPHA_SATURATE, "SRC_ALPHA_SATURATE");

        SCISSOR_TEST    = GL.GL_SCISSOR_TEST;    valuesToEnums.put(SCISSOR_TEST, "SCISSOR_TEST");
        STENCIL_TEST    = GL.GL_STENCIL_TEST;    valuesToEnums.put(STENCIL_TEST, "STENCIL_TEST");
        DEPTH_TEST      = GL.GL_DEPTH_TEST;    valuesToEnums.put(DEPTH_TEST, "DEPTH_TEST");
        DEPTH_WRITEMASK = GL.GL_DEPTH_WRITEMASK;    valuesToEnums.put(DEPTH_WRITEMASK, "DEPTH_WRITEMASK");

        COLOR_BUFFER_BIT   = GL.GL_COLOR_BUFFER_BIT;    valuesToEnums.put(COLOR_BUFFER_BIT, "COLOR_BUFFER_BIT");
        DEPTH_BUFFER_BIT   = GL.GL_DEPTH_BUFFER_BIT;    valuesToEnums.put(DEPTH_BUFFER_BIT, "DEPTH_BUFFER_BIT");
        STENCIL_BUFFER_BIT = GL.GL_STENCIL_BUFFER_BIT;    valuesToEnums.put(STENCIL_BUFFER_BIT, "STENCIL_BUFFER_BIT");

        FRAMEBUFFER        = GL.GL_FRAMEBUFFER;    valuesToEnums.put(FRAMEBUFFER, "FRAMEBUFFER");
        COLOR_ATTACHMENT0  = GL.GL_COLOR_ATTACHMENT0;    valuesToEnums.put(COLOR_ATTACHMENT0, "COLOR_ATTACHMENT0");
        COLOR_ATTACHMENT1  = GL2ES2.GL_COLOR_ATTACHMENT1;    valuesToEnums.put(COLOR_ATTACHMENT1, "COLOR_ATTACHMENT1");
        COLOR_ATTACHMENT2  = GL2ES2.GL_COLOR_ATTACHMENT2;    valuesToEnums.put(COLOR_ATTACHMENT2, "COLOR_ATTACHMENT2");
        COLOR_ATTACHMENT3  = GL2ES2.GL_COLOR_ATTACHMENT3;    valuesToEnums.put(COLOR_ATTACHMENT3, "COLOR_ATTACHMENT3");
        RENDERBUFFER       = GL.GL_RENDERBUFFER;    valuesToEnums.put(RENDERBUFFER, "RENDERBUFFER");
        DEPTH_ATTACHMENT   = GL.GL_DEPTH_ATTACHMENT;    valuesToEnums.put(DEPTH_ATTACHMENT, "DEPTH_ATTACHMENT");
        STENCIL_ATTACHMENT = GL.GL_STENCIL_ATTACHMENT;    valuesToEnums.put(STENCIL_ATTACHMENT, "STENCIL_ATTACHMENT");
        READ_FRAMEBUFFER   = GL.GL_READ_FRAMEBUFFER;    valuesToEnums.put(READ_FRAMEBUFFER, "READ_FRAMEBUFFER");
        DRAW_FRAMEBUFFER   = GL.GL_DRAW_FRAMEBUFFER;    valuesToEnums.put(DRAW_FRAMEBUFFER, "DRAW_FRAMEBUFFER");

        DEPTH24_STENCIL8 = GL.GL_DEPTH24_STENCIL8;    valuesToEnums.put(DEPTH24_STENCIL8, "DEPTH24_STENCIL8");

        DEPTH_COMPONENT   = GL2ES2.GL_DEPTH_COMPONENT;    valuesToEnums.put(DEPTH_COMPONENT, "DEPTH_COMPONENT");
        DEPTH_COMPONENT16 = GL.GL_DEPTH_COMPONENT16;    valuesToEnums.put(DEPTH_COMPONENT16, "DEPTH_COMPONENT16");
        DEPTH_COMPONENT24 = GL.GL_DEPTH_COMPONENT24;    valuesToEnums.put(DEPTH_COMPONENT24, "DEPTH_COMPONENT24");
        DEPTH_COMPONENT32 = GL.GL_DEPTH_COMPONENT32;    valuesToEnums.put(DEPTH_COMPONENT32, "DEPTH_COMPONENT32");

        STENCIL_INDEX  = GL2ES2.GL_STENCIL_INDEX;    valuesToEnums.put(STENCIL_INDEX, "STENCIL_INDEX");
        STENCIL_INDEX1 = GL.GL_STENCIL_INDEX1;    valuesToEnums.put(STENCIL_INDEX1, "STENCIL_INDEX1");
        STENCIL_INDEX4 = GL.GL_STENCIL_INDEX4;    valuesToEnums.put(STENCIL_INDEX4, "STENCIL_INDEX4");
        STENCIL_INDEX8 = GL.GL_STENCIL_INDEX8;    valuesToEnums.put(STENCIL_INDEX8, "STENCIL_INDEX8");

        DEPTH_STENCIL = GL.GL_DEPTH_STENCIL;    valuesToEnums.put(DEPTH_STENCIL, "DEPTH_STENCIL");

        FRAMEBUFFER_COMPLETE                      = GL.GL_FRAMEBUFFER_COMPLETE;    valuesToEnums.put(FRAMEBUFFER_COMPLETE, "FRAMEBUFFER_COMPLETE");
        FRAMEBUFFER_UNDEFINED                     = GL2ES3.GL_FRAMEBUFFER_UNDEFINED;    valuesToEnums.put(FRAMEBUFFER_UNDEFINED, "FRAMEBUFFER_UNDEFINED");
        FRAMEBUFFER_INCOMPLETE_ATTACHMENT         = GL.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT;    valuesToEnums.put(FRAMEBUFFER_INCOMPLETE_ATTACHMENT, "FRAMEBUFFER_INCOMPLETE_ATTACHMENT");
        FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = GL.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT;    valuesToEnums.put(FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT, "FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT");
        FRAMEBUFFER_INCOMPLETE_DIMENSIONS         = GL.GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS;    valuesToEnums.put(FRAMEBUFFER_INCOMPLETE_DIMENSIONS, "FRAMEBUFFER_INCOMPLETE_DIMENSIONS");
        FRAMEBUFFER_INCOMPLETE_FORMATS            = GL.GL_FRAMEBUFFER_INCOMPLETE_FORMATS;    valuesToEnums.put(FRAMEBUFFER_INCOMPLETE_FORMATS, "FRAMEBUFFER_INCOMPLETE_FORMATS");
        FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER        = GL2GL3.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER;    valuesToEnums.put(FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER, "FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER");
        FRAMEBUFFER_INCOMPLETE_READ_BUFFER        = GL2GL3.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER;    valuesToEnums.put(FRAMEBUFFER_INCOMPLETE_READ_BUFFER, "FRAMEBUFFER_INCOMPLETE_READ_BUFFER");
        FRAMEBUFFER_UNSUPPORTED                   = GL.GL_FRAMEBUFFER_UNSUPPORTED;    valuesToEnums.put(FRAMEBUFFER_UNSUPPORTED, "FRAMEBUFFER_UNSUPPORTED");
        FRAMEBUFFER_INCOMPLETE_MULTISAMPLE        = GL.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE;    valuesToEnums.put(FRAMEBUFFER_INCOMPLETE_MULTISAMPLE, "FRAMEBUFFER_INCOMPLETE_MULTISAMPLE");
        FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS      = GL3ES3.GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS;    valuesToEnums.put(FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS, "FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS");

        FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE           = GL.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE;    valuesToEnums.put(FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE, "FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE");
        FRAMEBUFFER_ATTACHMENT_OBJECT_NAME           = GL.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;    valuesToEnums.put(FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, "FRAMEBUFFER_ATTACHMENT_OBJECT_NAME");
        FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL         = GL.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL;    valuesToEnums.put(FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL, "FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL");
        FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE = GL.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE;    valuesToEnums.put(FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE, "FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE");

        RENDERBUFFER_WIDTH           = GL.GL_RENDERBUFFER_WIDTH;    valuesToEnums.put(RENDERBUFFER_WIDTH, "RENDERBUFFER_WIDTH");
        RENDERBUFFER_HEIGHT          = GL.GL_RENDERBUFFER_HEIGHT;    valuesToEnums.put(RENDERBUFFER_HEIGHT, "RENDERBUFFER_HEIGHT");
        RENDERBUFFER_RED_SIZE        = GL.GL_RENDERBUFFER_RED_SIZE;    valuesToEnums.put(RENDERBUFFER_RED_SIZE, "RENDERBUFFER_RED_SIZE");
        RENDERBUFFER_GREEN_SIZE      = GL.GL_RENDERBUFFER_GREEN_SIZE;    valuesToEnums.put(RENDERBUFFER_GREEN_SIZE, "RENDERBUFFER_GREEN_SIZE");
        RENDERBUFFER_BLUE_SIZE       = GL.GL_RENDERBUFFER_BLUE_SIZE;    valuesToEnums.put(RENDERBUFFER_BLUE_SIZE, "RENDERBUFFER_BLUE_SIZE");
        RENDERBUFFER_ALPHA_SIZE      = GL.GL_RENDERBUFFER_ALPHA_SIZE;    valuesToEnums.put(RENDERBUFFER_ALPHA_SIZE, "RENDERBUFFER_ALPHA_SIZE");
        RENDERBUFFER_DEPTH_SIZE      = GL.GL_RENDERBUFFER_DEPTH_SIZE;    valuesToEnums.put(RENDERBUFFER_DEPTH_SIZE, "RENDERBUFFER_DEPTH_SIZE");
        RENDERBUFFER_STENCIL_SIZE    = GL.GL_RENDERBUFFER_STENCIL_SIZE;    valuesToEnums.put(RENDERBUFFER_STENCIL_SIZE, "RENDERBUFFER_STENCIL_SIZE");
        RENDERBUFFER_INTERNAL_FORMAT = GL.GL_RENDERBUFFER_INTERNAL_FORMAT;    valuesToEnums.put(RENDERBUFFER_INTERNAL_FORMAT, "RENDERBUFFER_INTERNAL_FORMAT");

        MULTISAMPLE    = GL.GL_MULTISAMPLE;    valuesToEnums.put(MULTISAMPLE, "MULTISAMPLE");
        LINE_SMOOTH    = GL.GL_LINE_SMOOTH;    valuesToEnums.put(LINE_SMOOTH, "LINE_SMOOTH");
        POLYGON_SMOOTH = GL2GL3.GL_POLYGON_SMOOTH;    valuesToEnums.put(POLYGON_SMOOTH, "POLYGON_SMOOTH");

        SYNC_GPU_COMMANDS_COMPLETE = GL3ES3.GL_SYNC_GPU_COMMANDS_COMPLETE;    valuesToEnums.put(SYNC_GPU_COMMANDS_COMPLETE, "SYNC_GPU_COMMANDS_COMPLETE");
        ALREADY_SIGNALED           = GL3ES3.GL_ALREADY_SIGNALED;    valuesToEnums.put(ALREADY_SIGNALED, "ALREADY_SIGNALED");
        CONDITION_SATISFIED        = GL3ES3.GL_CONDITION_SATISFIED;    valuesToEnums.put(CONDITION_SATISFIED, "CONDITION_SATISFIED");

  }

  ///////////////////////////////////////////////////////////

  // Special Functions

  @Override
  public void flush() {
        report("flush");
    gl.glFlush();
  }

  @Override
  public void finish() {
        report("finish");
    gl.glFinish();
  }

  @Override
  public void hint(int target, int hint) {
        report("hint");
    gl.glHint(target, hint);
  }

  ///////////////////////////////////////////////////////////

  // State and State Requests

  @Override
  public void enable(int value) {
    report("enable");
//    System.out.println("ENABLE "+valuesToEnums.get(value));
    if (-1 < value) {
      gl.glEnable(value);
    }
  }

  @Override
  public void disable(int value) {
    report("disable");
//    System.out.println("DISABLE "+valuesToEnums.get(value));
    if (-1 < value) {
      gl.glDisable(value);
    }
  }

  @Override
  public void getBooleanv(int value, IntBuffer data) {
        report("getBooleanv");
    if (-1 < value) {
      if (byteBuffer.capacity() < data.capacity()) {
        byteBuffer = allocateDirectByteBuffer(data.capacity());
      }
      gl.glGetBooleanv(value, byteBuffer);
      for (int i = 0; i < data.capacity(); i++) {
        data.put(i, byteBuffer.get(i));
      }
    } else {
      fillIntBuffer(data, 0, data.capacity() - 1, 0);
    }
  }

  @Override
  public void getIntegerv(int value, IntBuffer data) {
        report("getIntegerv");

    if (-1 < value) {
      gl.glGetIntegerv(value, data);
    } else {
      fillIntBuffer(data, 0, data.capacity() - 1, 0);
    }
  }

  @Override
  public void getFloatv(int value, FloatBuffer data) {
        report("getFloatv");
    if (-1 < value) {
      gl.glGetFloatv(value, data);
    } else {
      fillFloatBuffer(data, 0, data.capacity() - 1, 0);
    }

  }

  @Override
  public boolean isEnabled(int value) {
        report("isEnabled");
    return gl.glIsEnabled(value);
  }

  @Override
  public String getString(int name) {
        report("getString");
    return gl.glGetString(name);
  }

  ///////////////////////////////////////////////////////////

  // Error Handling

  @Override
  public int getError() {
        report("getError");
    return gl.glGetError();
  }

  @Override
  public String errorString(int err) {
        report("errorString");
    return glu.gluErrorString(err);
  }

  //////////////////////////////////////////////////////////////////////////////

  // Buffer Objects

  @Override
  public void genBuffers(int n, IntBuffer buffers) {
        report("genBuffers");
    gl.glGenBuffers(n, buffers);
  }

  @Override
  public void deleteBuffers(int n, IntBuffer buffers) {
        report("deleteBuffers");
    gl.glDeleteBuffers(n, buffers);
  }

  @Override
  public void bindBuffer(int target, int buffer) {
        report("bindBuffer");
//        System.out.println("bindBuffer: "+buffer);
    gl.glBindBuffer(target, buffer);
  }

  @Override
  public void bufferData(int target, int size, Buffer data, int usage) {
        report("bufferData");
//        printBuffer("bufferData ", data);
    gl.glBufferData(target, size, data, usage);
  }

  @Override
  public void bufferSubData(int target, int offset, int size, Buffer data) {
        report("bufferSubData");
    gl.glBufferSubData(target, offset, size, data);
  }

  @Override
  public void isBuffer(int buffer) {
        report("isBuffer");
    gl.glIsBuffer(buffer);
  }

  @Override
  public void getBufferParameteriv(int target, int value, IntBuffer data) {
        report("getBufferParameteriv");
    gl.glGetBufferParameteriv(target, value, data);
  }

  @Override
  public ByteBuffer mapBuffer(int target, int access) {
        report("mapBuffer");
    return gl2.glMapBuffer(target, access);
  }

  @Override
  public ByteBuffer mapBufferRange(int target, int offset, int length, int access) {
        report("mapBufferRange");
    if (gl2x != null) {
      return gl2x.glMapBufferRange(target, offset, length, access);
    } else if (gl3 != null) {
      return gl3.glMapBufferRange(target, offset, length, access);
    } else {
      throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glMapBufferRange()"));
    }
  }

  @Override
  public void unmapBuffer(int target) {
        report("unmapBuffer");
    gl2.glUnmapBuffer(target);
  }

  //////////////////////////////////////////////////////////////////////////////

  // Synchronization

  @Override
  public long fenceSync(int condition, int flags) {
        report("fenceSync");
    if (gl3es3 != null) {
      return gl3es3.glFenceSync(condition, flags);
    } else {
      throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "fenceSync()"));
    }
  }

  @Override
  public void deleteSync(long sync) {
        report("deleteSync");
    if (gl3es3 != null) {
      gl3es3.glDeleteSync(sync);
    } else {
      throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "deleteSync()"));
    }
  }

  @Override
  public int clientWaitSync(long sync, int flags, long timeout) {
        report("clientWaitSync");
    if (gl3es3 != null) {
      return gl3es3.glClientWaitSync(sync, flags, timeout);
    } else {
      throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "clientWaitSync()"));
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  // Viewport and Clipping

  @Override
  public void depthRangef(float n, float f) {
        report("depthRangef");
    gl.glDepthRangef(n, f);
    System.out.println("depthRange "+n+" "+f);
  }

  @Override
  public void viewport(int x, int y, int w, int h) {
        report("viewport");
    float scale = getPixelScale();
    viewportImpl((int)scale * x, (int)(scale * y), (int)(scale * w), (int)(scale * h));
  }

  @Override
  protected void viewportImpl(int x, int y, int w, int h) {
        report("viewportImpl");
    gl.glViewport(x, y, w, h);
  }

  //////////////////////////////////////////////////////////////////////////////

  // Reading Pixels

  @Override
  protected void readPixelsImpl(int x, int y, int width, int height, int format, int type, Buffer buffer) {
        report("readPixelsImpl");
    gl.glReadPixels(x, y, width, height, format, type, buffer);
  }

  @Override
  protected void readPixelsImpl(int x, int y, int width, int height, int format, int type, long offset) {
        report("readPixelsImpl");
    gl.glReadPixels(x, y, width, height, format, type, 0);
  }

  //////////////////////////////////////////////////////////////////////////////

  // Vertices

  @Override
  public void vertexAttrib1f(int index, float value) {
        report("vertexAttrib1f");
    gl2.glVertexAttrib1f(index, value);
  }

  @Override
  public void vertexAttrib2f(int index, float value0, float value1) {
        report("vertexAttrib2f");
    gl2.glVertexAttrib2f(index, value0, value1);
  }

  @Override
  public void vertexAttrib3f(int index, float value0, float value1, float value2) {
        report("vertexAttrib3f");
    gl2.glVertexAttrib3f(index, value0, value1, value2);
  }

  @Override
  public void vertexAttrib4f(int index, float value0, float value1, float value2, float value3) {
        report("vertexAttrib4f");
    gl2.glVertexAttrib4f(index, value0, value1, value2, value3);
  }

  @Override
  public void vertexAttrib1fv(int index, FloatBuffer values) {
        report("vertexAttrib1fv");
    gl2.glVertexAttrib1fv(index, values);
  }

  @Override
  public void vertexAttrib2fv(int index, FloatBuffer values) {
        report("vertexAttrib2fv");
    gl2.glVertexAttrib2fv(index, values);
  }

  @Override
  public void vertexAttrib3fv(int index, FloatBuffer values) {
        report("vertexAttrib3fv");
    gl2.glVertexAttrib3fv(index, values);
  }

  @Override
  public void vertexAttrib4fv(int index, FloatBuffer values) {
        report("vertexAttrib4fv");
    gl2.glVertexAttrib4fv(index, values);
  }

  @Override
  public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset) {
        report("vertexAttribPointer");
    gl2.glVertexAttribPointer(index, size, type, normalized, stride, offset);
  }

  @Override
  public void enableVertexAttribArray(int index) {
        report("enableVertexAttribArray");
    gl2.glEnableVertexAttribArray(index);
  }

  @Override
  public void disableVertexAttribArray(int index) {
        report("disableVertexAttribArray");
    gl2.glDisableVertexAttribArray(index);
  }

  @Override
  public void drawArraysImpl(int mode, int first, int count) {
        report("drawArraysImpl");
    gl.glDrawArrays(mode, first, count);
  }

  @Override
  public void drawElementsImpl(int mode, int count, int type, int offset) {
        report("drawElementsImpl");
    gl.glDrawElements(mode, count, type, offset);
  }

  //////////////////////////////////////////////////////////////////////////////

  // Rasterization

  @Override
  public void lineWidth(float width) {
        report("lineWidth");
    gl.glLineWidth(width);
  }

  @Override
  public void frontFace(int dir) {
        report("frontFace");
    gl.glFrontFace(dir);
  }

  @Override
  public void cullFace(int mode) {
        report("cullFace");
    gl.glCullFace(mode);
  }

  @Override
  public void polygonOffset(float factor, float units) {
        report("polygonOffset");
    gl.glPolygonOffset(factor, units);
  }

  //////////////////////////////////////////////////////////////////////////////

  // Pixel Rectangles

  @Override
  public void pixelStorei(int pname, int param) {
        report("pixelStorei");
    gl.glPixelStorei(pname, param);
  }

  ///////////////////////////////////////////////////////////

  // Texturing

  @Override
  public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, Buffer data) {
        report("texImage2D");
    gl.glTexImage2D(target, level, internalFormat, width, height, border, format, type, data);

  }

  @Override
  public void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        report("copyTexImage2D");
    gl.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);

  }

  @Override
  public void texSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, Buffer data) {
        report("texSubImage2D");
    gl.glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, data);
  }

  @Override
  public void copyTexSubImage2D(int target, int level, int xOffset, int yOffset, int x, int y, int width, int height) {
        report("copyTexSubImage2D");
    gl.glCopyTexSubImage2D(target, level, x, y, xOffset, yOffset, width, height);
  }

  @Override
  public void compressedTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int imageSize, Buffer data) {
        report("compressedTexImage2D");
    gl.glCompressedTexImage2D(target, level, internalFormat, width, height, border, imageSize, data);
  }

  @Override
  public void compressedTexSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int imageSize, Buffer data) {
        report("compressedTexSubImage2D");
    gl.glCompressedTexSubImage2D(target, level, xOffset, yOffset, width, height, format, imageSize, data);
  }

  @Override
  public void texParameteri(int target, int pname, int param) {
        report("texParameteri");
    gl.glTexParameteri(target, pname, param);
  }

  @Override
  public void texParameterf(int target, int pname, float param) {
        report("texParameterf");
    gl.glTexParameterf(target, pname, param);
  }

  @Override
  public void texParameteriv(int target, int pname, IntBuffer params) {
        report("texParameteriv");
    gl.glTexParameteriv(target, pname, params);
  }

  @Override
  public void texParameterfv(int target, int pname, FloatBuffer params) {
        report("texParameterfv");
    gl.glTexParameterfv(target, pname, params);
  }

  @Override
  public void generateMipmap(int target) {
        report("generateMipmap");
    gl.glGenerateMipmap(target);
  }

  @Override
  public void genTextures(int n, IntBuffer textures) {
        report("genTextures");
    gl.glGenTextures(n, textures);
  }

  @Override
  public void deleteTextures(int n, IntBuffer textures) {
        report("deleteTextures");
    gl.glDeleteTextures(n, textures);
  }

  @Override
  public void getTexParameteriv(int target, int pname, IntBuffer params) {
        report("getTexParameteriv");
    gl.glGetTexParameteriv(target, pname, params);
  }

  @Override
  public void getTexParameterfv(int target, int pname, FloatBuffer params) {
        report("getTexParameterfv");
    gl.glGetTexParameterfv(target, pname, params);
  }

  @Override
  public boolean isTexture(int texture) {
        report("isTexture");
    return gl.glIsTexture(texture);
  }

  @Override
  protected void activeTextureImpl(int texture) {
        report("activeTextureImpl");
    gl.glActiveTexture(texture);
  }

  @Override
  protected void bindTextureImpl(int target, int texture) {
        report("bindTextureImpl");
    gl.glBindTexture(target, texture);
  }

  ///////////////////////////////////////////////////////////

  // Shaders and Programs

  @Override
  public int createShader(int type) {
        report("createShader");
    return gl2.glCreateShader(type);
  }

  @Override
  public void shaderSource(int shader, String source) {
        report("shaderSource");
    gl2.glShaderSource(shader, 1, new String[] { source }, null, 0);
  }

  @Override
  public void compileShader(int shader) {
        report("compileShader");
    gl2.glCompileShader(shader);
  }

  @Override
  public void releaseShaderCompiler() {
        report("releaseShaderCompiler");
    gl2.glReleaseShaderCompiler();
  }

  @Override
  public void deleteShader(int shader) {
        report("deleteShader");
    gl2.glDeleteShader(shader);
  }

  @Override
  public void shaderBinary(int count, IntBuffer shaders, int binaryFormat, Buffer binary, int length) {
        report("shaderBinary");
    gl2.glShaderBinary(count, shaders, binaryFormat, binary, length);
  }

  @Override
  public int createProgram() {
        report("createProgram");
        int p = gl2.glCreateProgram();
    return p;
  }

  @Override
  public void attachShader(int program, int shader) {
        report("attachShader");
    gl2.glAttachShader(program, shader);
  }

  @Override
  public void detachShader(int program, int shader) {
        report("detachShader");
    gl2.glDetachShader(program, shader);
  }

  @Override
  public void linkProgram(int program) {
        report("linkProgram");
    gl2.glLinkProgram(program);
  }

  @Override
  public void useProgram(int program) {
        report("useProgram");
    gl2.glUseProgram(program);
  }

  @Override
  public void deleteProgram(int program) {
        report("deleteProgram");
    gl2.glDeleteProgram(program);
  }

  @Override
  public String getActiveAttrib(int program, int index, IntBuffer size, IntBuffer type) {
        report("getActiveAttrib");
    int[] tmp = {0, 0, 0};
    byte[] namebuf = new byte[1024];
    gl2.glGetActiveAttrib(program, index, 1024, tmp, 0, tmp, 1, tmp, 2, namebuf, 0);
    size.put(tmp[1]);
    type.put(tmp[2]);
    return new String(namebuf, 0, tmp[0]);
  }

  @Override
  public int getAttribLocation(int program, String name) {
        report("getAttribLocation");
    int result = gl2.glGetAttribLocation(program, name);
    String args = "program "+program+"  name "+name+"";
    return result;
  }

  @Override
  public void bindAttribLocation(int program, int index, String name) {
        report("bindAttribLocation");
    gl2.glBindAttribLocation(program, index, name);
  }

  @Override
  public int getUniformLocation(int program, String name) {
    String args = program+" "+name;
        report("getUniformLocation");
        int result = gl2.glGetUniformLocation(program, name);
    return result;
  }

  @Override
  public String getActiveUniform(int program, int index, IntBuffer size, IntBuffer type) {
        report("getActiveUniform");
    final int[] tmp = { 0, 0, 0 };
    final byte[] namebuf = new byte[1024];
    gl2.glGetActiveUniform(program, index, 1024, tmp, 0, tmp, 1, tmp, 2, namebuf, 0);
    size.put(tmp[1]);
    type.put(tmp[2]);
    return new String(namebuf, 0, tmp[0]);
  }

  @Override
  public void uniform1i(int location, int value) {
        report("uniform1i");
    gl2.glUniform1i(location, value);
  }

  @Override
  public void uniform2i(int location, int value0, int value1) {
        report("uniform2i");
    gl2.glUniform2i(location, value0, value1);
  }

  @Override
  public void uniform3i(int location, int value0, int value1, int value2) {
        report("uniform3i");
    gl2.glUniform3i(location, value0, value1, value2);
  }

  @Override
  public void uniform4i(int location, int value0, int value1, int value2, int value3) {
        report("uniform4i");
    gl2.glUniform4i(location, value0, value1, value2, value3);
  }

  @Override
  public void uniform1f(int location, float value) {
        report("uniform1f");
    gl2.glUniform1f(location, value);
  }

  @Override
  public void uniform2f(int location, float value0, float value1) {
        report("uniform2f");
    gl2.glUniform2f(location, value0, value1);
  }

  @Override
  public void uniform3f(int location, float value0, float value1, float value2) {
        report("uniform3f");
    gl2.glUniform3f(location, value0, value1, value2);
  }

  @Override
  public void uniform4f(int location, float value0, float value1, float value2, float value3) {
        report("uniform4f");
    gl2.glUniform4f(location, value0, value1, value2, value3);
  }

  @Override
  public void uniform1iv(int location, int count, IntBuffer v) {
        report("uniform1iv");
    gl2.glUniform1iv(location, count, v);
  }

  @Override
  public void uniform2iv(int location, int count, IntBuffer v) {
        report("uniform2iv");
    gl2.glUniform2iv(location, count, v);
  }

  @Override
  public void uniform3iv(int location, int count, IntBuffer v) {
        report("uniform3iv");
    gl2.glUniform3iv(location, count, v);
  }

  @Override
  public void uniform4iv(int location, int count, IntBuffer v) {
        report("uniform4iv");
    gl2.glUniform4iv(location, count, v);
  }

  @Override
  public void uniform1fv(int location, int count, FloatBuffer v) {
        report("uniform1fv");
    gl2.glUniform1fv(location, count, v);
  }

  @Override
  public void uniform2fv(int location, int count, FloatBuffer v) {
        report("uniform2fv");
    gl2.glUniform2fv(location, count, v);
  }

  @Override
  public void uniform3fv(int location, int count, FloatBuffer v) {
        report("uniform3fv");
    gl2.glUniform3fv(location, count, v);
  }

  @Override
  public void uniform4fv(int location, int count, FloatBuffer v) {
        report("uniform4fv");
    gl2.glUniform4fv(location, count, v);
  }

  @Override
  public void uniformMatrix2fv(int location, int count, boolean transpose, FloatBuffer mat) {
        report("uniformMatrix2fv");
    gl2.glUniformMatrix2fv(location, count, transpose, mat);
  }

  @Override
  public void uniformMatrix3fv(int location, int count, boolean transpose, FloatBuffer mat) {
        report("uniformMatrix3fv");
    gl2.glUniformMatrix3fv(location, count, transpose, mat);
  }

  @Override
  public void uniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer mat) {
        report("uniformMatrix4fv");
    gl2.glUniformMatrix4fv(location, count, transpose, mat);
  }

  @Override
  public void validateProgram(int program) {
        report("validateProgram");
    gl2.glValidateProgram(program);
  }

  @Override
  public boolean isShader(int shader) {
        report("isShader");
    return gl2.glIsShader(shader);
  }

  @Override
  public void getShaderiv(int shader, int pname, IntBuffer params) {
    String arg = shader+" "+pname;
        report("getShaderiv");
    gl2.glGetShaderiv(shader, pname, params);
  }

  @Override
  public void getAttachedShaders(int program, int maxCount, IntBuffer count, IntBuffer shaders) {
        report("getAttachedShaders");
    gl2.glGetAttachedShaders(program, maxCount, count, shaders);
  }

  @Override
  public String getShaderInfoLog(int shader) {
        report("getShaderInfoLog");
    int[] val = { 0 };
    gl2.glGetShaderiv(shader, GL2ES2.GL_INFO_LOG_LENGTH, val, 0);
    int length = val[0];

    byte[] log = new byte[length];
    gl2.glGetShaderInfoLog(shader, length, val, 0, log, 0);
    return new String(log);
  }

  @Override
  public String getShaderSource(int shader) {
        report("getShaderSource");
    int[] len = {0};
    byte[] buf = new byte[1024];
    gl2.glGetShaderSource(shader, 1024, len, 0, buf, 0);
    return new String(buf, 0, len[0]);
  }

  @Override
  public void getShaderPrecisionFormat(int shaderType, int precisionType, IntBuffer range, IntBuffer precision) {
        report("getShaderPrecisionFormat");
    gl2.glGetShaderPrecisionFormat(shaderType, precisionType, range, precision);
  }

  @Override
  public void getVertexAttribfv(int index, int pname, FloatBuffer params) {
        report("getVertexAttribfv");
    gl2.glGetVertexAttribfv(index, pname, params);
  }

  @Override
  public void getVertexAttribiv(int index, int pname, IntBuffer params) {
        report("getVertexAttribiv");
    gl2.glGetVertexAttribiv(index, pname, params);
  }

  @Override
  public void getVertexAttribPointerv(int index, int pname, ByteBuffer data) {
        report("getVertexAttribPointerv");
    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glGetVertexAttribPointerv()"));
  }

  @Override
  public void getUniformfv(int program, int location, FloatBuffer params) {
        report("getUniformfv");
    gl2.glGetUniformfv(program, location, params);
  }

  @Override
  public void getUniformiv(int program, int location, IntBuffer params) {
        report("getUniformiv");
    gl2.glGetUniformiv(program, location, params);
  }

  @Override
  public boolean isProgram(int program) {
        report("isProgram");
    return gl2.glIsProgram(program);
  }

  @Override
  public void getProgramiv(int program, int pname, IntBuffer params) {
        report("getProgramiv");
    String args = program+" "+pname;
    gl2.glGetProgramiv(program, pname, params);
  }

  @Override
  public String getProgramInfoLog(int program) {
        report("getProgramInfoLog");
    int[] val = { 0 };
    gl2.glGetProgramiv(program, GL2ES2.GL_INFO_LOG_LENGTH, val, 0);
    int length = val[0];

    if (0 < length) {
      byte[] log = new byte[length];
      gl2.glGetProgramInfoLog(program, length, val, 0, log, 0);
      return new String(log);
    } else {
      return "Unknown error";
    }
  }

  ///////////////////////////////////////////////////////////

  // Per-Fragment Operations

  @Override
  public void scissor(int x, int y, int w, int h) {
        report("scissor");
    float scale = getPixelScale();
    gl.glScissor((int)scale * x, (int)(scale * y), (int)(scale * w), (int)(scale * h));
//    gl.glScissor(x, y, w, h);
  }

  @Override
  public void sampleCoverage(float value, boolean invert) {
        report("sampleCoverage");
    gl2.glSampleCoverage(value, invert);
  }

  @Override
  public void stencilFunc(int func, int ref, int mask) {
        report("stencilFunc");
    gl2.glStencilFunc(func, ref, mask);
  }

  @Override
  public void stencilFuncSeparate(int face, int func, int ref, int mask) {
        report("stencilFuncSeparate");
    gl2.glStencilFuncSeparate(face, func, ref, mask);
  }

  @Override
  public void stencilOp(int sfail, int dpfail, int dppass) {
        report("stencilOp");
    gl2.glStencilOp(sfail, dpfail, dppass);
  }

  @Override
  public void stencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        report("stencilOpSeparate");
    gl2.glStencilOpSeparate(face, sfail, dpfail, dppass);
  }

  @Override
  public void depthFunc(int func) {
        report("depthFunc");
    gl.glDepthFunc(func);
  }

  @Override
  public void blendEquation(int mode) {
        report("blendEquation");
    gl.glBlendEquation(mode);
  }

  @Override
  public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        report("blendEquationSeparate");
    gl.glBlendEquationSeparate(modeRGB, modeAlpha);
  }

  @Override
  public void blendFunc(int src, int dst) {
        report("blendFunc");
    gl.glBlendFunc(src, dst);
  }

  @Override
  public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        report("blendFuncSeparate");
    gl.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
  }

  @Override
  public void blendColor(float red, float green, float blue, float alpha) {
        report("blendColor");
    gl2.glBlendColor(red, green, blue, alpha);
  }

  ///////////////////////////////////////////////////////////

  // Whole Framebuffer Operations

  @Override
  public void colorMask(boolean r, boolean g, boolean b, boolean a) {
        report("colorMask");
    gl.glColorMask(r, g, b, a);
  }

  @Override
  public void depthMask(boolean mask) {
        report("depthMask");
    gl.glDepthMask(mask);
  }

  @Override
  public void stencilMask(int mask) {
        report("stencilMask");
    gl.glStencilMask(mask);
  }

  @Override
  public void stencilMaskSeparate(int face, int mask) {
        report("stencilMaskSeparate");
    gl2.glStencilMaskSeparate(face, mask);
  }

  @Override
  public void clearColor(float r, float g, float b, float a) {
        report("clearColor");
    gl.glClearColor(r, g, b, a);
  }

  @Override
  public void clearDepth(float d) {
        report("clearDepth");
    gl.glClearDepth(d);
  }

  @Override
  public void clearStencil(int s) {
        report("clearStencil");
    gl.glClearStencil(s);
  }

  @Override
  public void clear(int buf) {
        report("clear");
    gl.glClear(buf);
  }

  ///////////////////////////////////////////////////////////

  // Framebuffers Objects

  @Override
  protected void bindFramebufferImpl(int target, int framebuffer) {
        report("bindFramebufferImpl");
    gl.glBindFramebuffer(target, framebuffer);
  }

  @Override
  public void deleteFramebuffers(int n, IntBuffer framebuffers) {
        report("deleteFramebuffers");
    gl.glDeleteFramebuffers(n, framebuffers);
  }

  @Override
  public void genFramebuffers(int n, IntBuffer framebuffers) {
        report("genFramebuffers");
    gl.glGenFramebuffers(n, framebuffers);
  }

  @Override
  public void bindRenderbuffer(int target, int renderbuffer) {
        report("bindRenderbuffer");
    gl.glBindRenderbuffer(target, renderbuffer);
  }

  @Override
  public void deleteRenderbuffers(int n, IntBuffer renderbuffers) {
        report("deleteRenderbuffers");
    gl.glDeleteRenderbuffers(n, renderbuffers);
  }

  @Override
  public void genRenderbuffers(int n, IntBuffer renderbuffers) {
        report("genRenderbuffers");
    gl.glGenRenderbuffers(n, renderbuffers);
  }

  @Override
  public void renderbufferStorage(int target, int internalFormat, int width, int height) {
        report("renderbufferStorage");
    gl.glRenderbufferStorage(target, internalFormat, width, height);
  }

  @Override
  public void framebufferRenderbuffer(int target, int attachment, int rbt, int renderbuffer) {
        report("framebufferRenderbuffer");
    gl.glFramebufferRenderbuffer(target, attachment, rbt, renderbuffer);
  }

  @Override
  public void framebufferTexture2D(int target, int attachment, int texTarget, int texture, int level) {
        report("framebufferTexture2D");
    gl.glFramebufferTexture2D(target, attachment, texTarget, texture, level);
  }

  @Override
  public int checkFramebufferStatus(int target) {
        report("checkFramebufferStatus");
    return gl.glCheckFramebufferStatus(target);
  }

  @Override
  public boolean isFramebuffer(int framebuffer) {
        report("isFramebuffer");
    return gl2.glIsFramebuffer(framebuffer);
  }

  @Override
  public void getFramebufferAttachmentParameteriv(int target, int attachment, int name, IntBuffer params) {
        report("getFramebufferAttachmentParameteriv");
    gl2.glGetFramebufferAttachmentParameteriv(target, attachment, name, params);
  }

  @Override
  public boolean isRenderbuffer(int renderbuffer) {
        report("isRenderbuffer");
    return gl2.glIsRenderbuffer(renderbuffer);
  }

  @Override
  public void getRenderbufferParameteriv(int target, int name, IntBuffer params) {
        report("getRenderbufferParameteriv");
    gl2.glGetRenderbufferParameteriv(target, name, params);
  }

  @Override
  public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        report("blitFramebuffer");
    if (gl2x != null) {
      gl2x.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    } else if (gl3 != null) {
      gl3.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    } else if (gl3es3 != null) {
      gl3es3.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    } else {
      throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glBlitFramebuffer()"));
    }
  }

  @Override
  public void renderbufferStorageMultisample(int target, int samples, int format, int width, int height) {
        report("renderbufferStorageMultisample");
    if (gl2x != null) {
      gl2x.glRenderbufferStorageMultisample(target, samples, format, width, height);
    } else if (gl3 != null) {
      gl3.glRenderbufferStorageMultisample(target, samples, format, width, height);
    } else if (gl3es3 != null) {
      gl3es3.glRenderbufferStorageMultisample(target, samples, format, width, height);
    } else {
      throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glRenderbufferStorageMultisample()"));
    }
  }

  @Override
  public void readBuffer(int buf) {
        report("readBuffer");
    if (gl2x != null) {
      gl2x.glReadBuffer(buf);
    } else if (gl3 != null) {
      gl3.glReadBuffer(buf);
    } else if (gl3es3 != null) {
      gl3es3.glReadBuffer(buf);
    } else {
      throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glReadBuffer()"));
    }
  }

  @Override
  public void drawBuffer(int buf) {
        report("drawBuffer");
    if (gl2x != null) {
      gl2x.glDrawBuffer(buf);
    } else if (gl3 != null) {
      gl3.glDrawBuffer(buf);
    } else if (gl3es3 != null) {
      IntBuffer intBuffer = IntBuffer.allocate(1);
      intBuffer.put(buf);
      intBuffer.rewind();
      gl3es3.glDrawBuffers(1, intBuffer);
    } else {
      throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glDrawBuffer()"));
    }
  }
}
