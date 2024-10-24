package processing.GL2VK;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

class Tests {

	String code1 =
			"""
#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;

layout(location = 0) out vec3 fragColor;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}
			""";

	String weirdCode =
			"""
#version 450



void main() {
layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;
}
			""";


String crazyAttribsCode =
			"""
#version 450

layout(location = 0) in vec2 group1attrib1;
layout(location = 1) in vec3 group1attrib2;

layout(location = 2) in vec3 group2attrib1;
layout(location = 3) in float group2attrib2;
layout(location = 4) in vec4 group2attrib3;
layout(location = 5) in float group2attrib4;

layout(location = 6) in float group3attrib1;

layout(location = 0) out vec3 fragColor;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}
		""";



	private VkVertexInputAttributeDescription.Buffer getDescription1() {

		GL2VKPipeline pipeline = new GL2VKPipeline();
		pipeline.compileVertex(code1);
		pipeline.bind(1);
		pipeline.vertexAttribPointer(0, 2, GL2VK.GL_FLOAT, false, 5*4, 0);
		pipeline.vertexAttribPointer(1, 3, GL2VK.GL_FLOAT, false, 5*4, 2*4);

		return pipeline.getAttributeDescriptions();
	}


	private VkVertexInputAttributeDescription.Buffer getDescription2() {
		GL2VKPipeline pipeline = new GL2VKPipeline();
		pipeline.compileVertex(code1);
		pipeline.bind(1);
    pipeline.vertexAttribPointer(0, 2, GL2VK.GL_FLOAT, false, 5*4, 0);
		pipeline.bind(2);
    pipeline.vertexAttribPointer(1, 3, GL2VK.GL_FLOAT, false, 5*4, 2*4);

		return pipeline.getAttributeDescriptions();
	}

	private VkVertexInputAttributeDescription.Buffer getDescription3() {
		GL2VKPipeline pipeline = new GL2VKPipeline();
		pipeline.compileVertex(code1);
		pipeline.bind(1);
		pipeline.vertexAttribPointer(0);
		pipeline.vertexAttribPointer(1);

		return pipeline.getAttributeDescriptions();
	}

	private VkVertexInputBindingDescription.Buffer getBindings1() {

		GL2VKPipeline pipeline = new GL2VKPipeline();
		pipeline.compileVertex(code1);
		pipeline.bind(1);
    pipeline.vertexAttribPointer(0, 2, GL2VK.GL_FLOAT, false, 5*4, 0);
    pipeline.vertexAttribPointer(1, 3, GL2VK.GL_FLOAT, false, 5*4, 2*4);

		return pipeline.getBindingDescriptions();
	}

	private VkVertexInputBindingDescription.Buffer getBindings2() {

		GL2VKPipeline pipeline = new GL2VKPipeline();
		pipeline.compileVertex(code1);
		pipeline.bind(1);
    pipeline.vertexAttribPointer(0, 2, GL2VK.GL_FLOAT, false, 5*4, 0);
		pipeline.bind(2);
    pipeline.vertexAttribPointer(1, 3, GL2VK.GL_FLOAT, false, 5*4, 2*4);

		return pipeline.getBindingDescriptions();
	}







	@Test
	public void interleaved_binding() {
		assertEquals(0, getDescription1().get(0).binding());
		assertEquals(0, getDescription1().get(1).binding());
	}


	@Test
	public void basic_location() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription1();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
	}

	@Test
	public void basic_offset() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription1();
		assertEquals(0, descriptions.get(0).offset());
		assertEquals(2*4, descriptions.get(1).offset());
	}

	@Test
	public void separate_binding() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription2();
		assertEquals(0, descriptions.get(0).binding());
		assertEquals(1, descriptions.get(1).binding());
	}

	// Just a neat lil bunch of tests to have

	@Test
	public void simple_location() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription3();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
	}


	@Test
	public void simple_offset() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription3();
		assertEquals(0, descriptions.get(0).offset());
		assertEquals(2*4, descriptions.get(1).offset());
	}

	@Test
	public void simple_format() {
		VkVertexInputAttributeDescription.Buffer descriptions = getDescription3();
		assertEquals(VK_FORMAT_R32G32_SFLOAT, descriptions.get(0).format());
		assertEquals(VK_FORMAT_R32G32B32_SFLOAT, descriptions.get(1).format());
	}

	// Binding description tests
	@Test
	public void real_binding_test_1() {
		VkVertexInputBindingDescription.Buffer bindings = getBindings1();

		assertEquals(0, bindings.get(0).binding());
		assertEquals(1, bindings.capacity());
	}

	@Test
	public void real_binding_test_2() {
		VkVertexInputBindingDescription.Buffer bindings = getBindings2();

		assertEquals(0, bindings.get(0).binding());
		assertEquals(1, bindings.get(1).binding());
		assertEquals(2, bindings.capacity());
	}
















String vertSource1 = """
#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;

layout(location = 0) out vec3 fragColor;

void main() {
    gl_Position = vec4(inPosition, 0.0, 1.0);
    fragColor = inColor;
}
		""";

String vertSource1_alt = """
#version 450

layout(location = 2) in vec2 inPosition;
layout(location = 3) in vec3 inColor;

layout(location = 0) out vec3 fragColor;

void main() {
    gl_Position = vec4(inPosition, 0.0, 1.0);
    fragColor = inColor;
}
		""";

String fragSource1 = """
#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec3 fragColor;

layout(location = 0) out vec4 outColor;

void main() {
    outColor = vec4(fragColor, 1.0);
}
		""";

String vertSource2 = """
#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inNormals;
layout(location = 2) in vec3 inColor;
layout(location = 3) in float inBrightness;

layout(location = 0) out vec3 fragColor;

void main() {
// An awful program but we're just testing.
    gl_Position = vec4(inPosition*inNormals.xy, 0.0, 1.0);
    fragColor = inColor*vec3(inBrightness);
}
		""";

