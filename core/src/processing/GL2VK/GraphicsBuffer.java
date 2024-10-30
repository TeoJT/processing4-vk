package processing.GL2VK;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.IntBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;


//     ChatGPT response to an optimised buffering approach:
//Ah, I see what you're asking now! In Vulkan, you cannot directly "buffer" data during rendering within a VkRenderPass instance in the same way you might in some higher-level graphics APIs. However, you can achieve similar functionality through the use of dynamic buffers and descriptor sets. Here’s how that works:
//
//Dynamic Buffers in Vulkan
//Dynamic Vertex Buffers:
//
//You can create a vertex buffer that allows for dynamic updates.
//Use buffer updates (e.g., vkMapMemory followed by writing data) to change the contents of the buffer.
//Using Descriptor Sets:
//
//Create a descriptor set that points to the buffer.
//Update the descriptor set as needed during rendering.
//Steps to Buffer Data Dynamically
//Create Dynamic Buffers:
//
//When creating your vertex buffer, allocate enough space to allow for dynamic updates.
//Use VK_BUFFER_USAGE_VERTEX_BUFFER_BIT and VK_BUFFER_USAGE_TRANSFER_DST_BIT when creating the buffer.
//Map Buffer Memory:
//
//Use vkMapMemory to access the buffer memory and write your vertex data into it.
//void* data;
//vkMapMemory(device, vertexBufferMemory, 0, bufferSize, 0, &data);
//memcpy(data, newVertexData, sizeof(newVertexData));
//vkUnmapMemory(device, vertexBufferMemory);
//Update Descriptor Sets:
//
//If using descriptor sets, update them to point to the new data.
//Call vkUpdateDescriptorSets to update the descriptor set with new buffer information.
//Bind Buffers and Draw:
//
//In your command buffer, bind the vertex buffer and draw as usual.
//vkCmdBindVertexBuffers(commandBuffer, 0, 1, &vertexBuffer, offsets);
//vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0);
//Considerations
//Performance: Dynamic updates to buffers during rendering can impact performance. It's usually better to batch updates and minimize the frequency of changes if possible.
//Synchronization: Be cautious of synchronization issues when updating buffers while rendering. Use fences and semaphores appropriately.
//Conclusion
//While you cannot directly buffer data inside a VkRenderPass instance, you can achieve dynamic updates to your vertex data by using dynamic buffers and updating descriptor sets appropriately. This allows for flexible rendering scenarios where vertex data can change during the rendering process.
//
//If you have any more questions or need clarification on specific parts, feel free to ask!



public class GraphicsBuffer {

    public long bufferID = -1;
    private long bufferMemoryID = -1;
    private boolean bufferAssigned = false;
    private int bufferSize = 0;
    private long stagingBuffer = -1;
    private long stagingBufferMemory = -1;

    private VulkanSystem system;
    private VKSetup vkbase;

    public GraphicsBuffer(VulkanSystem system) {
    	this.system = system;
    	this.vkbase = system.vkbase;
    }

    // Debug mode constructor
    public GraphicsBuffer() {
    }


    // Releases any previous buffers and creates a buffer IF
    // - There's no previous buffer
    // - Buffer size != new size.
    public void createBufferAuto(int size, int usage) {
    	if (!bufferAssigned || size > bufferSize) {
    		// Delete old buffers
    		destroy();
    		// Create new one
    		createBuffer(size, usage);
    	}
    }

    public static int bufferCount = 0;


    // Creates a buffer without allocating any data
    public void createBuffer(int size, int usage) {
    	// If in debug mode, just assign a dummy value
    	if (system == null) {
    		this.bufferID = (long)(Math.random()*100000.);

    		return;
    	}

    	try(MemoryStack stack = stackPush()) {

    		// Not from anywhere, just alloc pointers we can use
    		// to get back from createbuffer method
            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pBufferMemory = stack.mallocLong(1);


            // Actually create our buffer.
            vkbase.createBuffer(size,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT | usage,
                    VK_MEMORY_HEAP_DEVICE_LOCAL_BIT,
                    pBuffer,
                    pBufferMemory);

            // Pointer variables now populated

            // GraphicsBuffedr object, set with our new pointer variables.
            this.bufferID = pBuffer.get(0);
            this.bufferMemoryID = pBufferMemory.get(0);
            this.bufferAssigned = true;
            this.bufferSize = size;

            // STAGING BUFFER
	        pBuffer = stack.mallocLong(1);
	        pBufferMemory = stack.mallocLong(1);
	        vkbase.createBuffer(size,
	                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
	                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
	                pBuffer,
	                pBufferMemory);

	        this.stagingBuffer = pBuffer.get(0);
	        this.stagingBufferMemory = pBufferMemory.get(0);

    	}
    	bufferCount++;
    }

