package processing.GL2VK;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_BACK_BIT;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_LOGIC_OP_COPY;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkOffset2D;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;

import processing.GL2VK.ShaderSPIRVUtils.SPIRV;


// Buffer bindings in opengl look like this
// buffer 6
// buffer 7
// buffer 8
// Let's say we call
//  bindBuffer(6)
//  vertexAttribPointer(...)
//  bindBuffer(5)
//  vertexAttribPointer(...)
//  bindBuffer(8)
//  vertexAttribPointer(...)
// The question is, what would vulkan's bindings be?
// ...
// Whenever we bind a buffer, we need to add it to a list along
// with the actual buffer so we can later use it to vkcmdBindVertexArrays()
// so
// bindBuffer(6)
// enableVertexArrays()
// vertexAttribPointer(...)
// - vkbuffer[0] = buffer(6)
// - Create VertexAttribsBinding with binding 0
//
// bindBuffer(5)
// vertexAttribPointer(...)
// - vkbuffer[1] = buffer(5)
// - Create VertexAttribsBinding with binding 1
//
// bindBuffer(8)
// vertexAttribPointer(...)
// - vkbuffer[2] = buffer(8)
// - Create VertexAttribsBinding with binding 2
//
// We don't care what our vkbindings are, as long as it's in the correct order
// as we're passing the lists in when we call vkCmdBindVertexArrays().

// TODO: Properly fix up compileshaders
// TODO: Turn gl2vkBinding to buffer pointers so that we can use it in vkCmdBindVertexBuffers.
// It's very simple, when you call drawArrays in GL2VK, simply pass a list of buffers[].bufferID
// to the command.
// Maybe not so simple, because challenge to overcome: we need to somehow pass that big ol array
// through atomicLongs.

public class GL2VKPipeline {

	private VulkanSystem system;
	private VKSetup vkbase;

	public SPIRV vertShaderSPIRV = null;
	public SPIRV fragShaderSPIRV = null;

	// Public for use with push constants.
    public long pipelineLayout = -1;
    public long graphicsPipeline = -1;

    // We assign the offsets when we add the uniforms to the pipeline,
    // NOT during source code parsing (check notes in the ShaderAttribInfo.parseUniforms() method)
    private int currUniformOffset = 0;

	public ShaderAttribInfo attribInfo = null;

	private HashMap<Integer, VertexAttribsBinding> gl2vkBinding = new HashMap<>();
	private HashMap<String, Integer> attribNameToGLLocation = new HashMap<>();
	private int[] GLLocationToVKLocation = new int[1024];
	private HashMap<String, Integer> name2UniformLocation = new HashMap<>();
	public ArrayList<GLUniform> uniforms = new ArrayList<>();

	private int boundBinding = 0;
	private int totalVertexAttribsBindings = 0;

	// TODO: replace with checking the hashState instead
	public boolean initiated = false;



	public GL2VKPipeline(VulkanSystem system) {
    	this.system = system;
    	this.vkbase = system.vkbase;
	}

	// Only use this constructor for testing purposes
	public GL2VKPipeline() {
	}

	// Same from the tutorial
    private long createShaderModule(ByteBuffer spirvCode) {
        try(MemoryStack stack = stackPush()) {

            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);

            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(spirvCode);

            LongBuffer pShaderModule = stack.mallocLong(1);

            if(vkCreateShaderModule(vkbase.device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create shader module");
            }

            return pShaderModule.get(0);
        }
    }





    public void createGraphicsPipeline() {

        try(MemoryStack stack = stackPush()) {

            // Let's compile the GLSL shaders into SPIR-V at runtime using the shaderc library
            // Check ShaderSPIRVUtils class to see how it can be done
        	if (vertShaderSPIRV == null || fragShaderSPIRV == null) {
        		throw new RuntimeException("Shaders must be compiled before calling createGraphicsPipeline()");
        	}

        	// Completely unnecessary code
    		long vertShaderModule = createShaderModule(vertShaderSPIRV.bytecode());
    		long fragShaderModule = createShaderModule(fragShaderSPIRV.bytecode());


//            long vertShaderModule = createShaderModule(vertShaderSPIRV.bytecode());
//            long fragShaderModule = createShaderModule(fragShaderSPIRV.bytecode());

            ByteBuffer entryPoint = stack.UTF8("main");

            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);

            VkPipelineShaderStageCreateInfo vertShaderStageInfo = shaderStages.get(0);

            vertShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            vertShaderStageInfo.stage(VK_SHADER_STAGE_VERTEX_BIT);
            vertShaderStageInfo.module(vertShaderModule);
            vertShaderStageInfo.pName(entryPoint);

            VkPipelineShaderStageCreateInfo fragShaderStageInfo = shaderStages.get(1);

            fragShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            fragShaderStageInfo.stage(VK_SHADER_STAGE_FRAGMENT_BIT);
            fragShaderStageInfo.module(fragShaderModule);
            fragShaderStageInfo.pName(entryPoint);

            // ===> VERTEX STAGE <===

            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack);
            vertexInputInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            vertexInputInfo.pVertexBindingDescriptions(getBindingDescriptions());
            vertexInputInfo.pVertexAttributeDescriptions(getAttributeDescriptions());

