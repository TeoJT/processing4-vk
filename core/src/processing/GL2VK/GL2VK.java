package processing.GL2VK;

import java.nio.IntBuffer;
import java.util.ArrayList;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;

import processing.GL2VK.ShaderSPIRVUtils.SPIRV;
import processing.GL2VK.ShaderSPIRVUtils.ShaderKind;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static processing.GL2VK.ShaderSPIRVUtils.compileShader;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GL2VK {

	public static final int GL_VERTEX_BUFFER = 1;
	public static final int GL_INDEX_BUFFER = 2;

	public static final int GL_VERTEX_SHADER = 1;
	public static final int GL_FRAGMENT_SHADER = 2;

	public static final int GL_COMPILE_STATUS = 1;
	public static final int GL_INFO_LOG_LENGTH = 2;

	public static final int GL_UNSIGNED_BYTE = 1;
	public static final int GL_UNSIGNED_SHORT = 2;
	public static final int GL_UNSIGNED_INT = 3;

  public static final int GL_INT            = 4;
  public static final int GL_BYTE           = 5;
  public static final int GL_SHORT          = 6;
  public static final int GL_FLOAT          = 7;
  public static final int GL_BOOL           = 8;

	public static final int GL_TRUE = 1;
	public static final int GL_FALSE = 0;

	public static final int DEBUG_MODE = 42;

	// Shaders aren't actually anything significant, they're really temporary data structures
	// to create a vulkan pipeline.
	private class GLShader {
		public String source = "";
		public boolean successfulCompile = false;
		public int type;
		public SPIRV spirv = null;
		public String log = "";

		// Use for vertex shaders only. See notes in glCompileShader
		// for why we're oddly putting this here.
		public ShaderAttribInfo attribInfo = null;

		// Once the vert and frag shaders are linked it will
		// be combined into one ArrayList
		public ArrayList<GLUniform> uniforms = new ArrayList<>();

		public GLShader(int type) {
			this.type = type;
		}

		public void setUniforms(ArrayList<GLUniform> uniforms) {
			this.uniforms = uniforms;
			for (GLUniform u : uniforms) {
				// I could and should do it the proper way to
				// ensure GL_VERTEX_SHADER is the same meaning for GLUniform
				// class but let's be real; it's an int with 2 different values.
				u.vertexFragment = type;
			}
		}
	}

	// Attrib pointers are the most stupid thing ever that
	// we need an entire class to do it.
	private class GLAttribPointer {
		public GL2VKPipeline program = null;

		public GLAttribPointer(GL2VKPipeline program) {
			this.program = program;
		}
	}

	// In processing, the glUniform* variable may be called before the pipeline is
	// initialised. We can't just init the pipeline because vertexAttribPointer may
	// not have been called yet and we end up with a pretty broken pipeline.
	// The solution is to temporarily store our glUniform commands and then once our
	// pipeline exists, we may call them straight after.
	private class TempUniformState {
	  public int cmdID = 0;

	  public int location = 0;
	  public float val0 = 0f;
    public float val1 = 0f;
    public float val2 = 0f;
    public float val3 = 0f;

    // For float matricies
    public FloatBuffer mat = null;

    public TempUniformState(int loc, float val0) {
      cmdID = 1;
      this.location = loc;
      this.val0 = val0;
    }
    public TempUniformState(int loc, float val0, float val1) {
      cmdID = 2;
      this.location = loc;
      this.val0 = val0;
      this.val1 = val1;
    }
    public TempUniformState(int loc, float val0, float val1, float val2) {
      cmdID = 3;
      this.location = loc;
      this.val0 = val0;
      this.val1 = val1;
      this.val2 = val2;
    }
    public TempUniformState(int loc, float val0, float val1, float val2, float val3) {
      cmdID = 4;
      this.location = loc;
      this.val0 = val0;
      this.val1 = val1;
      this.val2 = val2;
      this.val3 = val3;
    }
    // Anything else
    public TempUniformState(int loc, int cmdID, FloatBuffer buff) {
      this.cmdID = cmdID;
      this.location = loc;
      mat = buff;
    }

    public void execute() {
      switch (cmdID) {
      case 1:
        glUniform1f(location, val0);
        break;
      case 2:
        glUniform2f(location, val0, val1);
        break;
      case 3:
        glUniform3f(location, val0, val1, val2);
        break;
      case 4:
        glUniform4f(location, val0, val1, val2, val3);
        break;
      case 99:
        glUniformMatrix4fv(location, 1, false, mat);
        break;
      }
    }
	}

	private VulkanSystem system = null;

	// TODO: Change these to arrayLists?
	private GraphicsBuffer[] buffers = new GraphicsBuffer[4096];
	private GL2VKPipeline[] programs = new GL2VKPipeline[1024];
	private GLShader[] shaders = new GLShader[1024];
	private ArrayList<TempUniformState> tempUniformStates = new ArrayList<>();

	// Vulkan locations != OpenGL attrib locations
	// Attribs are universally unique, meaning that any 2 programs will never have any
	// attribute locations that are the same. (e.g. Program 1 has 1 2 3, Program 2 has 4 5 6)
	// In vulkan, 2 different programs can have different attribute locations
	// (Program 1 has 1 2 3, Program 2 has 1 2 3 4)
	// Because of
	private GLAttribPointer[] glAttribs = new GLAttribPointer[4096];

	// Buffering with secondary command buffers (in high-performance threadnodes) results
	// in the validation layers giving an error related to not being allowed to call vkCmdCopyData
	// while the renderpass is enabled (and disabling it temporarily is not really an option).
	// But, it still seems to perform as normal. Remember validations guarentees that your application
	// will run without error on all hardware. So... what if we were to ignore the warnings for the
	// sake of performance? That's what dangerMode does.
	private boolean dangerMode = false;
	private boolean warningsEnabled = true;

	private int bufferIndex = 1;
	private int programIndex = 1;
	private int shaderIndex = 1;
	private int attribIndex = 1;

	private int boundBuffer = 0;
	private int boundProgram = 0;
	private boolean changeProgram = true;

	// Used to convert shaders
	// Is an instance because it keeps the state from the
	// fragment shader.
	private GL2VKShaderConverter shaderConverter = new GL2VKShaderConverter();



	// Constructor
	public GL2VK(int width, int height) {
		system = new VulkanSystem();
		system.initVulkan(width, height);
	}

	public GL2VK(int debugNumber) {
		if (debugNumber != DEBUG_MODE) {
			system = new VulkanSystem();
			system.initVulkan(1200, 800);
		}
	}

	private void warn(String mssg) {
		if (warningsEnabled) {
			System.err.println("GL2VK WARNING  "+mssg);
		}
	}

	public void glGenBuffers(int count, IntBuffer out) {
		for (int i = 0; i < count; i++) {
			// Create new buffer object
			// We may be in debug mode so we may not use system
			if (system == null) {
				buffers[bufferIndex] = new GraphicsBuffer();
			}
			else {
				buffers[bufferIndex] = new GraphicsBuffer(system);
			}

			// Put it into the int array so we get back our
			// ids to our allocated buffers.
			out.put(i, bufferIndex++);
		}
		out.rewind();
	}


	public void glBindBuffer(int type, int vbo) {
		boundBuffer = vbo;
//		System.out.println("glBindBuffer"+vbo);
	}

	public void glBufferData(int target, int size, ByteBuffer data, int usage) {
		// Get VK usage
		int vkusage = 0;
		switch (target) {
		case GL_VERTEX_BUFFER:
			vkusage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
			break;
		case GL_INDEX_BUFFER:
			vkusage = VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
			break;
		}

		if (boundBuffer <= 0) {
			warn("glBufferData: no bound buffer.");
			return;
		}
		if (buffers[boundBuffer] == null) {
			warn("glBufferData: buffer "+boundBuffer+" doesn't exist.");
			return;
		}


		// Note: target is for specifying vertex_array, indicies_array
		// which we'll likely need. Usage, I have no idea what it does.

		// Create buffer if not exist or currentSize != size.
		buffers[boundBuffer].createBufferAuto(size, vkusage);

		if (data != null) {
		  buffers[boundBuffer].bufferData(data, size, dangerMode);
		}

//  ByteBuffer newData = null;
//    System.out.println(boundBuffer+"VK BUFFER "+buffers[boundBuffer].bufferID);
//    if (data != null) {
//  //    newData = ByteBuffer.allocateDirect(data.capacity());
//  //    newData.order(ByteOrder.LITTLE_ENDIAN);
//      data.rewind();
//
//      int max = data.capacity();
//      if (32 < max) max = 64;
//
//      for (int i = 0; i < max; i+=4) {
//        float f = data.getFloat();
//  //      if (f > 1.0f) f /= 512f;
//        System.out.print(f+" ");
//  //      newData.putFloat(f);
//      }
//      data.rewind();
//      System.out.println();
//
//      for (int i = 0; i < max; i+=4) {
//        short x = data.getShort();
//  //      if (f > 1.0f) f /= 512f;
//        System.out.print(x+" ");
//  //      newData.putFloat(f);
//      }
//      data.rewind();
//  //    newData.rewind();
//      System.out.println();
//    }
	}

	private boolean pipelineInitiated() {
	  return programs[boundProgram].initiated;
	}

	private boolean checkAndPrepareProgram() {
		if (boundProgram <= 0) {
			warn("checkAndPrepareProgram: No program bound.");
			return false;
		}
		if (programs[boundProgram] == null || programs[boundProgram].attribInfo == null) {
			warn("checkAndPrepareProgram: program "+boundProgram+" doesn't exist or isn't set up properly");
			return false;
		}

		if (!pipelineInitiated()) {
			programs[boundProgram].createGraphicsPipeline();
      system.updateNodePipeline(programs[boundProgram].graphicsPipeline);

			// Call our pending uniforms
			for (TempUniformState u : tempUniformStates) {
			  u.execute();
			}
			tempUniformStates.clear();
		}
		else {
		  system.updateNodePipeline(programs[boundProgram].graphicsPipeline);
		}

		if (changeProgram) {
//			system.bindPipelineAllNodes(programs[boundProgram].graphicsPipeline);

			changeProgram = false;
		}

		return true;
	}

	public void glDrawArrays(int mode, int first, int count) {
		// Mode not used
		if (checkAndPrepareProgram() == false) return;

//		int stride = programs[boundProgram].attribInfo.bindingSize;
//		System.out.println("CHECK YOUR STRIDE: "+stride);
//		for (Long val : programs[boundProgram].getVKBuffers()) {
//			System.out.println(val);
//		}
		system.nodeDrawArrays(programs[boundProgram].getVKBuffers(), count, first);
	}


	// In normal openGL, the offset argument is instead a pointer to the indicies, but
	// in jogl it's the bound buffer??
	// Either way, it's java and Processing we're writing this for, so it is what it is.
	public void glDrawElements(int mode, int count, int type, int offset) {
//	  System.out.println("DRAWINDEXED");
		// Mode not used
		if (checkAndPrepareProgram() == false) return;

//		System.out.println("glDrawElements "+boundBuffer+" count "+count+" type "+type+" offset "+offset+"  buffer "+buffers[boundBuffer].bufferID);
		system.nodeDrawIndexed(count, buffers[boundBuffer].bufferID, programs[boundProgram].getVKBuffers(), offset, type);
	}


	// Probably not going to fully implement glEnableVertexAttribArray or glDisableVertexAttribArray
	// because chances are, when we use glVertexAttribPointer, we're being pretty clear that we do,
	// indeed, want to use the vertexAttrib. And it's not like glDisableVertexAttribArray is going to
	// have any effect, you can't disable vertex attribs in a pipeline that's already been created.

	// BUG FOUND TODO:
	// Size should be the number of components in the vector, not the total size!
	// e.g. for a vec4, it should be 4, not 4*4
	public void glVertexAttribPointer(int glindex, int size, int type, boolean normalized, int stride, int offset) {
		if (boundBuffer <= 0) {
			warn("glVertexAttribPointer: don't forget to bind a buffer!");
		}
		if (glindex == -1 || glAttribs[glindex] == null) {
			warn("glVertexAttribPointer: Vertex attrib "+glindex+" doesn't exist.");
			return;
		}




		// Convert from gl index to vk location
		// We also need the program to see what we're doing
		GL2VKPipeline program = glAttribs[glindex].program;
		// It's such a mess, I'm so sorry
		int vkLocation = program.getVKAttribLocation(glindex);


//		System.out.println("ATTRIB BUFFER "+glindex+" "+buffers[boundBuffer].bufferID);
		program.bind(boundBuffer, buffers[boundBuffer]);
		program.vertexAttribPointer(vkLocation, size, type, normalized, stride, offset);
	}

	public int glCreateProgram() {
		int ret = programIndex;
		// Null if it's in debug mode.
		if (system == null) {
			programs[programIndex++] = new GL2VKPipeline();
		}
		else {
			programs[programIndex++] = new GL2VKPipeline(system);
		}
		return ret;
	}

	public int glGetAttribLocation(int program, String name) {
		if (programs[program] == null) {
			warn("glGetAttribLocation: program "+program+" doesn't exist.");
			return 0;
		}
//		System.out.println(name+" "+programs[program].getGLAttribLocation(name));
		return programs[program].getGLAttribLocation(name);
	}


	public int glCreateShader(int type) {
		int ret = shaderIndex;
		shaders[shaderIndex++] = new GLShader(type);
		return ret;
	}

	// TODO: add minor error checking.
	public void glShaderSource(int shader, String source) {
		if (shaders[shader] == null) {
			warn("glShaderSource: shader "+shader+" doesn't exist.");
		}
		// TODO: convert from opengl-style glsl to vulkan glsl
		shaders[shader].source = source;
	}

	public String glGetShaderSource(int shader) {
		if (shaders[shader] == null) {
			warn("glGetShaderSource: shader "+shader+" doesn't exist.");
		}
		return shaders[shader].source;
	}

	public void glDeleteShader(int shader) {
		shaders[shader] = null;
	}


	public void glCompileShader(int shader) {
		GLShader sh = shaders[shader];
		if (sh == null) {
			warn("glCompileShader: shader "+shader+" doesn't exist.");
		}

		// Convert the shader to openGL
		sh.source = shaderConverter.convert(sh.source, sh.type);

//		System.out.println(sh.source);



		glCompileVKShader(shader);
	}

	// This is simple stuff to understand (i hope)
	public void glCompileVKShader(int shader) {
		GLShader sh = shaders[shader];
		if (sh == null) {
			warn("glCompileShader: shader "+shader+" doesn't exist.");
		}

		ShaderKind shaderKind;
		if (sh.type == GL_VERTEX_SHADER) {
			shaderKind = ShaderKind.VERTEX_SHADER;
		}
		else if (sh.type == GL_FRAGMENT_SHADER) {
			shaderKind = ShaderKind.FRAGMENT_SHADER;
		}
		else shaderKind = ShaderKind.VERTEX_SHADER;

		try {
			sh.spirv = compileShader(sh.source, shaderKind);
			sh.successfulCompile = true;
		}
		catch (RuntimeException e) {
			sh.successfulCompile = false;
			sh.log = e.getMessage();
		}


		// TODO: failed compiles just throws an exception. Actually check to see if it compiled successfully
		// or not.

		if (sh.successfulCompile) {
			// Here, we must get attribute information.
			//
			// "Why not do it when we create our pipeline or when we
			// attach the shader?"
			//
			// Because as soon as we compile the shader, the program could
			// call glGetAttribLocation before attaching the shader or
			// linking the program.
			// Thanks, opengl, you're so messy that we have to do things weirdly.
			if (sh.type == GL_VERTEX_SHADER) {
				sh.attribInfo = new ShaderAttribInfo(sh.source);
			}

			// Also let's parse the uniform shader too
			sh.setUniforms(UniformParser.parseUniforms(sh.source));
		}
	}


	// This function populates the glAttribs array which contains information about
	// the vulkan program, and nothing else.
	// It literally just loops over or something
	private void addGLAttribs(GL2VKPipeline program) {
		int l = program.attribInfo.nameToLocation.size();
		for (int i = 0; i < l; i++) {
			glAttribs[attribIndex++] = new GLAttribPointer(program);
		}
	}



	public void glAttachShader(int program, int shader) {
		GLShader sh = shaders[shader];
		if (sh == null) {
			warn("glGetShaderiv: shader "+shader+" doesn't exist.");
			return;
		}
		if (!sh.successfulCompile) {
			warn("glAttachShader: Can't attach shader that hasn't been compiled or failed compilation.");
			return;
		}
		if (programs[program] == null) {
			warn("glAttachShader: program "+program+" doesn't exist.");
			return;
		}
		if (sh.type == GL_VERTEX_SHADER) {
			programs[program].vertShaderSPIRV = shaders[shader].spirv;
			// Of course we'll need the attrib info to our pipeline.
			// This function has been modified so that instead of vk locations, we get
			// back gl locations.
			// Welcome to the absolute unhinged nature of opengl.
			programs[program].addAttribInfo(sh.attribInfo, attribIndex);

			// And then
			// We'll need gl attribs since
			// gl attrib locations != vulkan attrib locations.
			addGLAttribs(programs[program]);

		}
		else if (sh.type == GL_FRAGMENT_SHADER) {
			programs[program].fragShaderSPIRV = shaders[shader].spirv;
		}

		// And also add the uniform attribs to the GLPipeline
		programs[program].addUniforms(sh.uniforms);

//		System.out.println("PROGRAM "+program+" to "+sh.source);
	}

	// Mainly just used for getting shader compilation status.
	public void glGetShaderiv(int shader, int pname, IntBuffer params) {
		GLShader sh = shaders[shader];
		if (sh == null) {
			warn("glGetShaderiv: shader "+shader+" doesn't exist.");
			return;
		}
		if (pname == GL_COMPILE_STATUS) {
			int status = GL_FALSE;
			if (sh.successfulCompile == true) {
				status = GL_TRUE;
			}
			params.put(0, status);
		}
		if (pname == GL_INFO_LOG_LENGTH) {
			params.put(0, shaders[shader].log.length());
		}
	}

	public String glGetShaderInfoLog(int shader) {
		if (shaders[shader] == null) {
			warn("glGetShaderInfoLog: shader "+shader+" doesn't exist.");
			return "";
		}
		return shaders[shader].log;
	}

	// Because we create our pipeline on draw command calls, this effectively does nothing.
	public void glLinkProgram(int program) {

	}

	public void glUseProgram(int program) {
	  if (program == 0) {
	    return;
	  }
		if (program != boundProgram) {
			changeProgram = true;
		}
		boundProgram = program;
//		System.out.println("USEPROGRAM "+program);
	}

	public int getUniformLocation(int program, String name) {
		if (programs[program] == null) {
			warn("getUniformLocation: program "+program+" doesn't exist.");
			return -1;
		}
//		System.out.println(name+" "+programs[program].getUniformLocation(name));
		return programs[program].getUniformLocation(name);
	}


	public void glUniform1f(int location, float value0) {
		GLUniform uniform = programs[boundProgram].getUniform(location);

		if (pipelineInitiated()) {
		  system.nodePushConstants(programs[boundProgram].pipelineLayout, uniform.vertexFragment, uniform.offset, value0);
		}
		else {
		  tempUniformStates.add(new TempUniformState(location, value0));
		}
	}


	public void glUniform2f(int location, float value0, float value1) {
		GLUniform uniform = programs[boundProgram].getUniform(location);

		if (pipelineInitiated()) {
	    system.nodePushConstants(programs[boundProgram].pipelineLayout, uniform.vertexFragment, uniform.offset, value0, value1);
    }
    else {
      tempUniformStates.add(new TempUniformState(location, value0, value1));
    }
	}

	public void glUniform3f(int location, float value0, float value1, float value2) {
		GLUniform uniform = programs[boundProgram].getUniform(location);

		if (pipelineInitiated()) {
	    system.nodePushConstants(programs[boundProgram].pipelineLayout, uniform.vertexFragment, uniform.offset, value0, value1, value2);
    }
    else {
      tempUniformStates.add(new TempUniformState(location, value0, value1, value2));
    }
	}


	public void glUniform4f(int location, float value0, float value1, float value2, float value3) {

		GLUniform uniform = programs[boundProgram].getUniform(location);


    if (pipelineInitiated()) {
      system.nodePushConstants(programs[boundProgram].pipelineLayout, uniform.vertexFragment, uniform.offset, value0, value1, value2, value3);
    }
    else {
      tempUniformStates.add(new TempUniformState(location, value0, value1, value2, value3));
    }
	}

	// TODO: add other uniform functions.


	public void glUniformMatrix4fv(int location, int count, boolean transpose, ByteBuffer mat) {

		// TODO: warn if count is > 1

		GLUniform uniform = programs[boundProgram].getUniform(location);

		// Not actually used by processing so ok to comment this out
//    if (pipelineInitiated()) {
      system.nodePushConstants(programs[boundProgram].pipelineLayout, uniform.vertexFragment, uniform.offset, mat);
//    }
//    else {
//      tempUniformStates.add(new TempUniformState(location, 99, mat));
//    }
	}

	public void glUniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer mat) {
//	  System.out.println("glUniformMatrix4fv");
    if (count > 1) {
      warn("glUniformMatrix4fv: count above 1 not supported yet.");
    }

//    System.out.println("MATRIX "+transpose);
//    mat.rewind();
//    for (int y = 0; y < 4; y++) {
//      for (int x = 0; x < 4; x++) {
//        System.out.print(" "+mat.get());
//      }
//      System.out.println();
//    }
//    mat.rewind();


    GLUniform uniform = programs[boundProgram].getUniform(location);

    if (pipelineInitiated()) {
      system.nodePushConstants(programs[boundProgram].pipelineLayout, uniform.vertexFragment, uniform.offset, mat);
    }
    else {
      tempUniformStates.add(new TempUniformState(location, 99, mat));
    }
  }

