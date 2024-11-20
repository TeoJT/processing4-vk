import processing.core.PApplet;

public class DepthTest extends PApplet {
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



  float y = 0;
  @Override
  public void draw() {
    try {
      background(200);
      bufferMultithreaded(false);
      noLights();
      noStroke();


      if (keyPressed && key == 'w') {
        y--;
      }
      if (keyPressed && key == 's') {
        y++;
      }


      pushMatrix();
      translate(width/2, height/2, -200);
      pushMatrix();
      rotateX(QUARTER_PI);
      rotateY(QUARTER_PI);
      rotateZ(QUARTER_PI);
      fill(255, 0, 0);
      box(500, 500, 500);
      popMatrix();
      popMatrix();

      pushMatrix();
      translate(width/2+200, height/2+200, -120);
      fill(0, 255, 0);
      box(500, 500, 500);
      popMatrix();




//      println("fps: "+frameRate);
    }
    catch (RuntimeException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static void main(String[] args) {
    String[] appletArgs = new String[] { "DepthTest" };
    PApplet.main(appletArgs);
  }
}
