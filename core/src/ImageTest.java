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

public class ImageTest extends PApplet {

  PShape particle = null;
    PImage img;

    @Override
    public void settings() {
      try {
        size(1024, 1024, P2D);
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
        img = loadImage("C:/mydata/temp/hh.jpg");
      }
      catch (RuntimeException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }



    @Override
    public void draw() {
      try {
        background(20, 15, 60);
        bufferMultithreaded(false);
        image(img, 0, 0);

      }
      catch (RuntimeException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    public static void main(String[] args) {
      String[] appletArgs = new String[] { "ImageTest" };
      PApplet.main(appletArgs);
    }
}