            // ===> ASSEMBLY STAGE <===

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
            inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
            inputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            inputAssembly.primitiveRestartEnable(false);

            // ===> VIEWPORT & SCISSOR

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.x(0.0f);
            viewport.y(0.0f);
            viewport.width(vkbase.swapChainExtent.width());
            viewport.height(vkbase.swapChainExtent.height());

            viewport.minDepth(0.0f);
            viewport.maxDepth(1.0f);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.offset(VkOffset2D.calloc(stack).set(0, 0));
            scissor.extent(vkbase.swapChainExtent);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack);
            viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
            viewportState.pViewports(viewport);
            viewportState.pScissors(scissor);

            // ===> RASTERIZATION STAGE <===

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack);
            rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
            rasterizer.depthClampEnable(false);
            rasterizer.rasterizerDiscardEnable(false);
            rasterizer.polygonMode(VK_POLYGON_MODE_FILL);
            rasterizer.lineWidth(1.0f);
            rasterizer.cullMode(VK_CULL_MODE_BACK_BIT);
            rasterizer.frontFace(VK_FRONT_FACE_CLOCKWISE);
            rasterizer.depthBiasEnable(false);

            // ===> MULTISAMPLING <===

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack);
            multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
            multisampling.sampleShadingEnable(false);
            multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            // ===> COLOR BLENDING <===

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            colorBlendAttachment.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
            colorBlendAttachment.blendEnable(false);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack);
            colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
            colorBlending.logicOpEnable(false);
            colorBlending.logicOp(VK_LOGIC_OP_COPY);
            colorBlending.pAttachments(colorBlendAttachment);
            colorBlending.blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

            // ===> PIPELINE LAYOUT CREATION <===

            // PUSH CONSTANTS
            int vertexSize = 0;
            int fragmentSize = 0;

            // Need these just in case the offset is set to something stupidly high
            int maxOffsettedSizeVertex = 0;
            int maxOffsettedSizeFragment = 0;
            // Now we must compile our uniforms list into this pushConstants thing
            // All we really need to do is set the size.
            for (GLUniform uni : uniforms) {
            	if (uni.vertexFragment == GLUniform.VERTEX) {
            		vertexSize += uni.size;
            		if (uni.offset+uni.size > maxOffsettedSizeVertex)
            			maxOffsettedSizeVertex = uni.offset+uni.size;
            	}
            	if (uni.vertexFragment == GLUniform.FRAGMENT) {
            		fragmentSize += uni.size;
            		if (uni.offset+uni.size > maxOffsettedSizeFragment)
            			maxOffsettedSizeFragment = uni.offset+uni.size;
            	}
            }

            if (maxOffsettedSizeVertex > vertexSize) vertexSize = maxOffsettedSizeVertex;
            if (maxOffsettedSizeFragment > fragmentSize) fragmentSize = maxOffsettedSizeFragment;


//            vertexSize = Util.roundToMultiple8(vertexSize);
//            System.out.println("VERTEX PUSHCONSTANT SIZE "+vertexSize);


            if (vertexSize+fragmentSize > system.getPushConstantsSizeLimit()) {
            	int size = system.getPushConstantsSizeLimit();
            	Util.emergencyExit(
            			"Uniform variables totals up to a size greater than the push constant limit of "+size+" bytes",
            			"on this gpu.",
            			"Unfortunately, uniform sizes greater than "+size+" bytes is not supported yet.",
            			"The only solution for now is to remove some of the uniforms in your shader",
            			"(both vertex and fragment) to reduce uniform size, I'm sorry :("
    			);
            	// Program will exit after this.
            }

        	// Here, for each uniform, we specify a pushConstant
            int numBlocks = 0;
            if (vertexSize > 0) numBlocks++;
            if (fragmentSize > 0) numBlocks++;
            VkPushConstantRange.Buffer pushConstants = VkPushConstantRange.calloc(numBlocks, stack);

            // 0: vertex
            if (vertexSize > 0) {
	            pushConstants.get(0).offset(0);
	            pushConstants.get(0).size(vertexSize);
	            pushConstants.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            }

            // No fragment variables? Forget about push constants
            // 1: fragment
            if (fragmentSize > 0) {
	            pushConstants.get(1).offset(vertexSize);
	            pushConstants.get(1).size(fragmentSize);
	            pushConstants.get(1).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            }


            // TODO: Remove