String fragSource2 = """
#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec3 fragColor;

layout(location = 0) out vec4 outColor;

void main() {
    outColor = vec4(fragColor, 1.0);
}
		""";




	// And now
	// For the openGL tests
	int glProgram1 = -1;
	int failShader = -1;
	int vertShader1 = -1;
	int fragShader1 = -1;
	int testbuffer1 = -1;
	int testbuffer2 = -1;

	private GL2VK glProgram1() {
		return glProgram1(false);
	}

	private GL2VK glProgram1(boolean extraBinding) {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		glProgram1 = gl.glCreateProgram();
		vertShader1 = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		fragShader1 = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	testbuffer1 = out.get(0);
    	testbuffer2 = out.get(1);

		gl.glShaderSource(vertShader1, vertSource1);
		gl.glShaderSource(fragShader1, fragSource1);

		gl.glCompileVKShader(vertShader1);
		gl.glCompileVKShader(fragShader1);

		gl.glAttachShader(glProgram1, vertShader1);
		gl.glAttachShader(glProgram1, fragShader1);

		int position = gl.glGetAttribLocation(glProgram1, "inPosition");
		int color = gl.glGetAttribLocation(glProgram1, "inColor");

		ByteBuffer data = ByteBuffer.allocate(10);

		gl.glBindBuffer(0, testbuffer1);
		gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, 10, data, 0);

		gl.glVertexAttribPointer(position, 4, GL2VK.GL_FLOAT, false, 5*4, 0);
		if (extraBinding) {
			gl.glBindBuffer(0, testbuffer2);
			gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, 10, data, 0);
		}
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);

		return gl;
	}

	// Intentional shader compilation fail
	private GL2VK glProgram2() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		glProgram1 = gl.glCreateProgram();
		failShader = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);

		gl.glShaderSource(failShader, weirdCode);

		gl.glCompileVKShader(failShader);

		return gl;
	}


	private VkVertexInputAttributeDescription.Buffer glProgram1Description() {
		// Don't care that I'm using 1 here.
		return glProgram1().getPipeline(glProgram1).getAttributeDescriptions();
	}

	private VkVertexInputBindingDescription.Buffer glProgram1Bindings1() {
		return glProgram1().getPipeline(glProgram1).getBindingDescriptions();
	}

	private VkVertexInputBindingDescription.Buffer glProgram1Bindings2() {
		return glProgram1(true).getPipeline(glProgram1).getBindingDescriptions();
	}


	@Test
	public void glprogram_location_description() {
		VkVertexInputAttributeDescription.Buffer descriptions = glProgram1Description();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
	}

	@Test
	public void glprogram_location_exists() {
		GL2VK gl = glProgram1();
		assertEquals(1, gl.glGetAttribLocation(glProgram1, "inColor"));
		assertEquals(2, gl.glGetAttribLocation(glProgram1, "inPosition"));
	}

	@Test
	public void glprogram_offset_description() {
		VkVertexInputAttributeDescription.Buffer descriptions = glProgram1Description();
		assertEquals(0, descriptions.get(0).offset());
		assertEquals(8, descriptions.get(1).offset());
	}

	// Binding descriptions
	@Test
	public void glprogram_binding_description_interleaved() {
		VkVertexInputBindingDescription.Buffer descriptions = glProgram1Bindings1();
		assertEquals(0, descriptions.get(0).binding());
	}

	// Binding descriptions
	@Test
	public void glprogram_binding_description_separate() {
		VkVertexInputBindingDescription.Buffer descriptions = glProgram1Bindings2();
		assertEquals(0, descriptions.get(0).binding());
		assertEquals(1, descriptions.get(1).binding());
	}

	// Compile failure expected
	@Test
	public void compile_fail() {
		GL2VK gl = glProgram2();
		IntBuffer out = IntBuffer.allocate(1);
		gl.glGetShaderiv(failShader, GL2VK.GL_COMPILE_STATUS, out);
		assertEquals(GL2VK.GL_FALSE, out.get(0));
	}

	// Compile pass expected
	@Test
	public void compile_pass() {
		GL2VK gl = glProgram1();

		IntBuffer out = IntBuffer.allocate(1);
		gl.glGetShaderiv(vertShader1, GL2VK.GL_COMPILE_STATUS, out);
		assertEquals(GL2VK.GL_TRUE, out.get(0));

		gl.glGetShaderiv(fragShader1, GL2VK.GL_COMPILE_STATUS, out);
		assertEquals(GL2VK.GL_TRUE, out.get(0));
	}

	// Compile pass expected
	@Test
	public void compile_fail_info() {
		GL2VK gl = glProgram2();

		String err = gl.glGetShaderInfoLog(failShader);
		System.out.println("Ready for a shader compile error?");
		System.out.println(err);
		assertTrue(err.length() > 0);
	}

	@Test
	public void check_shadercode2() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		glProgram1 = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource2);
		gl.glShaderSource(fragShaderX, fragSource2);

		gl.glCompileVKShader(vertShaderX);
		gl.glCompileVKShader(fragShaderX);

		IntBuffer out1 = IntBuffer.allocate(1);
		gl.glGetShaderiv(vertShaderX, GL2VK.GL_COMPILE_STATUS, out1);
		IntBuffer out2 = IntBuffer.allocate(1);
		gl.glGetShaderiv(fragShaderX, GL2VK.GL_COMPILE_STATUS, out2);

		assertEquals(1, out1.get(0));
		assertEquals(1, out2.get(0));
	}


	@Test
	public void glprogram_multiple_shaders_step() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);

    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(fragShaderX, fragSource1);

		// Compile shaders X
		gl.glCompileVKShader(vertShaderX);
		gl.glCompileVKShader(fragShaderX);

		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programX, fragShaderX);

		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);

		// Test X
		VkVertexInputAttributeDescription.Buffer descriptions = gl.getPipeline(programX).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());

		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

    	// Pass source code
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderY, fragSource2);

		// Compile shaders X
		gl.glCompileVKShader(vertShaderY);
		gl.glCompileVKShader(fragShaderY);

		// attach to program X
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programY, fragShaderY);

		//layout(location = 0) in vec2 inPosition;
		//layout(location = 1) in vec3 inNormals;
		//layout(location = 2) in vec3 inColor;
		//layout(location = 3) in float inBrightness;

		// Vertex attribs X
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");

		gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(positionY, 2*4, 0, false, 9*4, 0);
		gl.glVertexAttribPointer(normals, 3*4, 0, false, 9*4, 2*4);
		gl.glVertexAttribPointer(colorY, 3*4, 0, false, 9*4, 5*4);
		gl.glVertexAttribPointer(brightness, 1*4, 0, false, 9*4, 8*4);

		descriptions = gl.getPipeline(programY).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
		assertEquals(2, descriptions.get(2).location());
		assertEquals(3, descriptions.get(3).location());
	}



	// Let's do some variations to make the ordering weird and try to catch out bugs.
	// I just wanna test it differently in different ways now lmao
	@Test
	public void glprogram_multiple_shaders_after() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);

    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(fragShaderX, fragSource1);

		// Compile shaders X
		gl.glCompileVKShader(vertShaderX);
		gl.glCompileVKShader(fragShaderX);

		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programX, fragShaderX);

		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);

		// Test X
		VkVertexInputAttributeDescription.Buffer descriptions = gl.getPipeline(programX).getAttributeDescriptions();


		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

    	// Pass source code
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderY, fragSource2);

		// Compile shaders X
		gl.glCompileVKShader(vertShaderY);
		gl.glCompileVKShader(fragShaderY);

		// attach to program X
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programY, fragShaderY);

		//layout(location = 0) in vec2 inPosition;
		//layout(location = 1) in vec3 inNormals;
		//layout(location = 2) in vec3 inColor;
		//layout(location = 3) in float inBrightness;

		// Vertex attribs X
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");

		gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(positionY, 2*4, 0, false, 9*4, 0);
		gl.glVertexAttribPointer(normals, 3*4, 0, false, 9*4, 2*4);
		gl.glVertexAttribPointer(colorY, 3*4, 0, false, 9*4, 5*4);
		gl.glVertexAttribPointer(brightness, 1*4, 0, false, 9*4, 8*4);

		descriptions = gl.getPipeline(programY).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
		assertEquals(2, descriptions.get(2).location());
		assertEquals(3, descriptions.get(3).location());
	}



	@Test
	public void glprogram_multiple_shaders_merged() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);

    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderX, fragSource1);
		gl.glShaderSource(fragShaderY, fragSource2);

		// Compile shaders X
		gl.glCompileVKShader(vertShaderX);
		gl.glCompileVKShader(vertShaderY);
		gl.glCompileVKShader(fragShaderX);
		gl.glCompileVKShader(fragShaderY);

		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programX, fragShaderX);
		gl.glAttachShader(programY, fragShaderY);

		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);

		gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(positionY, 2*4, 0, false, 9*4, 0);
		gl.glVertexAttribPointer(normals, 3*4, 0, false, 9*4, 2*4);
		gl.glVertexAttribPointer(colorY, 3*4, 0, false, 9*4, 5*4);
		gl.glVertexAttribPointer(brightness, 1*4, 0, false, 9*4, 8*4);

		// Test X
		VkVertexInputAttributeDescription.Buffer descriptions = gl.getPipeline(programX).getAttributeDescriptions();

		descriptions = gl.getPipeline(programX).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());

		descriptions = gl.getPipeline(programY).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());
		assertEquals(2, descriptions.get(2).location());
		assertEquals(3, descriptions.get(3).location());
	}



	@Test
	public void glprogram_multiple_shaders_merged_index() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);

    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderX, fragSource1);
		gl.glShaderSource(fragShaderY, fragSource2);

		// Compile shaders X
		gl.glCompileVKShader(vertShaderX);
		gl.glCompileVKShader(vertShaderY);
		gl.glCompileVKShader(fragShaderX);
		gl.glCompileVKShader(fragShaderY);

		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programX, fragShaderX);
		gl.glAttachShader(programY, fragShaderY);

		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");

		assertEquals(1, color);
		assertEquals(2, position);
		assertEquals(3, brightness);
		assertEquals(4, colorY);
		assertEquals(5, positionY);
		assertEquals(6, normals);
	}



	public void glprogram_multiple_shaders_step_index() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);

    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(fragShaderX, fragSource1);

		// Compile shaders X
		gl.glCompileVKShader(vertShaderX);
		gl.glCompileVKShader(fragShaderX);

		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programX, fragShaderX);

		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);

		// Test X
		VkVertexInputAttributeDescription.Buffer descriptions = gl.getPipeline(programX).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());

		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

    	// Pass source code
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderY, fragSource2);

		// Compile shaders X
		gl.glCompileVKShader(vertShaderY);
		gl.glCompileVKShader(fragShaderY);

		// attach to program X
		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programY, fragShaderY);

		//layout(location = 0) in vec2 inPosition;
		//layout(location = 1) in vec3 inNormals;
		//layout(location = 2) in vec3 inColor;
		//layout(location = 3) in float inBrightness;

		// Vertex attribs X
		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");

		assertEquals(1, color);
		assertEquals(2, position);
		assertEquals(3, brightness);
		assertEquals(4, colorY);
		assertEquals(5, positionY);
		assertEquals(6, normals);
	}



	@Test
	public void glprogram_multiple_shaders_merged_offset() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);

    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderX, fragSource1);
		gl.glShaderSource(fragShaderY, fragSource2);

		// Compile shaders X
		gl.glCompileVKShader(vertShaderX);
		gl.glCompileVKShader(vertShaderY);
		gl.glCompileVKShader(fragShaderX);
		gl.glCompileVKShader(fragShaderY);

		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programX, fragShaderX);

		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);


		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programY, fragShaderY);

		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");