//	public void glUniform2f(int location, float value0, float value1) {
//		if (programs[boundProgram] == null) {
//			warn("glAttachShader: program "+boundProgram+" doesn't exist.");
//			return;
//		}
//		if (checkAndPrepareProgram() == false) return;
//
//		GLUniform uniform = programs[boundProgram].getUniform(location);
//
//		int size = 8;
//		ByteBuffer buffer = ByteBuffer.allocate(size);
//		buffer.order(ByteOrder.LITTLE_ENDIAN);
//		buffer.putFloat(value0);
//		buffer.putFloat(value1);
//		buffer.rewind();
//
//		system.nodePushConstants(programs[boundProgram].pipelineLayout, uniform.vertexFragment, uniform.offset, buffer);
//	}



	private void cleanup() {
		system.cleanupNodes();

		// Clean up graphics buffers
		for (int i = 0; i < buffers.length; i++) {
			if (buffers[i] != null) {
				buffers[i].destroy();
			}
		}

		// Clean up pipelines
		for (int i = 0; i < programs.length; i++) {
			if (programs[i] != null) {
				programs[i].clean();
			}
		}

		system.cleanupRest();
	}

	//     Placeholders for testing
	public boolean shouldClose() {
		return system.shouldClose();
	}

	public void close() {
		cleanup();
	}


	public void beginRecord() {
		system.beginRecord();
		changeProgram = true;
	}

	public void endRecord() {
		system.endRecord();
	}

	public void selectNode(int node) {
		system.selectNode(node);
	}

	public int getNodesCount() {
		return system.getNodesCount();
	}

	public void setDangerMode(boolean mode) {
		dangerMode = mode;
	}

	public GL2VKPipeline getPipeline(int program) {
		return programs[program];
	}
}