//            for (int i = 0; i < uniforms.size(); i++) {
//            	GLUniform uni = uniforms.get(i);
//                pushConstants.get(i).offset(uni.offset);
//                pushConstants.get(i).size(uni.size);
//                pushConstants.get(i).stageFlags(uni.getVkType());
//
//                System.out.println("Offset: "+uni.offset);
//                System.out.println("Size: "+uni.size);
//                System.out.println("Type: "+uni.getVkType());
//            }



            // Now pipeline layout info (only 1)
            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack);
            pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);

            if (numBlocks > 0) {
            	pipelineLayoutInfo.pPushConstantRanges(pushConstants);
            }

            LongBuffer pPipelineLayout = stack.longs(VK_NULL_HANDLE);

            if(vkCreatePipelineLayout(vkbase.device, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create pipeline layout");
            }

            pipelineLayout = pPipelineLayout.get(0);

            /////////////////////////////////////////////////////////////////////////////////
            /////////////////////////////////////////////////////////////////////////////////



            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
            pipelineInfo.pStages(shaderStages);
            pipelineInfo.pVertexInputState(vertexInputInfo);
            pipelineInfo.pInputAssemblyState(inputAssembly);
            pipelineInfo.pViewportState(viewportState);
            pipelineInfo.pRasterizationState(rasterizer);
            pipelineInfo.pMultisampleState(multisampling);
            pipelineInfo.pColorBlendState(colorBlending);
            pipelineInfo.layout(pipelineLayout);
            pipelineInfo.renderPass(system.renderPass);
            pipelineInfo.subpass(0);
            pipelineInfo.basePipelineHandle(VK_NULL_HANDLE);
            pipelineInfo.basePipelineIndex(-1);

            LongBuffer pGraphicsPipeline = stack.mallocLong(1);

            if(vkCreateGraphicsPipelines(vkbase.device, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create graphics pipeline");
            }

            graphicsPipeline = pGraphicsPipeline.get(0);


            // ===> RELEASE RESOURCES <===
            vertShaderSPIRV.free();
            fragShaderSPIRV.free();

    		vkDestroyShaderModule(vkbase.device, vertShaderModule, null);
    		vkDestroyShaderModule(vkbase.device, fragShaderModule, null);

            vertShaderModule = -1;
            fragShaderModule = -1;

            initiated = true;
        }
    }

    public VkVertexInputBindingDescription.Buffer getBindingDescriptions() {
		VkVertexInputBindingDescription.Buffer bindingDescriptions =
		VkVertexInputBindingDescription.calloc(gl2vkBinding.size());

		int i = 0;
    	for (VertexAttribsBinding vab : gl2vkBinding.values()) {
    		vab.updateBindingDescription(bindingDescriptions.get(i++));
    	}

    	return bindingDescriptions.rewind();
    }


    public VkVertexInputAttributeDescription.Buffer getAttributeDescriptions() {
  		VkVertexInputAttributeDescription.Buffer attributeDescriptions =
  		VkVertexInputAttributeDescription.calloc(attribInfo.nameToLocation.size());

  		int i = 0;
      	for (VertexAttribsBinding vab : gl2vkBinding.values()) {
      		vab.updateAttributeDescriptions(attributeDescriptions, i);
      		i += vab.getSize();
      	}

  		return attributeDescriptions;
    }

    public int getHashState() {
    	int hash = 0;
    	// TODO: Hash vertex and fragment shader code.
    	for (VertexAttribsBinding vab : gl2vkBinding.values()) {
    		hash += vab.getHashState();
    	}
    	return hash;
    }

    // Used when glBindBuffer is called, so that we know to create a new binding
    // for our vertexattribs vulkan pipeline. This should be called in glVertexAttribPointer
    // function.
    public void bind(int glIndex, GraphicsBuffer buffer) {
    	boundBinding = glIndex;
    	// Automatically allocate new binding and increate binding count by one.
    	if (!gl2vkBinding.containsKey(boundBinding)) {
        	gl2vkBinding.put(glIndex, new VertexAttribsBinding(totalVertexAttribsBindings++, attribInfo));
    	}
    	// It all flowssss. In a very complicated, spaghetti'd way.
    	// Tell me a better way to do it though.
    	// Technically the actual buffer can change during the pipeline so
    	// allow that to be dyncamically updated.
    	gl2vkBinding.get(glIndex).buffer = buffer;
    }

    // This should only be used with debug mode
    public void bind(int glIndex) {
    	bind(glIndex, null);
    }

    // Create global variable so it can be cached and hence avoiding garbage collection
    private ArrayList<Long> bufferArray = new ArrayList<>();

    // This is a pretty bad solution, but efficient.
    // Loop through the list, making sure the position in the array
    // aligns with the myBinding value in VertexAttribsBinding.
    // We just naively add an item if the list isn't big enough
    public ArrayList<Long> getVKBuffers() {
    	for (VertexAttribsBinding binding : gl2vkBinding.values()) {
    		while (binding.myBinding > bufferArray.size()-1) {
    			bufferArray.add(0L);
    		}
    		bufferArray.set(binding.myBinding, binding.buffer.getCurrBuffer());
    	}

    	return bufferArray;
    }

    public void vertexAttribPointer(int vklocation, int count, int type, boolean normalized, int stride, int offset) {
    	// Remember, a gl buffer binding of 0 means no bound buffer,
    	// and by default in this class, means bind() hasn't been called.
    	if (boundBinding == 0) {
    		System.err.println("BUG WARNING  vertexAttribPointer called with no bound buffer.");
    		return;
    	}
    	gl2vkBinding.get(boundBinding).vertexAttribPointer(vklocation, count, type, normalized, stride, offset);
    }

    // Not actually used but cool to have
    public void vertexAttribPointer(int location) {
    	gl2vkBinding.get(boundBinding).vertexAttribPointer(location);
    }

    public int addAttribInfo(ShaderAttribInfo attribInfo, int startIndex) {
    	this.attribInfo = attribInfo;
        // Of course we need a way to get our locations from the name.
    	int count = 0;
        for (Entry<String, Integer> entry : attribInfo.nameToLocation.entrySet()) {
        	attribNameToGLLocation.put(entry.getKey(), startIndex);
        	GLLocationToVKLocation[startIndex] = entry.getValue();
        	startIndex++;
        	count++;
        }

        // Returns the iterations
        return count;
    }

    public int getGLAttribLocation(String name) {
    	if (!attribNameToGLLocation.containsKey(name)) return -1;
    	return attribNameToGLLocation.get(name);
    }

    public int getVKAttribLocation(int glAttribLocation) {
    	return GLLocationToVKLocation[glAttribLocation];
    }

    public void addUniforms(ArrayList<GLUniform> uniforms) {
        for (GLUniform uniform : uniforms) {
        	// Assign offset to the uniform
        	// But only if it hasn't been manually assigned already
        	// (via the layout(offset=...) token in the shader).
        	if (uniform.offset == -1) {
	        	uniform.offset = currUniformOffset;
        	}
        	else {
        		currUniformOffset = uniform.offset;
        	}
        	currUniformOffset += uniform.size;


        	// +1 because opengl objects start at index 1.
        	// You'll need to remember to sub one whenever you access this
        	// uniform arrayList.
        	this.uniforms.add(uniform);
        	name2UniformLocation.put(uniform.name, this.uniforms.size());
        }
    }

    // Remember we start from 1, not 0.
    public GLUniform getUniform(int index) {
    	return uniforms.get(index-1);
    }

    public int getUniformLocation(String name) {
    	if (!name2UniformLocation.containsKey(name)) return -1;
    	return name2UniformLocation.get(name);
    }

    // Depricated but leave these in for the unit tests
	public void compileVertex(String source) {
	//      vertShaderSPIRV = compileShaderFile("resources/shaders/09_shader_base.vert", VERTEX_SHADER);
	  attribInfo = new ShaderAttribInfo(source);
	}

	public void compileFragment(String source) {
//    	  fragShaderSPIRV = compileShaderFile("resources/shaders/09_shader_base.frag", FRAGMENT_SHADER);
	}


    public void clean() {
        vkDestroyPipeline(system.device, graphicsPipeline, null);
        vkDestroyPipelineLayout(system.device, pipelineLayout, null);
    }
}