//		gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(positionY, 2*4, 0, false, 9*4, 5*4);
		gl.glVertexAttribPointer(normals, 3*4, 0, false, 9*4, 7*4);
		gl.glVertexAttribPointer(colorY, 3*4, 0, false, 9*4, 10*4);
		gl.glVertexAttribPointer(brightness, 1*4, 0, false, 9*4, 11*4);

		// Test X
		VkVertexInputAttributeDescription.Buffer descriptions = gl.getPipeline(programX).getAttributeDescriptions();

		descriptions = gl.getPipeline(programX).getAttributeDescriptions();
		assertEquals(0, descriptions.get(0).offset());
		assertEquals(2*4, descriptions.get(1).offset());

		descriptions = gl.getPipeline(programY).getAttributeDescriptions();
		assertEquals(5*4, descriptions.get(0).offset());
		assertEquals(7*4, descriptions.get(1).offset());
		assertEquals(10*4, descriptions.get(2).offset());
		assertEquals(11*4, descriptions.get(3).offset());
	}



	@Test
	public void glprogram_multiple_shaders_merged_bindings() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

		// Buffers
		IntBuffer out = IntBuffer.allocate(4);
    	gl.glGenBuffers(4, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);
    	int buffer3 = out.get(2);
    	int buffer4 = out.get(3);

    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(vertShaderY, vertSource2);
		gl.glShaderSource(fragShaderX, fragSource1);
		gl.glShaderSource(fragShaderY, fragSource2);

		// Compile shaders X
		gl.glCompileVKShader(vertShaderX);
		gl.glCompileVKShader(vertShaderY);
		gl.glCompileVKShader(fragShaderX);
		gl.glCompileVKShader(fragShaderY);

		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programX, fragShaderX);

		// Vertex attribs X
		int position = gl.glGetAttribLocation(programX, "inPosition");
		int color = gl.glGetAttribLocation(programX, "inColor");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color, 3*4, 0, false, 5*4, 2*4);


		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programY, fragShaderY);

		int positionY = gl.glGetAttribLocation(programY, "inPosition");
		int normals = gl.glGetAttribLocation(programY, "inNormals");
		int colorY = gl.glGetAttribLocation(programY, "inColor");
		int brightness = gl.glGetAttribLocation(programY, "inBrightness");

		gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(positionY, 2*4, 0, false, 9*4, 5*4);
		gl.glVertexAttribPointer(normals, 3*4, 0, false, 9*4, 7*4);
		gl.glBindBuffer(0, buffer3);
		gl.glVertexAttribPointer(colorY, 3*4, 0, false, 9*4, 10*4);
		gl.glBindBuffer(0, buffer4);
		gl.glVertexAttribPointer(brightness, 1*4, 0, false, 9*4, 11*4);

		// Test X
		VkVertexInputBindingDescription.Buffer descriptions = gl.getPipeline(programX).getBindingDescriptions();

		descriptions = gl.getPipeline(programX).getBindingDescriptions();
		assertEquals(0, descriptions.get(0).binding());

		descriptions = gl.getPipeline(programY).getBindingDescriptions();
		assertEquals(0, descriptions.get(0).binding());
		assertEquals(1, descriptions.get(1).binding());
		assertEquals(2, descriptions.get(2).binding());
	}


	@Test
	public void glprogram_multiple_shaders_same_attribs() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		int programX = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		// Now Y
		int programY = gl.glCreateProgram();
		int vertShaderY = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderY = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

		// Buffers
		IntBuffer out = IntBuffer.allocate(2);
    	gl.glGenBuffers(2, out);
    	int buffer1 = out.get(0);
    	int buffer2 = out.get(1);

    	// Pass source code
		gl.glShaderSource(vertShaderX, vertSource1);
		gl.glShaderSource(vertShaderY, vertSource1_alt);
		gl.glShaderSource(fragShaderX, fragSource1);
		gl.glShaderSource(fragShaderY, fragSource1);

		// Compile shaders X
		gl.glCompileVKShader(vertShaderX);
		gl.glCompileVKShader(vertShaderY);
		gl.glCompileVKShader(fragShaderX);
		gl.glCompileVKShader(fragShaderY);

		// attach to program X
		gl.glAttachShader(programX, vertShaderX);
		gl.glAttachShader(programX, fragShaderX);

		// Vertex attribs X
		int position1 = gl.glGetAttribLocation(programX, "inPosition");
		int color1 = gl.glGetAttribLocation(programX, "inColor");
		gl.glBindBuffer(0, buffer1);
		gl.glVertexAttribPointer(position1, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color1, 3*4, 0, false, 5*4, 2*4);


		gl.glAttachShader(programY, vertShaderY);
		gl.glAttachShader(programY, fragShaderY);
		int position2 = gl.glGetAttribLocation(programY, "inPosition");
		int color2 = gl.glGetAttribLocation(programY, "inColor");
		gl.glBindBuffer(0, buffer2);
		gl.glVertexAttribPointer(position2, 2*4, 0, false, 5*4, 0);
		gl.glVertexAttribPointer(color2, 3*4, 0, false, 5*4, 2*4);



		// Test X
		VkVertexInputAttributeDescription.Buffer descriptions = gl.getPipeline(programX).getAttributeDescriptions();

		assertEquals(0, descriptions.get(0).location());
		assertEquals(1, descriptions.get(1).location());

		descriptions = gl.getPipeline(programY).getAttributeDescriptions();
		assertEquals(2, descriptions.get(0).location());
		assertEquals(3, descriptions.get(1).location());
	}


	@Test
	public void multiple_buffers_1() {
		GL2VK gl = glProgram1(true);
		ArrayList<Long> bindings = gl.getPipeline(glProgram1).getVKBuffers();
		System.out.println("Buffer 1 value: "+bindings.get(0));
		System.out.println("Buffer 2 value: "+bindings.get(1));
		assertNotEquals(-1, bindings.get(0));
		assertNotEquals(-1, bindings.get(1));
		assertNotEquals(0, bindings.get(0));
		assertNotEquals(0, bindings.get(1));
		assertEquals(2, bindings.size());
	}

	@Test
	public void multiple_buffers_2() {
		GL2VK gl = glProgram1(false);
		ArrayList<Long> bindings = gl.getPipeline(glProgram1).getVKBuffers();
		System.out.println("Buffer 1 value: "+bindings.get(0));
		assertNotEquals(-1, bindings.get(0));
		assertEquals(1, bindings.size());
	}

	@Test
	public void multiple_buffers_change() {
		GL2VK gl = glProgram1(false);

		ArrayList<Long> bindings1 = gl.getPipeline(glProgram1).getVKBuffers();
		System.out.println("Buffer 1 value: "+bindings1.get(0));
		long beforeBinding = bindings1.get(0);
		assertNotEquals(-1, bindings1.get(0));
		assertEquals(1, bindings1.size());

		// Change
		gl.glBindBuffer(0, testbuffer1);
		gl.glBufferData(GL2VK.GL_VERTEX_BUFFER, 20, null, 0);
		ArrayList<Long> bindings2 = gl.getPipeline(glProgram1).getVKBuffers();
		assertNotEquals(beforeBinding, bindings2.get(0));
	}


	// Let's test uniforms now!!!!
