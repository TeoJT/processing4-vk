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

        println("--- CREATE PSHAPE ---\n\n\n\n");
        particle = createShape();
        particle.beginShape(QUAD);
        particle.stroke(255);
        particle.strokeWeight(5f);
//        particle.noStroke();
        particle.fill(50, 50, 150);
        particle.vertex(0f, 0f);
        particle.vertex(50f, 0f);
        particle.vertex(50f, 50f);
        particle.vertex(0f, 50f);
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
        background(200);
        shape(particle);
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
