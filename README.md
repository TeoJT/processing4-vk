# Vulkan in Processing

This project aims to add a new Vulkan-based renderer to the Processing framework (https://github.com/processing/processing4).

Currently, it's part of a university dissertation project. However later I plan to release this as a library rather than just being a copy+paste of the whole of Processing.

## How it works

![updated-design-1](https://github.com/user-attachments/assets/1ed4bbb1-88d4-4c30-8ce0-eec2ca128483)

(*Note: this diagram isn't fully up-to-date*)

This framework works by keeping most of the PGraphicsOpenGL code intact and simply emulating OpenGL behaviour through a thin OpenGL-to-Vulkan translation layer. This layer also is specifically optimised to work with certain elements of Processing. This layer is bounded to the PGL abstraction layer. We use LWJGL to use the Vulkan API.

## Features
- New PV3D and PV2D renderers
- OpenGL-GLSL to Vulkan-GLSL shader converter
- Automatic multithreading, meaning sketches can utilise 100% of the CPU.

## What works/doesn't work

### WORKING:
- Primitive 2D shapes (rect, ellipse, line, etc)
- Primitive 3D shapes (box, sphere, etc)
- Textures
- Depth buffer
- Immediate rendering mode

### PARTIALLY WORKING:
- Retained rendering mode (works mostly, but still some bugs, particularly with the StaticParticlesRetained example sketch)
- Custom PShaders (uniform arrays and uniforms that add up to over 256 bytes do not work yet, also GL-to-VK shader converter is still imperfect and some complex shaders will fail to compile)
- PGL (highly recommend against its usage in Processing sketches)
- hint() function (not fully tested yet)

### NOT WORKING / NOT IMPLEMENTED YET:
- Anti-aliasing
- PGraphics (rendering to an off-screen buffer)
- Keeping the previous framebuffer, i.e. drawing without calling background()
- Texture mipmapping
- Materials and lighting (the uniforms for the lighting shaders exceeds the 256 byte limit)
- Probably loads more I haven't noticed yet
- Resizing the window

## Performance
Some of Processing's performance example sketches. Lower is better.

__LineRendering__

![image](https://github.com/user-attachments/assets/8985c14a-bf5b-479c-8989-c82ee5cb55d3)

__TextRendering__

![image](https://github.com/user-attachments/assets/dbe77ecc-3711-4869-a866-a00ed254bab8)

__DynamicParticlesImmediate__

![image](https://github.com/user-attachments/assets/47c78c6f-5fd9-45cf-b77f-12571c5afc91)