String uniformVert_1 = """
#version 450

layout(location = 0) out vec3 fragColor;

layout( push_constant ) uniform uStruct
{
  vec2 u_pos;
  float u_time;
} uni;

vec2 positions[3] = vec2[](
    vec2(0.0, -0.5),
    vec2(0.5, 0.5),
    vec2(-0.5, 0.5)
);

vec3 colors[3] = vec3[](
    vec3(1.0, 0.0, 0.0),
    vec3(0.0, 1.0, 0.0),
    vec3(0.0, 0.0, 1.0)
);

void main() {
    gl_Position = vec4(positions[gl_VertexIndex]+uni.u_pos, 0.0, 1.0);
    fragColor = colors[gl_VertexIndex]+vec3(uni.u_time);
}
		""";

String uniformVert_throwOff = """
#version 450

layout(location = 0) out vec3 fragColor;

layout( push_constant ) uniform uniform_struct
{
  vec2 u_pos;
  float u_time;
} uniforms;

vec2 positions[3] = vec2[](
    vec2(0.0, -0.5),
    vec2(0.5, 0.5),
    vec2(-0.5, 0.5)
);

vec3 colors[3] = vec3[](
    vec3(1.0, 0.0, 0.0),
    vec3(0.0, 1.0, 0.0),
    vec3(0.0, 0.0, 1.0)
);

void main() {
    gl_Position = vec4(positions[gl_VertexIndex]+uniforms.u_pos, 0.0, 1.0);
    fragColor = colors[gl_VertexIndex]+vec3(uniforms.u_time);
}
		""";

