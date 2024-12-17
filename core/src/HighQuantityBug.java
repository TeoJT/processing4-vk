import processing.core.PApplet;

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

public class HighQuantityBug extends PApplet {

  PShape particles;
  PImage sprite;
  PImage redsprite;

  int npartTotal = 10000;
  int npartPerFrame = 50;
  float speed = 10.0f;
  float gravity = 0.05f;
  float partSize = 50f;

  int partLifetime;
  PVector velocities[];
  int lifetimes[];

  int fcount, lastm;
  float frate;
  int fint = 3;

//  DRAW ELEMENTS  count: 49146  offset: 0
//  DRAW ELEMENTS  count: 10854  offset: 98292

  @Override
  public void settings() {
    size(640*2, 480*2, PV3D);
  }

  @Override
  public void setup() {
    frameRate(60);

    particles = createShape(PShape.GROUP);
    sprite = loadImage("C:\\mydata\\projects\\processing-examples\\Demos\\Performance\\StaticParticlesRetained\\data\\sprite.png");
    redsprite = loadImage("C:\\mydata\\projects\\processing-examples\\Demos\\Performance\\StaticParticlesRetained\\data\\redsprite.png");

    float x = 0;
    float y = 0;
    int redparticletimer = 0;
    for (int n = 0; n < npartTotal; n++) {
      PShape part = createShape();
      part.beginShape(QUAD);
      part.noStroke();
      part.texture(sprite);

      partSize = 5f;
//      if (redparticletimer <= 8189) {
//      }
//      else {
//        partSize = 100f;
//      }

      part.normal(0, 0, 1);
      part.vertex(x, y, 0, 0);
      part.vertex(x+partSize, y, sprite.width, 0);
      part.vertex(x+partSize, y+partSize, sprite.width, sprite.height);
      part.vertex(x, y+partSize, 0, sprite.height);
      part.endShape();
      particles.addChild(part);
      redparticletimer++;

      x += partSize;
      if (x > width) {
        x = 0f;
        y += partSize;
      }
    }

    partLifetime = npartTotal / npartPerFrame;
    initVelocities();
    initLifetimes();

    // Writing to the depth buffer is disabled to avoid rendering
    // artifacts due to the fact that the particles are semi-transparent
    // but not z-sorted.
    hint(DISABLE_DEPTH_MASK);
  }

  @Override
  public void draw() {
    background(0);

    selectNode(AUTO);

    println("------------------------------");


//    for (int n = 0; n < particles.getChildCount(); n++) {
//      PShape part = particles.getChild(n);
//
//      lifetimes[n]++;
//      if (lifetimes[n] == partLifetime) {
//        lifetimes[n] = 0;
//      }
//
//      if (0 <= lifetimes[n]) {
//        float opacity = 1.0f - (float)(lifetimes[n]) / partLifetime;
//        part.setTint(color(255f, 255f));
//
//        if (lifetimes[n] == 0) {
//          // Re-spawn dead particle
//          part.resetMatrix();
//          part.translate(mouseX, mouseY);
//          float angle = random(0f, TWO_PI);
//          float s = random(0.5f * speed, 0.5f * speed);
//          velocities[n].x = s * cos(angle);
//          velocities[n].y = s * sin(angle);
//        } else {
//          part.translate(velocities[n].x, velocities[n].y);
//          velocities[n].y += gravity;
//        }
//      } else {
//        part.setTint(color(0));
//      }
//    }

    shape(particles);

    fcount += 1;
    int m = millis();
    if (m - lastm > 1000 * fint) {
      frate = (float)(fcount) / fint;
      fcount = 0;
      lastm = m;
      println("fps: " + frate);
    }
  }

  void initVelocities() {
    velocities = new PVector[npartTotal];
    for (int n = 0; n < velocities.length; n++) {
      velocities[n] = new PVector();
    }
  }

  void initLifetimes() {
    // Initializing particles with negative lifetimes so they are added
    // progressively into the screen during the first frames of the sketch
    lifetimes = new int[npartTotal];
    int t = -1;
    for (int n = 0; n < lifetimes.length; n++) {
      if (n % npartPerFrame == 0) {
        t++;
      }
      lifetimes[n] = -t;
    }
  }

  public static void main(String[] args) {
    String[] appletArgs = new String[] { "HighQuantityBug" };
    PApplet.main(appletArgs);
  }
}
