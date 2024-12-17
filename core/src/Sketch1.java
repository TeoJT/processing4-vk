import processing.GL2VK.Util;
import processing.core.*;
import processing.data.*;
import processing.event.*;
import processing.opengl.*;

import java.nio.*;

import java.util.HashMap;

import java.util.ArrayList;
import java.io.File;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class Sketch1 extends PApplet {

  PShape particle = null;

    @Override
    public void settings() {
      try {
        size(1024, 1024, PV2D);
      }
      catch (RuntimeException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    @Override
    public void setup() {
      try {
        surface.setLocation(100, 100);

//        particle = createShape();
//        particle.beginShape(QUAD);
//        particle.stroke(255);
//        particle.strokeWeight(5f);
//        particle.fill(50, 50, 150);
//        particle.vertex(0f, 0f);
//        particle.vertex(50f, 0f);
//        particle.vertex(50f, 50f);
//        particle.vertex(0f, 50f);
//        particle.endShape();
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
        bufferMultithreaded(false);

//        println();

        noStroke();
        int MAX = 256;
        colorMode(HSB, MAX, 255, 255);

        float time = ((float)frameCount)*0.02f;

        for (int i = 0; i < 1000; i++) {
          selectNode(0);
          fill((frameCount+i*289)%MAX, 255, 255);
          ellipseMode(CORNER);
          ellipse(sin((float)i+time)*noise(i*23)*700f+width/2,
                  cos((float)i+time)*noise(i*23)*700f+height/2, 64f, 64f);
        }
        colorMode(RGB, 256);
        flush();

        selectNode(0);

        // TODO: Uniform1i
        strokeCap(ROUND);
        fill(255, 0, 0);
        strokeWeight(sin(((float)frameCount)*0.1f)*30f+30f);
        stroke(255);
        rect(mouseX, mouseY, 256f, 256f);
        bufferMultithreaded(false);
        flush();
//        background(200,0,0);

//        printThreadNodeReport();
        println("fps: "+frameRate);


      }
      catch (RuntimeException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    public static void main(String[] args) {
      String[] appletArgs = new String[] { "Sketch1" };
      PApplet.main(appletArgs);
    }
}