String uniformFrag = """
#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec3 fragColor;

layout(location = 0) out vec4 outColor;

void main() {
    outColor = vec4(fragColor, 1.0);
}
		""";

// So creative with naming omg
String uniformFrag_uniforms = """
#version 450
#extension GL_ARB_separate_shader_objects : enable

layout( push_constant ) uniform uniform_struct
{
  layout(offset=12) float u_brightness;
  vec4 u_extraColor;
} uniforms;


layout(location = 0) in vec3 fragColor;

layout(location = 0) out vec4 outColor;

void main() {
    outColor = vec4(fragColor+vec3(uniforms.u_brightness), 1.0)+uniforms.u_extraColor;
}
		""";


	private void checkCompiledVertex(GL2VK gl, String code) {
		int vert1 = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		gl.glShaderSource(vert1, code);
		gl.glCompileVKShader(vert1);

		IntBuffer out = IntBuffer.allocate(1);
		gl.glGetShaderiv(vert1, GL2VK.GL_COMPILE_STATUS, out);
		if (out.get(0) != GL2VK.GL_TRUE) {
			System.out.println("compile_uniform_shaders:\n"+gl.glGetShaderInfoLog(vert1));
			fail();
		}
	}

	private void checkCompiledFragment(GL2VK gl, String code) {
		int vert1 = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);
		gl.glShaderSource(vert1, code);
		gl.glCompileVKShader(vert1);

		IntBuffer out = IntBuffer.allocate(1);
		gl.glGetShaderiv(vert1, GL2VK.GL_COMPILE_STATUS, out);
		if (out.get(0) != GL2VK.GL_TRUE) {
			System.out.println("compile_uniform_shaders:\n"+gl.glGetShaderInfoLog(vert1));
			fail();
		}
	}

	@Test
	public void compile_uniform_shaders() {

		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);

		checkCompiledVertex(gl, uniformVert_1);
		checkCompiledVertex(gl, uniformVert_throwOff);
		checkCompiledFragment(gl, uniformFrag);
		checkCompiledFragment(gl, uniformFrag_uniforms);

	}

	private int uniformGLProgram = 0;

	private GL2VK uniformProgram(String vertCode, String fragCode) {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		uniformGLProgram = gl.glCreateProgram();
		int vertShader = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShader = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

		gl.glShaderSource(vertShader, vertCode);
		gl.glShaderSource(fragShader, fragCode);

		gl.glCompileVKShader(vertShader);
		gl.glCompileVKShader(fragShader);

		gl.glAttachShader(uniformGLProgram, vertShader);
		gl.glAttachShader(uniformGLProgram, fragShader);

		gl.glLinkProgram(uniformGLProgram);

		return gl;
	}

	private GL2VK uniformProgram(String vertCode) {
		return uniformProgram(vertCode, uniformFrag);
	}


	@Test
	public void uniform_basic() {
		GL2VK gl = uniformProgram(uniformVert_1);

		int u_pos = gl.getUniformLocation(uniformGLProgram, "u_pos");
		int u_time = gl.getUniformLocation(uniformGLProgram, "u_time");
		assertEquals(1, u_pos);
		assertEquals(2, u_time);
	}

	// Try to throw off the uniform parser
	@Test
	public void uniform_throw_off() {
		GL2VK gl = uniformProgram(uniformVert_throwOff);

		int u_pos = gl.getUniformLocation(uniformGLProgram, "u_pos");
		int u_time = gl.getUniformLocation(uniformGLProgram, "u_time");
		assertEquals(1, u_pos);
		assertEquals(2, u_time);
	}

	@Test
	public void uniform_vertex_fragment() {
		GL2VK gl = uniformProgram(uniformVert_1, uniformFrag_uniforms);

		int u_pos = gl.getUniformLocation(uniformGLProgram, "u_pos");
		int u_time = gl.getUniformLocation(uniformGLProgram, "u_time");
		int u_brightness = gl.getUniformLocation(uniformGLProgram, "u_brightness");
		int u_extraColor = gl.getUniformLocation(uniformGLProgram, "u_extraColor");
		assertEquals(1, u_pos);
		assertEquals(2, u_time);
		assertEquals(3, u_brightness);
		assertEquals(4, u_extraColor);
	}

	@Test
	public void roundToMultiple8_tests() {
		assertEquals(24, Util.roundToMultiple8(22));
		assertEquals(16, Util.roundToMultiple8(15));
		assertEquals(128, Util.roundToMultiple8(121));
		assertEquals(64, Util.roundToMultiple8(60));
		assertEquals(32, Util.roundToMultiple8(32));
	}

	@Test
  @Disabled("Need old vk shader path")
	public void converted_shaders_compile_test() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);

    	// Shader source
    	checkCompiledVertex(gl, Util.readFile("resources/shaders/vkversion/ColorVert.glsl"));
    	checkCompiledFragment(gl, Util.readFile("resources/shaders/vkversion/ColorFrag.glsl"));

	}



	@Test
	public void remove_comments() {
		GL2VKShaderConverter converter = new GL2VKShaderConverter();

		String code =
				"""
#version 450
// Blah blah blah
layout(location = 0) in vec2 inPosition;// position variable
layout(location = 1) in vec3 inColor;// Color variable

// Woah look varying
layout(location = 0) out vec3 fragColor;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);// Lalalalala
fragColor = inColor;// Cool
}
				""";

		String result = converter.removeComments(code).trim().replaceAll(" ", "");

		String expected =
				"""
#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;


layout(location = 0) out vec3 fragColor;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}

				""".trim().replaceAll(" ", "");

		if (!result.equals(expected)) {
			System.out.println("remove_comments actual result: ");
			System.out.println(result);
			System.out.println("\nremove_comments expected: ");
			System.out.println(expected);
			fail();
		}
	}




	@Test
	public void remove_comments_multiline() {
		GL2VKShaderConverter converter = new GL2VKShaderConverter();

		String code =
				"""
#version 450

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-21 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

// Blah blah blah
layout(location = 0) in vec2 inPosition;// position variable
layout(location = 1) in vec3 inColor;// Color variable

// Woah look varying
layout(location = 0) out vec3 fragColor;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);// Lalalalala
fragColor = inColor;// Cool
}
				""";

		String result = converter.removeComments(code).trim().replaceAll("[\\n ]", "");

		String expected =
				"""
#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;


layout(location = 0) out vec3 fragColor;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}

				""".trim().replaceAll("[\\n ]", "");

		if (!result.equals(expected)) {
			System.out.println("remove_comments_multiline actual result: ");
			System.out.println(result);
			System.out.println("\nremove_comments_multiline expected: ");
			System.out.println(expected);
			fail();
		}
	}








	@Test
	public void append_version() {
		GL2VKShaderConverter converter = new GL2VKShaderConverter();
		String code =
				"""
// Hello look at this source code

void main() {
// I've deliberately cut out some variables so this won't compile lol
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}
				""";

		String expected =
				"""
#version 450

void main() {

gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}
				""".trim().replaceAll(" ", "").replaceAll("\n", "");

		// Let's test removeComments again shall we?
		code = converter.removeComments(code);
		code = converter.appendVersion(code).trim().replaceAll(" ", "").replaceAll("\n", "");

		if (!code.equals(expected)) {
			System.out.println("append_version actual result: ");
			System.out.println(code);
			System.out.println("\nappend_version expected: ");
			System.out.println(expected);
			fail();
		}
	}




	@Test
	public void attribute_to_in() {
		GL2VKShaderConverter converter = new GL2VKShaderConverter();
		String code =
				"""

attribute vec2 inPosition;
attribute vec3 inColor;


layout(location = 0) out vec3 fragColor;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;// Cool
}
				""";

		code = converter.removeComments(code);
		code = converter.appendVersion(code);
		code = converter.attribute2In(code);

		String expected =
				"""
#version 450

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec3 inColor;


layout(location = 0) out vec3 fragColor;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}

				""";

		if (!code.trim().replaceAll(" ", "").equals(expected.trim().replaceAll(" ", ""))) {
			System.out.println("attribute_to_in actual result: ");
			System.out.println(code);
			System.out.println("\nattribute_to_in expected: ");
			System.out.println(expected);
			fail();
		}
	}


	@Test
	public void varying_to_out() {
		GL2VKShaderConverter converter = new GL2VKShaderConverter();
		String code =
				"""

varying vec3 fragColor;
varying vec2 texUv;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}
				""";

		code = converter.removeComments(code);
		code = converter.appendVersion(code);
		code = converter.attribute2In(code);
		code = converter.varying2Out(code);

		String expected =
				"""
#version 450

layout(location = 0) out vec3 fragColor;
layout(location = 1) out vec2 texUv;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}

				""";

		if (!code.trim().replaceAll(" ", "").equals(expected.trim().replaceAll(" ", ""))) {
			System.out.println("varying_to_out actual result: ");
			System.out.println(code);
			System.out.println("\nvarying_to_out expected: ");
			System.out.println(expected);
			fail();
		}

		assertEquals(0, converter.vertexVaryingLocations.get("fragColor"));
		assertEquals(1, converter.vertexVaryingLocations.get("texUv"));
	}





	@Test
	public void varying_to_in() {
		// First we need the vertex cache
		GL2VKShaderConverter converter = new GL2VKShaderConverter();
		String vertex =
				"""

varying vec3 fragColor;
varying vec2 texUv;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}
				""";

		converter.varying2Out(vertex);

		// Now for the actual test
		String code =
				"""

varying vec3 fragColor;
varying vec2 texUv;

void main() {
  gl_FragColor = fragColor;
}
				""";

		code = converter.removeComments(code);
		code = converter.appendVersion(code);
		code = converter.attribute2In(code);
		code = converter.varying2In(code);

		String expected =
				"""
#version 450

layout(location = 0) in vec3 fragColor;
layout(location = 1) in vec2 texUv;

void main() {
  gl_FragColor = fragColor;
}
				""";

		if (!code.trim().replaceAll(" ", "").equals(expected.trim().replaceAll(" ", ""))) {
			System.out.println("varying_to_in actual result: ");
			System.out.println(code);
			System.out.println("\nvarying_to_in expected: ");
			System.out.println(expected);
			fail();
		}
	}





	// Expect a thrown exception because we did not convert a vertex shader.
	@Disabled("Disabled due to using a feature that was removed, which now causes the program to exit.")
	@Test
	public void varying_to_in_novertex() {
		// First we need the vertex cache
		GL2VKShaderConverter converter = new GL2VKShaderConverter();

		// Now for the actual test
		String code =
				"""

varying vec3 fragColor;
varying vec2 texUv;

void main() {
  gl_FragColor = fragColor;
}
				""";

		code = converter.removeComments(code);
		code = converter.appendVersion(code);
		code = converter.attribute2In(code);

		try {
			converter.varying2In(code);
			fail();
		}
		catch (RuntimeException e) {
			// Expect exception
		}
	}


	@Test
	public void uniforms_into_block() {
		// First we need the vertex cache
		GL2VKShaderConverter converter = new GL2VKShaderConverter();

		// Now for the actual test
		String code =
				"""
uniform mat4 modelviewMatrix;
uniform mat4 transformMatrix;
uniform mat3 normalMatrix;

uniform int lightCount;

void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}
				""";

		code = converter.convertUniforms(code, 1);
		code = converter.appendVersion(code);

		String expected =
				"""
#version 450
layout( push_constant ) uniform gltovkuniforms_struct {
    mat4 modelviewMatrix;
    mat4 transformMatrix;
    mat3 normalMatrix;
    int lightCount;
} gltovkuniforms;


void main() {
gl_Position = vec4(inPosition, 0.0, 1.0);
fragColor = inColor;
}
				""";

		if (!code.trim().replaceAll("\n", "").replaceAll(" ", "").equals(expected.trim().replaceAll("\n", "").replaceAll(" ", ""))) {
			System.out.println("uniforms_into_block actual result: ");
			System.out.println(code);
			System.out.println("\nuniforms_into_block expected: ");
			System.out.println(expected);
			fail();
		}
	}



	@Test
	public void uniform_conversion() {
		// First we need the vertex cache
		GL2VKShaderConverter converter = new GL2VKShaderConverter();

		// Now for the actual test
		String code =
				"""
uniform vec2 u_pos;
uniform vec2 u_pos_secondary;
uniform float u_r;
uniform float u_g;
uniform float u_b;

void main() {
    gl_Position = vec4(inPosition+u_pos+u_pos_secondary, 0.0, 1.0);
    fragColor += inColor*vec3(u_r, u_g, u_b);
}
				""";

		code = converter.convertUniforms(code, 1);
		code = converter.appendVersion(code);

		String expected =
				"""
#version 450
layout( push_constant ) uniform gltovkuniforms_struct
{
  vec2 u_pos;
  vec2 u_pos_secondary;
  float u_r;
  float u_g;
  float u_b;
} gltovkuniforms;

void main() {
    gl_Position = vec4(inPosition+gltovkuniforms.u_pos+gltovkuniforms.u_pos_secondary, 0.0, 1.0);
    fragColor += inColor*vec3(gltovkuniforms.u_r, gltovkuniforms.u_g, gltovkuniforms.u_b);
}
				""";

		if (!code.trim().replaceAll("\n", "").replaceAll(" ", "").equals(expected.trim().replaceAll("\n", "").replaceAll(" ", ""))) {
			System.out.println("uniform_conversion actual result: ");
			System.out.println(code);
			System.out.println("\nuniform_conversion expected: ");
			System.out.println(expected);
			fail();
		}
		System.out.println("uniform_conversion actual result: ");
		System.out.println(code);
		System.out.println("\nuniform_conversion expected: ");
		System.out.println(expected);

		assertEquals(28, converter.vertUniformSize);
	}







	// Expect a thrown exception because we did not convert a vertex shader.
	@Disabled("Disabled due to using a feature that was removed, which now causes the program to exit.")
	@Test
	public void uniforms_into_block_novertex() {
		// First we need the vertex cache
		GL2VKShaderConverter converter = new GL2VKShaderConverter();

		// Now for the actual test
		String code =
				"""
uniform vec2 u_pos;
uniform vec2 u_pos_secondary;
uniform float u_r;
uniform float u_g;
uniform float u_b;

void main() {
	gl_FragColor = fragColor;
}
				""";


		try {
			// 2 here cus we usin' fragment
			code = converter.convertUniforms(code, 2);
			fail();
		}
		catch (RuntimeException e) {
			// Expect exception
		}
	}





	@Test
	public void uniform_conversion_fragment() {
		GL2VKShaderConverter converter = new GL2VKShaderConverter();

		// First vertex code
		String vertex =
				"""
uniform vec2 u_pos;
uniform vec2 u_pos_secondary;
uniform float u_r;
uniform float u_g;
uniform float u_b;

void main() {
    gl_Position = vec4(inPosition+u_pos+u_pos_secondary, 0.0, 1.0);
    fragColor = inColor*vec3(u_r, u_g, u_b);
}
				""";

		vertex = converter.convertUniforms(vertex, 1);
		vertex = converter.appendVersion(vertex);

		// Now for the fragment
		String code =
				"""
uniform vec2 u_cool;
uniform vec2 u_someuniform;

void main() {
	gl_FragColor = fragColor;
}
				""";

		code = converter.convertUniforms(code, 2);
		code = converter.appendVersion(code);

		String expected =
				"""
#version 450
layout( push_constant ) uniform gltovkuniforms_struct {
    layout( offset=28 ) vec2 u_cool;
    vec2 u_someuniform;
} gltovkuniforms;


void main() {
    gl_FragColor = fragColor;
}
				""";

		if (!code.trim().replaceAll("\n", "").replaceAll(" ", "").equals(expected.trim().replaceAll("\n", "").replaceAll(" ", ""))) {
			System.out.println("uniform_conversion_fragment actual result: ");
			System.out.println(code);
			System.out.println("\nuniform_conversion_fragment expected: ");
			System.out.println(expected);
			fail();
		}
	}


	@Test
	public void space_out_symbols() {
		String testString = "x+=x+x==x=x/=4.2/0.9*=egg.x*egg.y";
		String expected = "x += x + x == x = x /= 4.2 / 0.9 *= egg .x * egg .y";

		String actual = GL2VKShaderConverter.spaceOutSymbols(testString);

		if (!actual.equals(expected)) {
			System.out.println("space_out_symbols actual result: ");
			System.out.println(actual);
			System.out.println("\nspace_out_symbols expected: ");
			System.out.println(expected);
			fail();
		}
	}





	@Test
	public void replace_frag_out() {
		GL2VKShaderConverter converter = new GL2VKShaderConverter();

		String code =
				"""
void main() {
    gl_FragColor = fragColor;
}
""";

		String expected =
				"""
#version 450
layout(location = 0) out vec4 gl2vk_FragColor;

void main() {
    gl2vk_FragColor = fragColor;
}
""";

		code = converter.replaceFragOut(code);
		code = converter.appendVersion(code);


		if (!code.trim().replaceAll("\n", "").replaceAll(" ", "").equals(expected.trim().replaceAll("\n", "").replaceAll(" ", ""))) {
			System.out.println("replace_frag_out actual result: ");
			System.out.println(code);
			System.out.println("\nreplace_frag_out expected: ");
			System.out.println(expected);
			fail();
		}
	}



	@Test
  public void invert_y() {
    GL2VKShaderConverter converter = new GL2VKShaderConverter();

    String code =
        """
void main() {

    gl_Position = transformMatrix * position;

    // Comment

    fragColor = inColor;
}
        """;

    String expected =
        """
#version 450
void main() {

    gl_Position = transformMatrix * position;



    fragColor = inColor;
    gl_Position.y *= -1.0;
}
""";

    code = converter.removeComments(code);
    code = converter.invertY(code);
    code = converter.appendVersion(code);


    if (!code.trim().replaceAll("\n", "").replaceAll(" ", "").equals(expected.trim().replaceAll("\n", "").replaceAll(" ", ""))) {
      System.out.println("invert_y actual result: ");
      System.out.println(code);
      System.out.println("\ninvert_y expected: ");
      System.out.println(expected);
      fail();
    }
  }













	// Now for the big test.
	// Let's convert a processing shader.
