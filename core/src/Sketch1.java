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
        size(512, 512, PV2D);
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
        fill(255, 0, 0);
        noStroke();

        rect(-1f, -1f, 2f, 2f);

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
