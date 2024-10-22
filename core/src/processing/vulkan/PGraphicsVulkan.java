package processing.vulkan;

import processing.GL2VK.GL2VK;
import processing.core.PSurface;
import processing.opengl.PGL;
import processing.opengl.PGraphicsOpenGL;
import processing.opengl.PJOGL;

public class PGraphicsVulkan extends PGraphicsOpenGL {

  public GL2VK gl2vk = null;

  public PGraphicsVulkan() {
    super();
  }

  @Override
  protected PGL createPGL(PGraphicsOpenGL pg) {
    return new PVK(pg);
  }

  @Override
  public PSurface createSurface() {
    return surface = new PSurfaceVK(this);
  }


}
