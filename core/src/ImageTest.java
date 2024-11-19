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
    PImage img1;
    PImage img2;
    PImage img3;

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
//        img1 = loadImage("C:/mydata/temp/hh.jpg");
//        img2 = loadImage("C:/mydata/temp/mm.png");
//        img3 = loadImage("C:/mydata/temp/ee.png");
      }
      catch (RuntimeException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }

    String text = "Hello world. This is an example of some text working. I hope. Yeah, vulkan can be a lil unstable lol";

    @Override
    public void draw() {
//      println("-----------------------");
      try {

        background(20, 15, 60);
        bufferMultithreaded(false);
        selectNode(0);

//        image(img1, 0, 0, 512, 512);
//
//        image(img2, 100, 100, 512, 512);
//
//        image(img3, 200, 200, 512, 512);

        fill(255);
        textAlign(CENTER, CENTER);
        textSize(60);

        int i = min(frameCount/3, text.length());
        text(text.substring(0, i), 0, 0, width, height);

//        println(frameRate);
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
