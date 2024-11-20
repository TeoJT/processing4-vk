

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

public class ThreadBug1 extends PApplet {

  PShape particle = null;

    @Override
    public void settings() {
      try {
        size(640, 360, PV3D);
      }
      catch (RuntimeException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }


    PShader sh;
    PShape grid;

    @Override
    public void setup() {
     sh = loadShader("C:\\mydata\\projects\\processing-examples\\Demos\\Graphics\\MeshTweening\\data\\frag.glsl", "C:\\mydata\\projects\\processing-examples\\Demos\\Graphics\\MeshTweening\\data\\vert.glsl");
     shader(sh);

     grid = createShape();
     grid.beginShape(QUADS);
     grid.noStroke();
     grid.fill(150);
     float d = 10;
     for (int x = -500; x < 500; x += d) {
       for (int y = -500; y < 500; y += d) {
         grid.fill(255 * noise(x, y));
         grid.attribPosition("tweened", x, y, 100 * noise(x, y));
         grid.vertex(x, y, 0);

         grid.fill(255 * noise(x + d, y));
         grid.attribPosition("tweened", x + d, y, 100 * noise(x + d, y));
         grid.vertex(x + d, y, 0);

         grid.fill(255 * noise(x + d, y + d));
         grid.attribPosition("tweened", x + d, y + d, 100 * noise(x + d, y + d));
         grid.vertex(x + d, y + d, 0);

         grid.fill(255 * noise(x, y + d));
         grid.attribPosition("tweened", x, y + d, 100 * noise(x, y + d));
         grid.vertex(x, y + d, 0);
       }
     }
     grid.endShape();
    }

    @Override
    public void draw() {
     background(255);

     sh.set("tween", map(mouseX, 0, width, 0, 1));

     translate(width/2, height/2, 0);
     rotateX(frameCount * 0.01f);
     rotateY(frameCount * 0.01f);

     shape(grid);
    }

    public static void main(String[] args) {
      String[] appletArgs = new String[] { "ThreadBug1" };
      PApplet.main(appletArgs);
    }
}

