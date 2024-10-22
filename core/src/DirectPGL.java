import java.nio.FloatBuffer;

import processing.core.*;
import processing.opengl.*;
import processing.data.*;
import processing.event.*;

import java.nio.*;

import java.util.HashMap;



public class DirectPGL extends PApplet {

PShader sh1;
PShader sh2;

float[] attribs;

FloatBuffer attribBuffer;

int attribVboId;

final static int VERT_CMP_COUNT = 4; // vertex component count (x, y, z, w) -> 4
final static int CLR_CMP_COUNT = 4;  // color component count (r, g, b, a) -> 4


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

  @Override
  public void setup() {
    System.out.println(sketchPath());
    try {
      surface.setLocation(100, 100);

      // Loads a shader to render geometry w/out
      // textures and lights.
      sh1 = loadShader("C:\\mydata\\temp\\LowLevelGLVboInterleaved\\data\\frag1.glsl", "C:\\mydata\\temp\\LowLevelGLVboInterleaved\\data\\vert1.glsl");
//      sh2 = loadShader("C:\\mydata\\temp\\LowLevelGLVboInterleaved\\data\\frag2.glsl", "C:\\mydata\\temp\\LowLevelGLVboInterleaved\\data\\vert2.glsl");

      try {
        sh1.init();
//        sh2.init();
      }
      catch (RuntimeException e) {
        println(e.getMessage());
        exit();
      }


      attribs = new float[24];
      attribBuffer = allocateDirectFloatBuffer(24);

      PGL pgl = beginPGL();

      IntBuffer intBuffer = IntBuffer.allocate(1);
      pgl.genBuffers(1, intBuffer);

      attribVboId = intBuffer.get(0);

      updateGeometry();
      pgl.bindBuffer(PGL.ARRAY_BUFFER, attribVboId);
      // fill VBO with data
      pgl.bufferData(PGL.ARRAY_BUFFER, Float.BYTES * attribs.length, attribBuffer, PGL.DYNAMIC_DRAW);
      // associate currently bound VBO with "vertex" shader attribute

      pgl.bindBuffer(PGL.ARRAY_BUFFER, 0);

      endPGL();
    }
    catch (RuntimeException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }



  @Override

public void draw() {

  PGL pgl = beginPGL();

  background(0);

  // The geometric transformations will be automatically passed
  // to the shader.
  pushMatrix();
  rotate(frameCount * 0.01f, width, height, 0);

  sh1.bind();

  // get "vertex" attribute location in the shader
  int vertLoc = pgl.getAttribLocation(sh1.glProgram, "vertex");
  // enable array for "vertex" attribute
  pgl.enableVertexAttribArray(vertLoc);

  // get "color" attribute location in the shader
  int colorLoc = pgl.getAttribLocation(sh1.glProgram, "color");
  // enable array for "color" attribute
  pgl.enableVertexAttribArray(colorLoc);


  /*
    BUFFER LAYOUT from updateGeometry()

    xyzwrgbaxyzwrgbaxyzwrgba...

    |v1       |v2       |v3       |...
    |0   |4   |8   |12  |16  |20  |...
    |xyzw|rgba|xyzw|rgba|xyzw|rgba|...


    stride (values per vertex) is 8 floats
    vertex offset is 0 floats (starts at the beginning of each line)
    color offset is 4 floats (starts after vertex coords)

       |0   |4   |8
    v1 |xyzw|rgba|
    v2 |xyzw|rgba|
    v3 |xyzw|rgba|
       |...
   */
  int stride       = (VERT_CMP_COUNT + CLR_CMP_COUNT) * Float.BYTES;
  int vertexOffset =                                0 * Float.BYTES;
  int colorOffset  =                   VERT_CMP_COUNT * Float.BYTES;

  // bind VBO
  pgl.bindBuffer(PGL.ARRAY_BUFFER, attribVboId);
  // associate currently bound VBO with "vertex" shader attribute
  pgl.vertexAttribPointer(vertLoc, VERT_CMP_COUNT, PGL.FLOAT, false, stride, vertexOffset);
  // associate currently bound VBO with "color" shader attribute
  pgl.vertexAttribPointer(colorLoc, CLR_CMP_COUNT, PGL.FLOAT, false, stride, colorOffset);
  // unbind VBO
  pgl.bindBuffer(PGL.ARRAY_BUFFER, 0);

  int u_brightness = pgl.getUniformLocation(sh1.glProgram, "brightness");
  println("u_brightness: "+u_brightness);

  pgl.uniform1f(u_brightness, sin(frameCount*0.1f)*1f+2f);


  pgl.drawArrays(PGL.TRIANGLES, 0, 3);


  // disable arrays for attributes before unbinding the shader
  pgl.disableVertexAttribArray(vertLoc);
  pgl.disableVertexAttribArray(colorLoc);

  sh1.unbind();

  popMatrix();


//  pushMatrix();
//
//  rotate(frameCount * 0.01f + HALF_PI, width, height, 0);



///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // NUMBER 2
//  sh2.bind();
//
//  // get "vertex" attribute location in the shader
//  vertLoc = pgl.getAttribLocation(sh1.glProgram, "vertex");
//  // enable array for "vertex" attribute
//  pgl.enableVertexAttribArray(vertLoc);
//
//  // get "color" attribute location in the shader
//  colorLoc = pgl.getAttribLocation(sh1.glProgram, "color");
//  // enable array for "color" attribute
//  pgl.enableVertexAttribArray(colorLoc);
//
//  stride       = (VERT_CMP_COUNT + CLR_CMP_COUNT) * Float.BYTES;
//  vertexOffset =                                0 * Float.BYTES;
//  colorOffset  =                   VERT_CMP_COUNT * Float.BYTES;
//
//  // bind VBO
//  pgl.bindBuffer(PGL.ARRAY_BUFFER, attribVboId);
//  // fill VBO with data
//  pgl.bufferData(PGL.ARRAY_BUFFER, Float.BYTES * attribs.length, attribBuffer, PGL.DYNAMIC_DRAW);
//  // associate currently bound VBO with "vertex" shader attribute
//  pgl.vertexAttribPointer(vertLoc, VERT_CMP_COUNT, PGL.FLOAT, false, stride, vertexOffset);
//  // associate currently bound VBO with "color" shader attribute
//  pgl.vertexAttribPointer(colorLoc, CLR_CMP_COUNT, PGL.FLOAT, false, stride, colorOffset);
//  // unbind VBO
//  pgl.bindBuffer(PGL.ARRAY_BUFFER, 0);
//
//  final int u_resolution = pgl.getUniformLocation(sh2.glProgram, "u_resolution");
//  final int u_x = pgl.getUniformLocation(sh2.glProgram, "u_x");
//  final int u_y = pgl.getUniformLocation(sh2.glProgram, "u_y");
//  println("u_resolution: "+u_resolution);
//  println("u_x: "+u_x);
//  println("u_y: "+u_y);
//
//  pgl.uniform2f(u_resolution, (float)(width), (float)(height));
//
//  float speed = 1.f;
//  pgl.uniform1f(u_x, frameCount*0.1f*speed);
//  pgl.uniform1f(u_y, frameCount*0.1f*speed);
//
//
//  pgl.drawArrays(PGL.TRIANGLES, 0, 3);
//
//
//  // disable arrays for attributes before unbinding the shader
//  pgl.disableVertexAttribArray(vertLoc);
//  pgl.disableVertexAttribArray(colorLoc);
//
//  sh2.unbind();
//
//  popMatrix();

  endPGL();
}

// Triggers a crash when closing the output window using the close button
//public void dispose() {
//  PGL pgl = beginPGL();

//  IntBuffer intBuffer = IntBuffer.allocate(1);
//  intBuffer.put(attribVboId);
//  intBuffer.rewind();
//  pgl.deleteBuffers(1, intBuffer);

//  endPGL();
//}

  void updateGeometry() {
    // Vertex 1
    attribs[0] = 0;
    attribs[1] = 0;
    attribs[2] = 0;
    attribs[3] = 1;

    // Color 1
    attribs[4] = 1;
    attribs[5] = 0;
    attribs[6] = 0;
    attribs[7] = 1;

    // Vertex 2
    attribs[8] = width/2;
    attribs[9] = height;
    attribs[10] = 0;
    attribs[11] = 1;

    // Color 2
    attribs[12] = 0;
    attribs[13] = 1;
    attribs[14] = 0;
    attribs[15] = 1;

    // Vertex 3
    attribs[16] = width;
    attribs[17] = 0;
    attribs[18] = 0;
    attribs[19] = 1;

    // Color 3
    attribs[20] = 0;
    attribs[21] = 0;
    attribs[22] = 1;
    attribs[23] = 1;

    attribBuffer.rewind();
    attribBuffer.put(attribs);
    attribBuffer.rewind();
  }

  FloatBuffer allocateDirectFloatBuffer(int n) {
    return ByteBuffer.allocateDirect(n * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
  }

  public static void main(String[] args) {
    String[] appletArgs = new String[] { "DirectPGL" };
    PApplet.main(appletArgs);
  }
}
