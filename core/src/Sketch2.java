import processing.core.*;
import processing.data.*;
import processing.event.*;
import processing.opengl.*;

import java.nio.*;

//TODO discoveries:
//- vertexAttribPointer thing (you know about it)
//- position has id of 2 and color has id of 2? That seems like the wrong order.
//Double check these are the right positions

public class Sketch2 extends PApplet {

  @Override
  public void settings() {
    try {
      size(512, 512, PV2D);
    }
    catch (RuntimeException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }



  FloatBuffer attribBuffer = null;


  FloatBuffer allocateDirectFloatBuffer(int n) {
    return ByteBuffer.allocateDirect(n * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
  }

  void updateGeometry() {

    float[] attribs = new float[24];
    attribBuffer = allocateDirectFloatBuffer(24);

    // Vertex 1
    attribs[0] = -1f;
    attribs[1] = -1f;
    attribs[2] = 0f;
    attribs[3] = 1f;

    // Color 1
    attribs[4] = 1f;
    attribs[5] = 0f;
    attribs[6] = 0f;
    attribs[7] = 1f;

    // Vertex 2
    attribs[8] = 1;
    attribs[9] = -1;
    attribs[10] = 0f;
    attribs[11] = 1f;

    // Color 2
    attribs[12] = 0f;
    attribs[13] = 1f;
    attribs[14] = 0f;
    attribs[15] = 1f;

    // Vertex 3
    attribs[16] = 0f;
    attribs[17] = 1f;
    attribs[18] = 0f;
    attribs[19] = 1f;

    // Color 3
    attribs[20] = 0f;
    attribs[21] = 0f;
    attribs[22] = 1f;
    attribs[23] = 1f;

    attribBuffer.rewind();
    attribBuffer.put(attribs);
    attribBuffer.rewind();
  }

  int program = -1;

  String vertShaderSource = """
attribute vec4 position;
attribute vec4 color;

varying vec4 vertColor;

void main() {
  gl_Position = position;

  vertColor = color;
}

      """;

  String fragShaderSource = """
varying vec4 vertColor;

void main() {
  gl_FragColor = vertColor;
}

      """;

  int vbo = -1;

  int posAttrib = -1;
  int colAttrib = -1;

  @Override
  public void setup() {
    try {
      surface.setLocation(100, 100);

      PGL pgl = beginPGL();

      int vertShader = pgl.createShader(PGL.VERTEX_SHADER);
      int fragShader = pgl.createShader(PGL.FRAGMENT_SHADER);

      pgl.shaderSource(vertShader, vertShaderSource);
      pgl.shaderSource(fragShader, fragShaderSource);

      pgl.compileShader(vertShader);
      pgl.compileShader(fragShader);

      program = pgl.createProgram();
      pgl.attachShader(program, vertShader);
      pgl.attachShader(program, fragShader);

      pgl.linkProgram(program);


      IntBuffer out = IntBuffer.allocate(1);
      pgl.genBuffers(1, out);
      vbo = out.get(0);

      updateGeometry();
      pgl.bindBuffer(PGL.ARRAY_BUFFER, vbo);
      pgl.bufferData(PGL.ARRAY_BUFFER, 24*4, attribBuffer, PGL.DYNAMIC_DRAW);
      pgl.bindBuffer(PGL.ARRAY_BUFFER, 0);

      posAttrib = pgl.getAttribLocation(program, "position");
      colAttrib = pgl.getAttribLocation(program, "color");


      endPGL();
    }
    catch (RuntimeException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }



  @Override
  public void draw() {
    try {
      background(200);


      PGL pgl = beginPGL();

      pgl.useProgram(program);

      pgl.bindBuffer(PGL.ARRAY_BUFFER, vbo);

      // Remember to change 4*4 to 4.
      pgl.vertexAttribPointer(posAttrib, 4*4, PGL.FLOAT, false, 4*4*2, 0);
      pgl.vertexAttribPointer(colAttrib, 4*4, PGL.FLOAT, false, 4*4*2, 4*4);

      pgl.enableVertexAttribArray(posAttrib);
      pgl.enableVertexAttribArray(colAttrib);
      pgl.bindBuffer(PGL.ARRAY_BUFFER, 0);

      pgl.drawArrays(PGL.TRIANGLES, 0, 3);

      pgl.disableVertexAttribArray(posAttrib);
      pgl.disableVertexAttribArray(colAttrib);


      endPGL();
    }
    catch (RuntimeException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static void main(String[] args) {
    String[] appletArgs = new String[] { "Sketch2" };
    PApplet.main(appletArgs);
  }
}
