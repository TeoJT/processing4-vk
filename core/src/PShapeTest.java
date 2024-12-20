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

public class PShapeTest extends PApplet {

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
        particle = createShape();
        particle.beginShape(QUAD);
        particle.stroke(255);
        particle.strokeWeight(3f);
//        particle.noStroke();
        float wi = 50f;
        particle.fill(50f, 50f, 255f);
        particle.vertex(0f, 0f);
        particle.vertex(wi, 0f);
        particle.vertex(wi, wi);
        particle.vertex(0f, wi);
        particle.endShape();
      }
      catch (RuntimeException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }



    @Override
    public void draw() {
      try {
        background(255);

//        Util.beginTmr();
        for (int i = 0; i < 10000; i++) {
//          selectNode(i%8);
//          Util.beginTmr();
//          println("--------------\n \n\n\n\n");
          pushMatrix();
          translate(random(0, width), random(0, height));
          shape(particle);
//          for (int j = 0; j < 100000; j++) {}
          popMatrix();

//
//          Util.endTmr("One cycle");
        }
//        Util.endTmr("One frame");

//        printThreadNodeReport();
        println("fps: "+frameRate);
      }
      catch (RuntimeException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    public static void main(String[] args) {
      String[] appletArgs = new String[] { "PShapeTest" };
      PApplet.main(appletArgs);
    }
}
