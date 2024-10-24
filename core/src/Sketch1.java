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

    @Override
    public void settings() {
      try {
        size(1024, 1024, PV3D);
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

        noStroke();
        int MAX = 256;
        colorMode(HSB, MAX, 255, 255);

        float time = ((float)frameCount)*0.02f;

        for (int i = 0; i < 500; i++) {
          fill((frameCount+i*289)%MAX, 255, 255);
          ellipseMode(CORNER);
          ellipse(sin((float)i+time)*noise(i*23)*700f+width/2,
                  cos((float)i+time)*noise(i*23)*700f+height/2, 64f, 64f);

        }
        colorMode(RGB, 256);

//        strokeCap(ROUND);
//        stroke(255);
//        strokeWeight(sin(((float)frameCount)*0.1f)*30f+30f);
        fill(255, 0, 0);
        rect(60f, 60f, 256f, 256f);

//        line(60f, 600f, 360f, 600f);


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