    public void destroy() {
    	// If debug mode enabled
    	if (system == null) return;

    	if (bufferID != -1 && bufferMemoryID != -1) {
	        vkDestroyBuffer(system.device, bufferID, null);
	        vkFreeMemory(system.device, bufferMemoryID, null);
	        vkDestroyBuffer(system.device, stagingBuffer, null);
	        vkFreeMemory(system.device, stagingBufferMemory, null);
    	}
//    	bufferCount--;
    }

    // TODO: Make multithreaded
    public ByteBuffer map(int size) {
      try(MemoryStack stack = stackPush()) {


          // alloc pointer for our data
          PointerBuffer pointer = stack.mallocPointer(1);
          vkMapMemory(system.device, stagingBufferMemory, 0, size, 0, pointer);

          // Here instead of some mem copy function we can just
          // copy each and every byte of buffer.
          ByteBuffer datato = pointer.getByteBuffer(0, size);

          return datato;
      }
    }

    public FloatBuffer mapFloat(int size) {
      try(MemoryStack stack = stackPush()) {
          // alloc pointer for our data
          PointerBuffer pointer = stack.mallocPointer(1);
          vkMapMemory(system.device, stagingBufferMemory, 0, size, 0, pointer);

          // Here instead of some mem copy function we can just
          // copy each and every byte of buffer.
          FloatBuffer datato = pointer.getFloatBuffer(0, size/Float.BYTES);

          return datato;
      }
    }

    public ShortBuffer mapShort(int size) {
      try(MemoryStack stack = stackPush()) {
          // alloc pointer for our data
          PointerBuffer pointer = stack.mallocPointer(1);
          vkMapMemory(system.device, stagingBufferMemory, 0, size, 0, pointer);

          // Here instead of some mem copy function we can just
          // copy each and every byte of buffer.
          ShortBuffer datato = pointer.getShortBuffer(0, size/Short.BYTES);

          return datato;
      }
    }

    public IntBuffer mapInt(int size) {
      try(MemoryStack stack = stackPush()) {
          // alloc pointer for our data
          PointerBuffer pointer = stack.mallocPointer(1);
          vkMapMemory(system.device, stagingBufferMemory, 0, size, 0, pointer);

          // Here instead of some mem copy function we can just
          // copy each and every byte of buffer.
          IntBuffer datato = pointer.getIntBuffer(0, size/Integer.BYTES);

          return datato;
      }
    }

    // TODO: Make multithreaded
    public void unmap() {
      vkUnmapMemory(system.device, stagingBufferMemory);
    }


    // Sends data straight to the gpu
    // TODO: version where memory is constantly unmapped
    public void bufferData(ByteBuffer data, int size, boolean nodeMode) {

      System.out.println("5");
    	// If debug mode enabled
    	if (system == null) return;

  	    ByteBuffer datato = map(size);
    		datato.rewind();
    		data.rewind();
    		while (datato.hasRemaining()) {
    			datato.put(data.get());
    		}
    		datato.rewind();
    		unmap();

//        if (nodeMode) {
//	        system.nodeBufferData(stagingBuffer, bufferID, size);
//        }
//        else {
      	vkbase.copyBufferAndWait(stagingBuffer, bufferID, size);
//        }
    }

    public void bufferData(FloatBuffer data, int size, boolean nodeMode) {

      // If debug mode enabled
      if (system == null) return;

        FloatBuffer datato = mapFloat(size);
        datato.rewind();
        data.rewind();
        while (datato.hasRemaining()) {
          datato.put(data.get());
        }
        datato.rewind();
        unmap();

        vkbase.copyBufferAndWait(stagingBuffer, bufferID, size);
    }

    public void bufferData(ShortBuffer data, int size, boolean nodeMode) {

      // If debug mode enabled
      if (system == null) return;

        ShortBuffer datato = mapShort(size);
        datato.rewind();
        data.rewind();
        while (datato.hasRemaining()) {
          datato.put(data.get());
        }
        datato.rewind();
        unmap();

        vkbase.copyBufferAndWait(stagingBuffer, bufferID, size);
    }

    public void bufferData(IntBuffer data, int size, boolean nodeMode) {

      // If debug mode enabled
      if (system == null) return;

        IntBuffer datato = mapInt(size);
        datato.rewind();
        data.rewind();
        while (datato.hasRemaining()) {
          datato.put(data.get());
        }
        datato.rewind();
        unmap();

        vkbase.copyBufferAndWait(stagingBuffer, bufferID, size);
    }
}