String processingColorVert = """
		/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-21 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

uniform mat4 transformMatrix;

attribute vec4 position;
attribute vec4 color;

varying vec4 vertColor;

void main() {
  gl_Position = transformMatrix * position;

  vertColor = color;
}
		""";

String processingColorFrag = """
/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-21 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

varying vec4 vertColor;

void main() {
  gl_FragColor = vertColor;
}
		""";

String processingColorVertExpected = """
#version 450

layout( push_constant ) uniform gltovkuniforms_struct
{
  mat4 transformMatrix;
} gltovkuniforms;


layout(location = 0) in vec4 position;
layout(location = 1) in vec4 color;

layout(location = 0) out vec4 vertColor;

void main() {
  gl_Position = gltovkuniforms.transformMatrix * position;

  vertColor = color;
  gl_Position.y *= -1.0;
}
		""";

String processingColorFragExpected = """
#version 450
layout(location = 0) out vec4 gl2vk_FragColor;

#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

layout(location = 0) in vec4 vertColor;


void main() {
  gl2vk_FragColor = vertColor;
}
		""";


	@Test
	public void convert_vert() {
		GL2VKShaderConverter converter = new GL2VKShaderConverter();


		String vertCode = converter.convert(processingColorVert, 1);


		if (!vertCode.replaceAll("[\\t\\n ]", "").equals(processingColorVertExpected.replaceAll("[\\t\\n ]", ""))) {
			System.out.println("convert_1_vert actual result: ");
			System.out.println(vertCode);
			System.out.println("\nconvert_1_vert expected: ");
			System.out.println(processingColorVertExpected);
			fail();
		}
	}



	@Test
	public void convert_frag() {
		GL2VKShaderConverter converter = new GL2VKShaderConverter();


		converter.convert(processingColorVert, 1);

		String fragCode = converter.convert(processingColorFrag, 2);


		if (!fragCode.replaceAll("[\\t\\n ]", "").equals(processingColorFragExpected.replaceAll("[\\t\\n ]", ""))) {
			System.out.println("convert_1_frag actual result: ");
			System.out.println(fragCode);
			System.out.println("\nconvert_1_frag expected: ");
			System.out.println(processingColorFragExpected);
			fail();
		} else {

		System.out.println("convert_1_frag actual result: ");
		System.out.println(fragCode);
		System.out.println("\nconvert_1_frag expected: ");
		System.out.println(processingColorFragExpected);

		}
	}



	// Now for the ultimate test:
	// Fittingly test #50 (maybe)
	// Convert and compile a processing shader in openGL GLSL code.
	@Test
	public void convert_and_compile() {
		GL2VK gl = new GL2VK(GL2VK.DEBUG_MODE);
		glProgram1 = gl.glCreateProgram();
		int vertShaderX = gl.glCreateShader(GL2VK.GL_VERTEX_SHADER);
		int fragShaderX = gl.glCreateShader(GL2VK.GL_FRAGMENT_SHADER);

    	// Pass source code
		gl.glShaderSource(vertShaderX, processingColorVert);
		gl.glShaderSource(fragShaderX, processingColorFrag);

		gl.glCompileShader(vertShaderX);
		gl.glCompileShader(fragShaderX);

		IntBuffer out1 = IntBuffer.allocate(1);
		gl.glGetShaderiv(vertShaderX, GL2VK.GL_COMPILE_STATUS, out1);
		IntBuffer out2 = IntBuffer.allocate(1);
		gl.glGetShaderiv(fragShaderX, GL2VK.GL_COMPILE_STATUS, out2);

		assertEquals(1, out1.get(0));
		assertEquals(1, out2.get(0));
	}





	// TODO: test using crazyAttribsCode.

	// TODO: test many bindings
	// with a glprogram with many bindings.

	// TODO: Test source code with comments that have keywords like
	// vec3 and mat4.
}