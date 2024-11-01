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
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
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


    private int globalInstance = 0;
    private final static int MAX_INSTANCES = 128;

    public long[] buffers = new long[MAX_INSTANCES];
    private volatile long[] bufferMemory = new long[MAX_INSTANCES];
    public long[] stagingBuffers = new long[MAX_INSTANCES];
    private long[] stagingBufferMemory = new long[MAX_INSTANCES];

    private int[] bufferSize = new int[128];

    private static VulkanSystem system;
    private static VKSetup vkbase;

    public GraphicsBuffer(VulkanSystem s) {
    	system = s;
    	vkbase = s.vkbase;
    }

    // Debug mode constructor
    public GraphicsBuffer() {
    }

    {
      for (int i = 0; i < buffers.length; i++) {
        buffers[i] = -1;
        bufferMemory[i] = -1;
        stagingBuffers[i] = -1;
        stagingBufferMemory[i] = -1;
      }
    }


    // Releases any previous buffers and creates a buffer IF
    // - There's no previous buffer
    // - Buffer size != new size.
    // NOT THREAD SAFE HERE
    public void createBufferAuto(int size, int vertexIndexUsage, boolean retainedMode) {

    	if (buffers[globalInstance] == -1 || size > bufferSize[globalInstance]) {
    		// Delete old buffers
    		destroy(globalInstance);

    		// Create new one
    		if (retainedMode) {
          createBufferRetainedMode(size, vertexIndexUsage, globalInstance);
    		}
    		else {
          createBufferImmediateMode(size, vertexIndexUsage, globalInstance);
    		}
    	}
    }

    public static int bufferCount = 0;


    // Creates a buffer without allocating any data
    private void createBufferImmediateMode(int size, int usage, int inst) {
    	// If in debug mode, just assign a dummy value
    	if (system == null) {
    		this.buffers[inst] = (long)(Math.random()*100000.);

    		return;
    	}

    	try(MemoryStack stack = stackPush()) {

    		// Not from anywhere, just alloc pointers we can use
    		// to get back from createbuffer method
            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pBufferMemory = stack.mallocLong(1);


            // Buffer which is usable by both CPU and GPU
            vkbase.createBuffer(size,
                    usage,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer,
                    pBufferMemory);

            // Pointer variables now populated

            // GraphicsBuffedr object, set with our new pointer variables.
            buffers[inst] = pBuffer.get(0);
            bufferMemory[inst] = pBufferMemory.get(0);
            bufferSize[inst] = size;

    	}
    	bufferCount++;
    }


    // Needs to be called when it
    public long getCurrBuffer() {
      if (globalInstance == 0) globalInstance = 1;

      // Problem: we need to know whether we're doing retained or immediate.
      // We can quickly check stagingBuffer == -1 for immediate, != -1 for retained
      if (stagingBuffers[globalInstance-1] != -1) {
        // Retained
        return stagingBuffers[globalInstance-1];
      }
      else {
        // Immediate
        return buffers[globalInstance-1];
      }
    }


    public void reset() {
      globalInstance = 0;
    }

    // NOT THREAD SAFE
    private void createBufferRetainedMode(int size, int usage, int instance) {
      // If in debug mode, just assign a dummy value
      if (system == null) {
        this.buffers[instance] = (long)(Math.random()*100000.);

        return;
      }

      try(MemoryStack stack = stackPush()) {

        // Not from anywhere, just alloc pointers we can use
        // to get back from createbuffer method
            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pBufferMemory = stack.mallocLong(1);


            // Buffer which is usable by both CPU and GPU
            vkbase.createBuffer(size,
                    VK_BUFFER_USAGE_TRANSFER_DST_BIT | usage,
                    VK_MEMORY_HEAP_DEVICE_LOCAL_BIT,
                    pBuffer,
                    pBufferMemory);

            // Pointer variables now populated

            // GraphicsBuffedr object, set with our new pointer variables.
            buffers[instance] = pBuffer.get(0);
            bufferMemory[instance] = pBufferMemory.get(0);
            bufferSize[instance] = size;
      }


      // STAGING BUFFER
      try(MemoryStack stack = stackPush()) {
        LongBuffer pBuffer = stack.mallocLong(1);
        LongBuffer pBufferMemory = stack.mallocLong(1);
        vkbase.createBuffer(size,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT | usage,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                pBuffer,
                pBufferMemory);

        this.stagingBuffers[instance] = pBuffer.get(0);
        this.stagingBufferMemory[instance] = pBufferMemory.get(0);
      }
      bufferCount++;
    }






    public void destroy(int instance) {
    	// If debug mode enabled
    	if (system == null) return;

    	if (buffers[instance] != -1 && bufferMemory[instance] != -1) {
	        vkDestroyBuffer(system.device, buffers[instance], null);
	        vkFreeMemory(system.device, bufferMemory[instance], null);
    	}
//    	bufferCount--;
    }

    public void destroy() {
      for (int i = 0; i < buffers.length; i++) {
        if (buffers[i] != -1 && bufferMemory[i] != -1) {
          vkDestroyBuffer(system.device, buffers[i], null);
          vkFreeMemory(system.device, bufferMemory[i], null);
        }
      }
    }

    // TODO: Make multithreaded
    public ByteBuffer mapByte(int size, long mem) {
      try(MemoryStack stack = stackPush()) {


          // alloc pointer for our data
          PointerBuffer pointer = stack.mallocPointer(1);
          vkMapMemory(system.device, mem, 0, size, 0, pointer);

          // Here instead of some mem copy function we can just
          // copy each and every byte of buffer.
          ByteBuffer datato = pointer.getByteBuffer(0, size);

          return datato;
      }
    }

    public ByteBuffer map(int instance) {
      // Problem: we need to know whether we're doing retained or immediate.
      // We can quickly check stagingBuffer == -1 for immediate, != -1 for retained
      if (stagingBufferMemory[instance] != -1) {
        // Retained
        return mapByte(bufferSize[instance], stagingBufferMemory[instance]);
      }
      else {
        // Immediate
        return mapByte(bufferSize[instance], bufferMemory[instance]);
      }
    }

    public FloatBuffer mapFloat(int size, long mem) {
      try(MemoryStack stack = stackPush()) {
          // alloc pointer for our data
          PointerBuffer pointer = stack.mallocPointer(1);
          vkMapMemory(system.device, mem, 0, size, 0, pointer);

          // Here instead of some mem copy function we can just
          // copy each and every byte of buffer.
          FloatBuffer datato = pointer.getFloatBuffer(0, size/Float.BYTES);

          return datato;
      }
    }

    public ShortBuffer mapShort(int size, long mem) {
      try(MemoryStack stack = stackPush()) {
          // alloc pointer for our data
          PointerBuffer pointer = stack.mallocPointer(1);
          vkMapMemory(system.device, mem, 0, size, 0, pointer);

          // Here instead of some mem copy function we can just
          // copy each and every byte of buffer.
          ShortBuffer datato = pointer.getShortBuffer(0, size/Short.BYTES);

          return datato;
      }
    }

    public static IntBuffer mapInt(int size, long mem) {
      try(MemoryStack stack = stackPush()) {
          // alloc pointer for our data
          PointerBuffer pointer = stack.mallocPointer(1);
          vkMapMemory(system.device, mem, 0, size, 0, pointer);

          // Here instead of some mem copy function we can just
          // copy each and every byte of buffer.
          IntBuffer datato = pointer.getIntBuffer(0, size/Integer.BYTES);

          return datato;
      }
    }

    // TODO: Make multithreaded
    public void unmap(long mem) {
      vkUnmapMemory(system.device, mem);
    }

    public void increaseInstance() {
      globalInstance++;
    }

    public int getInstance() {
      return globalInstance;
    }

    public void unmap(int instance) {
      // Problem: we need to know whether we're doing retained or immediate.
      // We can quickly check stagingBuffer == -1 for immediate, != -1 for retained
      if (stagingBufferMemory[instance] != -1) {
        // Retained
        unmap(stagingBufferMemory[instance]);
      }
      else {
        // Immediate
        unmap(bufferMemory[instance]);
      }
    }

    /////////////////////////////////////////////////////
    // TODO: These take a lot of time, move them to threadNodes.
    // Don't worry, the problem of all this copy+paste code will be solved
    // by moving the problem somewhere else.

    // Sends data straight to the gpu
    // TODO: version where memory is constantly unmapped

    // IMMEDIATE MODE METHODS TO BE USED IN MULTITHREADING
    public void bufferDataImmediate(ByteBuffer data, int size, int instance) {
      	// If debug mode enabled
      	if (system == null) return;

      	long mem = bufferMemory[instance];

  	    ByteBuffer datato = mapByte(size, mem);
    		datato.rewind();
    		data.rewind();
    		while (datato.hasRemaining()) {
    			datato.put(data.get());
    		}
    		datato.rewind();
    		unmap(mem);
    }

    public void bufferDataImmediate(FloatBuffer data, int size, int instance) {
        // If debug mode enabled
        if (system == null) return;

        long mem = bufferMemory[instance];

        FloatBuffer datato = mapFloat(size, mem);

        datato.rewind();
        data.rewind();
        while (datato.hasRemaining()) {
          datato.put(data.get());
        }
        datato.rewind();

        unmap(mem);
    }

    public void bufferDataImmediate(ShortBuffer data, int size, int instance) {
        // If debug mode enabled
        if (system == null) return;

        long mem = bufferMemory[instance];

        ShortBuffer datato = mapShort(size, mem);
        datato.rewind();
        data.rewind();
        while (datato.hasRemaining()) {
          datato.put(data.get());
        }
        datato.rewind();
        unmap(mem);
    }

    public void bufferDataImmediate(IntBuffer data, int size, int instance) {
        // If debug mode enabled
        if (system == null) return;

        long mem = bufferMemory[instance];

        IntBuffer datato = mapInt(size, mem);
        datato.rewind();
        data.rewind();
        while (datato.hasRemaining()) {
          datato.put(data.get());
        }
        datato.rewind();
        unmap(mem);
    }










    ////////////////////////////////////////////////////////////////
    // Retained mode
    // NOT TO BE USED IN MULTITHREADING

    public void bufferDataRetained(ByteBuffer data, int size, int instance) {
        // If debug mode enabled
        if (system == null) return;

        ByteBuffer datato = mapByte(size, stagingBuffers[instance]);
        datato.rewind();
        data.rewind();
        while (datato.hasRemaining()) {
          datato.put(data.get());
        }
        datato.rewind();
        unmap(stagingBuffers[instance]);

        vkbase.copyBufferAndWait(stagingBuffers[instance], buffers[instance], size);
    }

    public void bufferDataRetained(FloatBuffer data, int size, int instance) {
        // If debug mode enabled
        if (system == null) return;

        FloatBuffer datato = mapFloat(size, stagingBuffers[instance]);

        datato.rewind();
        data.rewind();
        while (datato.hasRemaining()) {
          datato.put(data.get());
        }
        datato.rewind();

        unmap(stagingBuffers[instance]);

        vkbase.copyBufferAndWait(stagingBuffers[instance], buffers[instance], size);
    }

    public void bufferDataRetained(ShortBuffer data, int size, int instance) {
        // If debug mode enabled
        if (system == null) return;

        ShortBuffer datato = mapShort(size, stagingBuffers[instance]);
        datato.rewind();
        data.rewind();
        while (datato.hasRemaining()) {
          datato.put(data.get());
        }
        datato.rewind();
        unmap(stagingBuffers[instance]);

        vkbase.copyBufferAndWait(stagingBuffers[instance], buffers[instance], size);
    }

    public void bufferDataRetained(IntBuffer data, int size, int instance) {
        // If debug mode enabled
        if (system == null) return;

        IntBuffer datato = mapInt(size, stagingBuffers[instance]);
        datato.rewind();
        data.rewind();
        while (datato.hasRemaining()) {
          datato.put(data.get());
        }
        datato.rewind();
        unmap(stagingBuffers[instance]);

        vkbase.copyBufferAndWait(stagingBuffers[instance], buffers[instance], size);
    }
}