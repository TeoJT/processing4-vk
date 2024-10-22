import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import processing.core.*;
import processing.opengl.*;
import processing.data.*;
import processing.event.*;

import org.joml.Math;
import org.joml.Matrix4f;

import processing.GL2VK.Util;
import processing.core.PApplet;




//uniform mat4 transformMatrix;
//
//attribute vec4 position;
//attribute vec4 color;
//
//varying vec4 vertColor;
//
//
//vec2 positions[3] = vec2[](
//  vec2(-1.0, 0.0),
//  vec2(1.0, 0.0),
//  vec2(0.0, 1.0)
//);
//
//void main() {
//int index = gl_VertexIndex;
//if (index > 2) {
//  index = 2;
//}
//gl_Position = vec4(positions[index], 0.0, 1.0);
//}



//#ifdef GL_ES
//precision mediump float;
//precision mediump int;
//#endif
//
//varying vec4 vertColor;
//
//void main() {
//  gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
//}









public class CoolIndicies extends PApplet {


  @Override
  public void settings() {
    try {
      size(512, 512, P3D);
    }
    catch (RuntimeException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void createIndicesSquareProcessingShader(ByteBuffer vertexBuffer, ByteBuffer colorBuffer, ByteBuffer indexBuffer) {

//      vertexBuffer.order(ByteOrder.LITTLE_ENDIAN);
//      colorBuffer.order(ByteOrder.LITTLE_ENDIAN);
//      indexBuffer.order(ByteOrder.LITTLE_ENDIAN);

//        {{-0.5f, -0.5f}, {1.0f, 0.0f, 0.0f}},
//        {{0.5f, -0.5f}, {0.0f, 1.0f, 0.0f}},
//        {{0.5f, 0.5f}, {0.0f, 0.0f, 1.0f}},
//        {{-0.5f, 0.5f}, {1.0f, 1.0f, 1.0f}}
      vertexBuffer.putFloat(-0.5f);
      vertexBuffer.putFloat(-0.5f);
      vertexBuffer.putFloat(0f);
      vertexBuffer.putFloat(1f);

      colorBuffer.putFloat(1f);
      colorBuffer.putFloat(0f);
      colorBuffer.putFloat(0f);
      colorBuffer.putFloat(1f);

      vertexBuffer.putFloat(0.5f);
      vertexBuffer.putFloat(-0.5f);
      vertexBuffer.putFloat(0f);
      vertexBuffer.putFloat(1f);

      colorBuffer.putFloat(0f);
      colorBuffer.putFloat(1f);
      colorBuffer.putFloat(0f);
      colorBuffer.putFloat(1f);

      vertexBuffer.putFloat(0.5f);
      vertexBuffer.putFloat(0.5f);
      vertexBuffer.putFloat(0f);
      vertexBuffer.putFloat(1f);

      colorBuffer.putFloat(0f);
      colorBuffer.putFloat(0f);
      colorBuffer.putFloat(1f);
      colorBuffer.putFloat(1f);

      vertexBuffer.putFloat(-0.5f);
      vertexBuffer.putFloat(0.5f);
      vertexBuffer.putFloat(0f);
      vertexBuffer.putFloat(1f);

      colorBuffer.putFloat(1f);
      colorBuffer.putFloat(0f);
      colorBuffer.putFloat(1f);
      colorBuffer.putFloat(1f);

      indexBuffer.putShort((short)0);
      indexBuffer.putShort((short)1);
      indexBuffer.putShort((short)2);
      indexBuffer.putShort((short)2);
      indexBuffer.putShort((short)3);
      indexBuffer.putShort((short)0);

      vertexBuffer.rewind();
      colorBuffer.rewind();
      indexBuffer.rewind();
  }


  int transformMatrix = -1;
  int glVertBuff = -1;
  int glColBuff = -1;
  int glIndexBuff = -1;

  int program = -1;
  int vertShader = -1;
  int fragShader = -1;
  int position = -1;
  int color = -1;

  @Override
  public void setup() {
    try {

    PGL pgl = beginPGL();
  // Create the data
    ByteBuffer vertexBuffer = ByteBuffer.allocate(Float.BYTES * 6 * 4);
    ByteBuffer colorBuffer = ByteBuffer.allocate(Float.BYTES * 6 * 4);
    ByteBuffer indexBuffer = ByteBuffer.allocate(Short.BYTES * 6);
    createIndicesSquareProcessingShader(vertexBuffer, colorBuffer, indexBuffer);

    // Gen buffers
    IntBuffer out = IntBuffer.allocate(3);
    pgl.genBuffers(3, out);
    glVertBuff = out.get(0);
    glColBuff = out.get(1);
    glIndexBuff = out.get(2);


    // Create our gpu program
    program = pgl.createProgram();
    vertShader = pgl.createShader(PGL.VERTEX_SHADER);
    fragShader = pgl.createShader(PGL.FRAGMENT_SHADER);

    // Shader source
    pgl.shaderSource(vertShader, Util.readFile("src/processing/opengl/shaders/ColorVert.glsl"));
    pgl.shaderSource(fragShader, Util.readFile("src/processing/opengl/shaders/ColorFrag.glsl"));
    // Compile the shaders
    pgl.compileShader(vertShader);
    pgl.compileShader(fragShader);
    // Check shaders
    IntBuffer compileStatus = IntBuffer.allocate(1);
    pgl.getShaderiv(vertShader, PGL.COMPILE_STATUS, compileStatus);
    if (compileStatus.get(0) == PGL.FALSE) {
      System.out.println(pgl.getShaderInfoLog(vertShader));
      System.exit(1);
    }
    pgl.getShaderiv(fragShader, PGL.COMPILE_STATUS, compileStatus);
    if (compileStatus.get(0) == PGL.FALSE) {
      System.out.println(pgl.getShaderInfoLog(fragShader));
      System.exit(1);
    }
    // Attach the shaders
    pgl.attachShader(program, vertShader);
    pgl.attachShader(program, fragShader);
    // Don't need em anymore
    pgl.deleteShader(vertShader);
    pgl.deleteShader(fragShader);

    pgl.linkProgram(program);



  // Setup up attribs
  position = pgl.getAttribLocation(program, "position");
  color = pgl.getAttribLocation(program, "color");

  pgl.bindBuffer(PGL.ARRAY_BUFFER, glVertBuff);
  pgl.bufferData(PGL.ARRAY_BUFFER, vertexBuffer.capacity(), vertexBuffer, PGL.DYNAMIC_DRAW);

  pgl.bindBuffer(PGL.ARRAY_BUFFER, glColBuff);
  pgl.bufferData(PGL.ARRAY_BUFFER, colorBuffer.capacity(), colorBuffer, PGL.DYNAMIC_DRAW);

  pgl.bindBuffer(PGL.ELEMENT_ARRAY_BUFFER, glIndexBuff);
  pgl.bufferData(PGL.ELEMENT_ARRAY_BUFFER, indexBuffer.capacity(), indexBuffer, PGL.DYNAMIC_DRAW);

  pgl.useProgram(program);

  transformMatrix = pgl.getUniformLocation(program, "transformMatrix");
  if (transformMatrix == -1) {
    System.out.println("Missing transformMatrix!");
    System.exit(1);
  }

//  System.out.println(transformMatrix);
//  System.out.println( glVertBuff );
//  System.out.println( glColBuff);
//  System.out.println( glIndexBuff );
//
//  System.out.println( program );
//  System.out.println( vertShader);
//  System.out.println( fragShader );
//  System.out.println( position );
//  System.out.println( color );
//  System.out.println();

  endPGL();

    }
    catch (RuntimeException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  boolean multithreaded = true;
  int threadIndex = 0;
  float qtime = 0f;
  Matrix4f transform = new Matrix4f();


  @Override
  public void draw() {
    try {

      background(0);

      PGL pgl = beginPGL();

        transform.identity();
//        transform.rotateZ((qtime * Math.toRadians(90)));
//        transform.lookAt(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
//        transform.perspective((float) Math.toRadians(45),
//                    (float)1200 / (float)00, 0.1f, 10.0f);
//        transform.m11(transform.m11() * -1);

        qtime += 0.02f;

        pgl.useProgram(program);

        pgl.bindBuffer(PGL.ARRAY_BUFFER, glVertBuff);
        pgl.vertexAttribPointer(position, 4, PGL.FLOAT, false, 4*4, 0);
        pgl.enableVertexAttribArray(position);

        pgl.bindBuffer(PGL.ARRAY_BUFFER, glColBuff);
        pgl.vertexAttribPointer(color, 4, PGL.FLOAT, false, 4*4, 0);
        pgl.enableVertexAttribArray(color);


        FloatBuffer buff = ByteBuffer.allocateDirect(64).asFloatBuffer();
        transform.get(buff);

        buff.rewind();
//        for (int y = 0; y < 4; y+=1) {
//          for (int x = 0; x < 4; x+=1) {
//            print(buff.get()+" ");
//          }
//          println();
//        }
//        buff.rewind();
//        println();
//        println();

        pgl.uniformMatrix4fv(transformMatrix, 1, false, buff);

        pgl.bindBuffer(PGL.ELEMENT_ARRAY_BUFFER, glIndexBuff);

        pgl.drawElements(PGL.TRIANGLE_STRIP, 6, PGL.UNSIGNED_SHORT, 0);

        pgl.disableVertexAttribArray(position);
        pgl.disableVertexAttribArray(color);

        endPGL();

    }
    catch (RuntimeException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }



  public static void main(String[] args) {
    String[] appletArgs = new String[] { "CoolIndicies" };
    PApplet.main(appletArgs);
  }
}
