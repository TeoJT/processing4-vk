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
import java.nio.LongBuffer;
import java.nio.Buffer;
import java.nio.BufferOverflowException;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;

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
    	if (!bufferAssigned || bufferSize != size) {
    		// Delete old buffers
    		destroy();
    		// Create new one
    		createBuffer(size, usage);
    	}
    }


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
    }


    // Sends data straight to the gpu
    // TODO: version where memory is constantly unmapped
    public void bufferData(ByteBuffer data, int size, boolean nodeMode) {

    	// If debug mode enabled
    	if (system == null) return;

    	try(MemoryStack stack = stackPush()) {


	        // alloc pointer for our data
	        PointerBuffer pointer = stack.mallocPointer(1);

	        vkMapMemory(system.device, stagingBufferMemory, 0, size, 0, pointer);

	        // Here instead of some mem copy function we can just
	        // copy each and every byte of buffer.
        	ByteBuffer datato = pointer.getByteBuffer(0, size);
	        try {
	        	// Now for some reason we need to put in floats instead of bytes.
	        	// Maybe something to do with the ordering of bytes in a float.
	        	// Whatever, it works and that's what matters.
	    		datato.rewind();
	    		data.rewind();

	    		while (datato.hasRemaining()) {
	    			datato.put(data.get());
	    		}
	    		datato.rewind();

//	    		FloatBuffer printout = (FloatBuffer)

	    		// Just to print out stuff
//	    		data.rewind();
//	    		datato.rewind();
//	    		while (datato.hasRemaining()) {
//	    			System.out.println(datato.getFloat());
//	    			System.out.println(data.getFloat());
//	    		}
//	    		datato.rewind();
	        }
	        catch (BufferOverflowException e) {
	        	System.err.println("Buffer overflow: tried to write buffer of size "+size+" into buffer of size "+datato.capacity());
	        }

	        vkUnmapMemory(system.device, stagingBufferMemory);


	        if (nodeMode) {
		        system.nodeBufferData(stagingBuffer, bufferID, size);
	        }
	        else {
	        	vkbase.copyBufferAndWait(stagingBuffer, bufferID, size);
	        }


//	        vkDestroyBuffer(system.device, stagingBuffer, null);
//	        vkFreeMemory(system.device, stagingBufferMemory, null);
    	}
    }

}