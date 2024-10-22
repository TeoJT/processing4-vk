package processing.GL2VK;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_SECONDARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT16;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VkPhysicalDeviceIndexTypeUint8FeaturesKHR.INDEXTYPEUINT8;
import static org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdDraw;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexed;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandBufferInheritanceInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;

//import helloVulkan.VKSetup.QueueFamilyIndices;

// TODO: time active vs. time idle query information
// TODO: average % of cmdQueue used in a frame.

public class ThreadNode {
	final static boolean DEBUG = false;

	// ThreadNode commands
	public final static int NO_CMD = 0;
	public final static int CMD_DRAW_ARRAYS = 1;
	public final static int CMD_DRAW_INDEXED = 2;
	public final static int CMD_BEGIN_RECORD = 3;
	public final static int CMD_END_RECORD = 4;
	public final static int CMD_KILL = 5;
	public final static int CMD_BUFFER_DATA = 6;
	public final static int CMD_BIND_PIPELINE = 7;
	public final static int CMD_PUSH_CONSTANT = 8;

	// ThreadNode state statuses
	public final static int STATE_INACTIVE = 0;
	public final static int STATE_SLEEPING = 1;
	public final static int STATE_RUNNING = 2;
	public final static int STATE_ENTERING_SLEEP = 3;
	public final static int STATE_KILLED = 4;
	public final static int STATE_WAKING = 5;
	public final static int STATE_SLEEPING_INTERRUPTED = 6;
	public final static int STATE_NEXT_CMD = 7;


	// CURRENT BUGS:
	// BUG WARNING signalled out of sleep with no work available.
	// Let's say we're calling endCommands:
	// - (1) executing cmd
	// - (0) Set cmd id
	// - (1) Oh look! A new command to execute.
	// - (1) Done, let's go to sleep
	// - (0) wakeThread (we're under the assumption that the thread hasn't done the work yet)
	// - (1) Woke up, but wait! There isn't any work for me to do!
	// Solution: check cmd is set to 0 or not

	// Other bug:
	// looplock'd waiting for a thread that won't respond (state 1)
	//

	// To avoid clashing from the main thread accessing the front of the queue while the
	// other thread is accessing the end of the queue, best solution is to make this big
	// enough lol.
	private final static int MAX_QUEUE_LENGTH = 1024;

	private VulkanSystem system;
	private VKSetup vkbase;
	private int myID = 0;

	private VkCommandBuffer[] cmdbuffers;

	// NOT to be set by main thread
	private AtomicInteger currentFrame = new AtomicInteger(0);
	private AtomicInteger currentImage = new AtomicInteger(0);
	private long commandPool;

	private AtomicInteger threadState = new AtomicInteger(STATE_INACTIVE);
	private AtomicBoolean openCmdBuffer = new AtomicBoolean(false);

	// Read-only begin info for beginning our recording of commands
	// (what am i even typing i need sleep)
	// One for each frames in flight
	private VkCommandBufferBeginInfo[] beginInfos;
	// Just to keep it from being garbage collected or something
	private VkCommandBufferInheritanceInfo[] inheritanceInfos;


	// There are two seperate indexes, one for our thread (main thread) and one for this thread.
	// We add item to queue (cmdindex = 0 -> 1) and then eventually thread updates its own index
	// as it works on cmd   (myIndex  = 0 -> 1)
	private int cmdindex = 0;  // Start at one because thread will have already consumed index 0
	private Thread thread;

	// Accessed by 2 threads so volatile (i was told that volatile avoids outdated caching issues)
	// (source: the internet, the most truthful place, i think)
//	private volatile CMD[] cmdqueue;


	// INNER COMMAND TYPES
	// Originally was gonna create some classes which extend this class,
	// but this would mean garbage collection for each command we call.
	// The other option is to put all arguments from every command into
	// the one cmd class, which isn't the most readable or memory efficient,
	// but we care about going FAST.
	private AtomicIntegerArray cmdID = new AtomicIntegerArray(MAX_QUEUE_LENGTH);
	private AtomicLongArray[] cmdLongArgs = new AtomicLongArray[128];
	private AtomicIntegerArray[] cmdIntArgs = new AtomicIntegerArray[128];
	public long currentPipeline = 0L;



	public ThreadNode(VulkanSystem vk, int id) {
		// Variables setup
		system = vk;
		vkbase = vk.vkbase;
		myID = id;

		// Initialise cmdqueue and all its objects
		for (int i = 0; i < MAX_QUEUE_LENGTH; i++) {
			cmdID.set(i, 0);
		}

		createObjects();
		startThread();
	}


	private void println(String message) {
		if (DEBUG) {
			System.out.println("("+myID+") "+message);
		}
	}

	private void createObjects() {
		// Create command pool
        try(MemoryStack stack = stackPush()) {
	        VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack);
	        poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
	        poolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
	        poolInfo.queueFamilyIndex(vkbase.queueIndicies.graphicsFamily);

	        // create our command pool vk
	        LongBuffer pCommandPool = stack.mallocLong(1);
	        if (vkCreateCommandPool(vkbase.device, poolInfo, null, pCommandPool) != VK_SUCCESS) {
	            throw new RuntimeException("Failed to create command pool");
	        };
	        commandPool = pCommandPool.get(0);
        }

        final int commandBuffersCount = VulkanSystem.MAX_FRAMES_IN_FLIGHT;

        // Create secondary command buffer
        try(MemoryStack stack = stackPush()) {

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(commandPool);
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_SECONDARY);
            allocInfo.commandBufferCount(commandBuffersCount);

            PointerBuffer pCommandBuffers = stack.mallocPointer(commandBuffersCount);

            if(vkAllocateCommandBuffers(vkbase.device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers");
            }

            cmdbuffers = new VkCommandBuffer[commandBuffersCount];
            for(int i = 0; i < commandBuffersCount; i++) {
            	cmdbuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), vkbase.device);
            }
        }

        int imagesSize = system.swapChainFramebuffers.size();
        // Create readonly beginInfo structs.
        beginInfos = new VkCommandBufferBeginInfo[imagesSize];
        inheritanceInfos = new VkCommandBufferInheritanceInfo[imagesSize];
        for(int i = 0; i < imagesSize; i++) {
//        	 Inheritance because for some reason we need that
	        inheritanceInfos[i] = VkCommandBufferInheritanceInfo.create();
	        inheritanceInfos[i].sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO);
			inheritanceInfos[i].renderPass(system.renderPass);
			// Secondary command buffer also use the currently active framebuffer
			inheritanceInfos[i].framebuffer(system.swapChainFramebuffers.get(i));

			beginInfos[i] = VkCommandBufferBeginInfo.create();
			beginInfos[i].sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
			beginInfos[i].flags(VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT);
			beginInfos[i].pInheritanceInfo(inheritanceInfos[i]);
        }
	}

	private void startThread() {
		// Inside of thread, we run logic which checks for items in the queue
		// and then executes the vk commands that correspond to the int
		thread = new Thread(new Runnable() {
	          public void run() {
	        	  int myIndex = 0;

        		  long sleepTime = 0L;
        		  long runTime = 0L;

        		  boolean pipelineBound = false;

    			  ByteBuffer pushConstantBuffer = null;

	        	  // Loop until receive KILL_THREAD cmd
	        	  while (true) {
    				  long runbefore = System.nanoTime();
	        		  boolean goToSleepMode = false;
	        		  boolean kill = false;

	        		  int index = (myIndex++)%MAX_QUEUE_LENGTH;

	        		  VkCommandBuffer cmdbuffer = cmdbuffers[currentFrame.get()];


//	        		  if (threadState.get() == STATE_WAKING) {
//	        			  if (id == NO_CMD) System.err.println(myID+" NO_CMD warning");
//        				  threadState.set(STATE_RUNNING);
//	        		  }


	        		  // ======================
	        		  // CMD EXECUTOR
	        		  // ======================


	        		  // As soon as we're at this point we need to tell the main thread that this is a
	        		  // time-sensitive point where we are in the process of getting the next
	        		  // cmd, and in between that verrrry small timeframe, it could be set to something
	        		  // different. The next command will most definitely be 0 -> something else, so
	        		  // our thread may end up reading an outdated version (i.e. 0, or NO_CMD).
	        		  // If that happens, our main thread needs to see the state is STATE_NEXT_CMD, and
	        		  // then call wakeThread which will make sure our thread's got the right value.

        			  threadState.set(STATE_NEXT_CMD);

	        		  switch (cmdID.getAndSet(index, 0)) {
	        		  case NO_CMD:
	        			  threadState.set(STATE_ENTERING_SLEEP);
	        			  goToSleepMode = true;
	        			  myIndex--;
	        			  break;
	        		  case CMD_DRAW_ARRAYS: {
	        			  // TODO: Similar to drawIndexed, pass a list of bound buffers
	        			  // instead of the one interleaved list.

	        			  threadState.set(STATE_RUNNING);
	        			  println("CMD_DRAW_ARRAYS (index "+index+")");
	        			  int size = cmdIntArgs[0].get(index);
	        			  int first = cmdIntArgs[1].get(index);
	        			  int numBuffers = cmdIntArgs[2].get(index);

	        			  try(MemoryStack stack = stackPush()) {
	        			      LongBuffer vertexBuffers = stack.callocLong(numBuffers);
	        			      LongBuffer offsets = stack.callocLong(numBuffers);

	        			      // Longargs 1-x are buffers.
	        			      for (int i = 0; i < numBuffers; i++) {
	        			    	  vertexBuffers.put(i, cmdLongArgs[i].get(index));
		        			      offsets.put(i, 0);
	        			      }
	        			      vkCmdBindVertexBuffers(cmdbuffer, 0, vertexBuffers, offsets);

	        			      vkCmdDraw(cmdbuffer, size, 1, first, 0);
	        		      }
	        			  break;
	        		  }
	        			  // Probably the most important command
	        		  case CMD_BEGIN_RECORD:
	        			  	threadState.set(STATE_RUNNING);
	        			  	sleepTime = 0;
	        			  	runTime = 0;
	        			  	println("CMD_BEGIN_RECORD");

        			  		currentImage.set(cmdIntArgs[0].get(index));
        			  		currentFrame.set(cmdIntArgs[1].get(index));
        			  		cmdbuffer = cmdbuffers[currentFrame.get()];

	        			  	if (openCmdBuffer.get() == false) {
	        			  		vkResetCommandBuffer(cmdbuffer, 0);
		        		      	// Begin recording

		        		      	// In case you're wondering, beginInfo index is currentImage, not
		        		      	// the currentFrame, because it holds which framebuffer (image) we're
		        		      	// using (confusing i know)
		        	            if(vkBeginCommandBuffer(cmdbuffer, beginInfos[currentImage.get()]) != VK_SUCCESS) {
		        	                throw new RuntimeException("Failed to begin recording command buffer");
		        	            }
	        			  	}
	        			  	// Bug detected
	        			  	else System.err.println("("+myID+") Attempt to begin an already open command buffer.");

	        	            openCmdBuffer.set(true);
//	        	            vkCmdBindPipeline(cmdbuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, system.graphicsPipeline);
	        	            pipelineBound = false;
	        	            break;
	        		  case CMD_END_RECORD:
	        			  	threadState.set(STATE_RUNNING);
	        			  	println("CMD_END_RECORD (index "+index+")");

	        			  	if (openCmdBuffer.get() == true) {
							    if(vkEndCommandBuffer(cmdbuffer) != VK_SUCCESS) {
							        throw new RuntimeException("Failed to record command buffer");
							    }
	        			  	}
	        			  	else System.err.println("("+myID+") Attempt to close an already closed command buffer.");

	        	            openCmdBuffer.set(false);
	        	            // We should also really go into sleep mode now
	        	            threadState.set(STATE_ENTERING_SLEEP);
	        	            goToSleepMode = true;

//	        	            System.out.println("("+myID+") Sleep time "+(sleepTime/1000L)+"us  Run time "+(runTime/1000L)+"us");

						    break;
	        		  case CMD_KILL:
	        			  threadState.set(STATE_RUNNING);
	        			  goToSleepMode = false;
	        			  kill = true;
	        			  break;

	        		  // This goes pretty much unused.
	        		  case CMD_BUFFER_DATA:
	        			  threadState.set(STATE_RUNNING);
	        			  println("CMD_BUFFER_DATA (index "+index+")");

//	        			  vkCmdEndRenderPass(system.currentCommandBuffer);
	        			  system.copyBufferFast(cmdbuffer, cmdLongArgs[0].get(index), cmdLongArgs[1].get(index), cmdIntArgs[0].get(index));
//	        			  vkCmdBeginRenderPass(system.currentCommandBuffer, system.renderPassInfo, VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS);
	        			  break;

	        		  case CMD_BIND_PIPELINE:
	        			  // Ensure we have a bound pipeline before anything

	        			  threadState.set(STATE_RUNNING);
	        			  println("CMD_BIND_PIPELINE (index "+index+")");
	        	          vkCmdBindPipeline(cmdbuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmdLongArgs[0].get(index));
	        			  break;

	        		  case CMD_DRAW_INDEXED: {
	        			  threadState.set(STATE_RUNNING);
	        			  println("CMD_DRAW_INDEXED (index "+index+")");
	        			  // Int0: indiciesSize
	        			  // Long0: indiciesBuffer
	        			  // Int1: numBuffers
	        			  // LongX: vertexBuffers
	        			  // Int2: offset

	        			  int indiciesSize = cmdIntArgs[0].get(index);
	        			  int numBuffers = cmdIntArgs[1].get(index);
	        			  int offsettype = cmdIntArgs[2].get(index);
	        			  long indicesBuffer = cmdLongArgs[0].get(index);

	        			  int offset = offsettype & 0x3FFFFFFF;
	        			  int type   = offsettype & 0xC0000000;

	        			  int vkType = 0;
	        			  switch (type) {
	        			  // Unsigned byte
	        			  case 0x40000000:
	        				  // TODO: Test this to see if vulkan accepts it.
	        				  vkType = INDEXTYPEUINT8;
	        				  break;

	        		      // Unsigned int
	        			  case 0x80000000:
	        				  vkType = VK_INDEX_TYPE_UINT32;
	        				  break;

	        			  // Unsigned short
	        			  case 0xC0000000:
	        				  vkType = VK_INDEX_TYPE_UINT16;
	        				  break;
	        			  }


	        			  try(MemoryStack stack = stackPush()) {
	        			      LongBuffer vertexBuffers = stack.callocLong(numBuffers);
	        			      LongBuffer offsets = stack.callocLong(numBuffers);

	        			      // Longargs 1-x are buffers.
	        			      for (int i = 0; i < numBuffers; i++) {
	        			    	  vertexBuffers.put(i, cmdLongArgs[i+1].get(index));
		        			      offsets.put(i, offset);
	        			      }
	        			      vertexBuffers.rewind();
	        			      offsets.rewind();

	        			      vkCmdBindVertexBuffers(cmdbuffer, 0, vertexBuffers, offsets);

	        			      vkCmdBindIndexBuffer(cmdbuffer, indicesBuffer, offset, vkType);
	        			      vkCmdDrawIndexed(cmdbuffer, indiciesSize, 1, offset, 0, 0);
	        			  }

	        			  break;
	        		  }
	        		  case CMD_PUSH_CONSTANT:
	        			  threadState.set(STATE_RUNNING);
	        			  println("CMD_PUSH_CONSTANT (index "+index+")");
	        			  // Long0:   pipelineLayout
	        			  // Int0:    Size/offset/vertexOrFragment
	        			  // Long1-X: bufferData (needs to be reconstructed

	        			  long pipelineLayout = cmdLongArgs[0].get(index);

	        			  // Layout: ssssssssoooooooooooooooovvvvvvvv
	        			  int arg0 = cmdIntArgs[0].get(index);
	        			  int size             = ((arg0 >> 24) & 0x000000FF);
	        			  int offset           = ((arg0 >> 8) & 0x0000FFFF);
	        			  int vertexOrFragment = (arg0 & 0x000000FF);

	        			  // Needs to be in multiples of 8 for long buffer.
	        			  // TODO: actually sort out.
	        			  // buffer size = 12, we need to expand to 16.
	        			  // This equasion would set it to 8.
//	        			  size = ((size/8))*8;

	        			  // Create buffer
	        			  // It's a gobal variable so we don't have to recreate the variable each
	        			  // time
	        			  if (pushConstantBuffer == null || pushConstantBuffer.capacity() != size) {
	        				  pushConstantBuffer = BufferUtils.createByteBuffer(size);
	        				  pushConstantBuffer.order(ByteOrder.LITTLE_ENDIAN);
	        			  }

	        			  // Now we have to reconstruct the buffer from the longs
	        			  pushConstantBuffer.rewind();
	        			  int arg = 1;

	        			  // Long story short, we might store 8 bytes in a 4-byte buffer,
	        			  // which would cause an exception.
	        			  // We have a special case to write the bytes in multiples of 8,
	        			  // then write the remaining 4 bytes
	        			  int ssize = size;
	        			  if (size % 8 == 4) ssize -= 4;
	        			  for (int i = 0; i < ssize; i += Long.BYTES) {
	        				  pushConstantBuffer.putLong(cmdLongArgs[arg++].get(index));
	        			  }
	        			  if (size % 8 == 4) {
	        				  int val = (int)cmdLongArgs[arg++].get(index);
	        				  pushConstantBuffer.putInt(val);
	        			  }
	        			  pushConstantBuffer.rewind();


	        			  // Get the vk type
	        			  int vkType = 0;
	        			  if (vertexOrFragment == GLUniform.VERTEX) {
	        				  vkType = VK_SHADER_STAGE_VERTEX_BIT;
	        			  }
	        			  else if (vertexOrFragment == GLUniform.FRAGMENT) {
	        				  vkType = VK_SHADER_STAGE_FRAGMENT_BIT;
	        			  }
	        			  else {
	        				  vkType = VK_SHADER_STAGE_VERTEX_BIT;
	        			  }

	        			  vkCmdPushConstants(cmdbuffer, pipelineLayout, vkType, offset, pushConstantBuffer);

	        			  break;
	        		  }

	        		  // ======================


	        		  if (kill) {
	        			  threadState.set(STATE_KILLED);
	        			  // Kills the thread
	        			  break;
	        		  }

	        		  runTime += System.nanoTime()-runbefore;
	        		  // No more tasks to do? Take a lil nap.
	        		  if (goToSleepMode) {
	        			  println("NOW SLEEPING");
        				  long before = System.nanoTime();
	        			  try {
	        				  // Sleep for an indefinite amount of time
	        				  // (we gonna interrupt the thread later)
	        				  threadState.set(STATE_SLEEPING);
	        				  Thread.sleep(999999);
	        			  }
	        			  catch (InterruptedException e) {
	        				  threadState.set(STATE_WAKING);
	        				  println("WAKEUP");
	        			  }
        				  sleepTime += System.nanoTime()-before;
	        		  }
	        	  }
	        	  threadState.set(STATE_KILLED);

	          }
		}
		);
		thread.start();
	}
	int stalltime = 0;

	private int getNextCMDIndex() {
		int ret = (cmdindex)%MAX_QUEUE_LENGTH;
		while (cmdID.get(ret) != NO_CMD) {
			// We're forced to wait until the thread has caught up with some of the queue
//			try {
//				Thread.sleep(0);
//			} catch (InterruptedException e) {
//			}
//			println("WARNING  queue clash, cmdid is "+ret);
		}
		cmdindex++;
		return ret;
	}

	private void setIntArg(int argIndex, int queueIndex, int value) {
		if (cmdIntArgs[argIndex] == null) {
			cmdIntArgs[argIndex] = new AtomicIntegerArray(MAX_QUEUE_LENGTH);
		}
		cmdIntArgs[argIndex].set(queueIndex, value);
	}

	private void setLongArg(int argIndex, int queueIndex, long value) {
		if (cmdLongArgs[argIndex] == null) {
			cmdLongArgs[argIndex] = new AtomicLongArray(MAX_QUEUE_LENGTH);
		}
		cmdLongArgs[argIndex].set(queueIndex, value);
	}


	private void wakeThread(int cmdindex) {
		// There's a bug if we just call wakethread after setting cmdIndex.
		// I'm going to copy+paste it here:
		// Let's say we're calling endCommands:
		// - (1) executing cmd
		// - (0) Set cmd id
		// - (1) Oh look! A new command to execute.
		// - (1) Done, let's go to sleep
		// - (0) wakeThread (we're under the assumption that the thread hasn't done the work yet)
		// - (1) Woke up, but wait! There isn't any work for me to do!
		// Solution: Check cmdid is non-zero, because thread sets it to 0 as soon as it executes
		// the command
		if (cmdID.get(cmdindex) == NO_CMD) {
			return;
		}


		// Only need to interrupt if sleeping.
		// We call it here because if wakeThread is called, then a command was called, and
		// when a command was called, that means we should definitely not be asleep
		// (avoids concurrency issues with await()
		// If it's on STATE_NEXT_CMD, it means that it might have an outdated cmdid, which we can fix
		// by simply interrupting it as soon as it eventually goes into sleep mode
		if (threadState.get() == STATE_ENTERING_SLEEP || threadState.get() == STATE_NEXT_CMD) {
			// Uhoh, unlucky. This means we just gotta wait until we're entering sleep state then wake up.
			while (threadState.get() != STATE_SLEEPING) {
				try {
					Thread.sleep(0, 1000);
				} catch (InterruptedException e) {
				}
			}
			println("INTERRUPT");

			threadState.set(STATE_SLEEPING_INTERRUPTED);
			thread.interrupt();
		}
		if (threadState.get() == STATE_SLEEPING) {
			println("INTERRUPT");

			// We need to set status for only one interrupt otherwise we will keep calling
			// interrupt interrupt interrupt interrupt interrupt interrupt interrupt interrupt
			// and it seems to be stored in some sort of queue. That means, when the thread tries
			// to go back to sleep, it immediately wakes up because those interrupts are still in
			// the queue. We tell it "it's been interrupted once, don't bother it any further."
			threadState.set(STATE_SLEEPING_INTERRUPTED);
			thread.interrupt();
		}

		// We also need to consider the case for when a thread is ABOUT to enter sleep mode.
		// Cus we can call interrupt() all we want, it's not going to stop the thread from
		// entering sleep mode.

//		sleeping.set(false);
	}


    public void drawArrays(ArrayList<Long> buffers, int size, int first) {
        int index = getNextCMDIndex();
		println("call CMD_DRAW_ARRAYS (index "+index+")");

        for (int i = 0; i < buffers.size(); i++) {
        	setLongArg(i, index, buffers.get(i));
        }

		setIntArg(0, index, size);
		setIntArg(1, index, first);
		setIntArg(2, index, buffers.size());
        // Remember, last thing we should set is cmdID, set it before and
        // our thread may begin executing drawArrays without all the commands
        // being properly set.
        cmdID.set(index, CMD_DRAW_ARRAYS);
        wakeThread(index);
    }


    public void drawIndexed(int indiciesSize, long indiciesBuffer, ArrayList<Long> vertexBuffers, int offset, int type) {
        int index = getNextCMDIndex();
		println("call CMD_DRAW_INDEXED (index "+index+")");

		  // Int0: indiciesSize
		  // Long0: indiciesBuffer
		  // Int1: numBuffers
		  // LongX: vertexBuffers
		  // Int2: offset/type (higher bits type, lower bits offset)

        setIntArg(0, index, indiciesSize);
        setLongArg(0, index, indiciesBuffer);
        setIntArg(1, index, vertexBuffers.size());

        int offsettype = offset & 0x3FFFFFFF | 0xC0000000;
        // Replace last 2 bits with type

        switch (type) {
        case GL2VK.GL_UNSIGNED_BYTE:
        	// 1
        	offsettype &= 0x40000000;
        	break;
        case GL2VK.GL_UNSIGNED_INT:
        	// 2
        	offsettype &= 0x80000000;
        	break;
        case GL2VK.GL_UNSIGNED_SHORT:
        	// 3
        	offsettype &= 0xC0000000;
        	break;
        }

        setIntArg(2, index, offsettype);

        for (int i = 0; i < vertexBuffers.size(); i++) {
        	setLongArg(i+1, index, vertexBuffers.get(i));
        }

        cmdID.set(index, CMD_DRAW_INDEXED);
        wakeThread(index);
    }


    // TODO: what would be much more efficient and perhaps easier would be to pass
    // the literal uniform arguments e.g.
    // mat4, vec2, another vec2
    // We would need a class that contains the args we wanna pass tho.
    public void pushConstant(long pipelineLayout, int vertexOrFragment, int offset, ByteBuffer buffer) {
    	int index = getNextCMDIndex();
		println("call CMD_PUSH_CONSTANT (index "+index+")");
		  // Long0:   pipelineLayout
		  // Int0:    Size/offset/vertexOrFragment
		  // Long1-X: bufferData (needs to be reconstructed
    	setLongArg(0, index, pipelineLayout);

    	int size = buffer.capacity();

    	// Let's combine it into a single int argument to reduce memory usage
    	// Layout: ssssssssoooooooooooooooovvvvvvvv
    	// Remember that none of these should ever be bigger than their limits
    	int arg0 = 0;
    	arg0 |= size << 24;
    	arg0 |= ((offset << 8) & 0x00FFFF00);
    	arg0 |= (vertexOrFragment & 0x000000FF);
    	setIntArg(0, index, arg0);

    	// Now, we need to do the unhinged:
    	// Stuff an entire buffer into the long args.
    	// If we use the entire 256 bytes of pushConstant space,
    	// we will need 32 long args altogether so it's not too bad I guess??
    	int arg = 1;
    	for (int i = 0; i < size; i += Long.BYTES) {
    		setLongArg(arg++, index, buffer.getLong());
    	}

        cmdID.set(index, CMD_PUSH_CONSTANT);
        wakeThread(index);
    }

    public void pushConstant(long pipelineLayout, int vertexOrFragment, int offset, FloatBuffer buffer) {
      int index = getNextCMDIndex();
    println("call CMD_PUSH_CONSTANT (index "+index+")");
      // Long0:   pipelineLayout
      // Int0:    Size/offset/vertexOrFragment
      // Long1-X: bufferData (needs to be reconstructed
      setLongArg(0, index, pipelineLayout);

      int size = buffer.capacity()*4;

      // Let's combine it into a single int argument to reduce memory usage
      // Layout: ssssssssoooooooooooooooovvvvvvvv
      // Remember that none of these should ever be bigger than their limits
      int arg0 = 0;
      arg0 |= size << 24;
      arg0 |= ((offset << 8) & 0x00FFFF00);
      arg0 |= (vertexOrFragment & 0x000000FF);
      setIntArg(0, index, arg0);

      // Now, we need to do the unhinged:
      // Stuff an entire buffer into the long args.
      // If we use the entire 256 bytes of pushConstant space,
      // we will need 32 long args altogether so it's not too bad I guess??
      int arg = 1;
      for (int i = 0; i < size; i += Long.BYTES) {
        float val1 = buffer.get();
        float val2 = buffer.get();
        setLongArg(arg++, index, (Float.floatToIntBits(val1) & 0xFFFFFFFFL) | ((Float.floatToIntBits(val2) & 0xFFFFFFFFL) << 32));
      }

      cmdID.set(index, CMD_PUSH_CONSTANT);
      wakeThread(index);
    }

    private int pushConstant(long pipelineLayout, int vertexOrFragment, int offset, int size) {
    	int index = getNextCMDIndex();

    	println("call CMD_PUSH_CONSTANT (index "+index+")");
		  // Long0:   pipelineLayout
		  // Int0:    Size/offset/vertexOrFragment
		  // Long1-X: bufferData (needs to be reconstructed
	  	setLongArg(0, index, pipelineLayout);

	  	// Let's combine it into a single int argument to reduce memory usage
	  	// Layout: ssssssssoooooooooooooooovvvvvvvv
	  	// Remember that none of these should ever be bigger than their limits
	  	int arg0 = 0;
	  	arg0 |= size << 24;
	  	arg0 |= ((offset << 8) & 0x00FFFF00);
	  	arg0 |= (vertexOrFragment & 0x000000FF);
	  	setIntArg(0, index, arg0);

	  	return index;
    }

    public void pushConstant(long pipelineLayout, int vertexOrFragment, int offset, float val) {
		int index = pushConstant(pipelineLayout, vertexOrFragment, offset, 4);

		setLongArg(1, index, Float.floatToIntBits(val) & 0xFFFFFFFFL);

        cmdID.set(index, CMD_PUSH_CONSTANT);
        wakeThread(index);
    }

    public void pushConstant(long pipelineLayout, int vertexOrFragment, int offset, float val0, float val1) {
		int index = pushConstant(pipelineLayout, vertexOrFragment, offset, 8);

		setLongArg(1, index, (Float.floatToIntBits(val0) & 0xFFFFFFFFL) | ((Float.floatToIntBits(val1) & 0xFFFFFFFFL) << 32));

        cmdID.set(index, CMD_PUSH_CONSTANT);
        wakeThread(index);
    }

    public void pushConstant(long pipelineLayout, int vertexOrFragment, int offset, float val0, float val1, float val2) {
		int index = pushConstant(pipelineLayout, vertexOrFragment, offset, 12);

		setLongArg(1, index, (Float.floatToIntBits(val0) & 0xFFFFFFFFL) | ((Float.floatToIntBits(val1) & 0xFFFFFFFFL) << 32));
		setLongArg(2, index, (Float.floatToIntBits(val2) & 0xFFFFFFFFL));

        cmdID.set(index, CMD_PUSH_CONSTANT);
        wakeThread(index);
    }

    public void pushConstant(long pipelineLayout, int vertexOrFragment, int offset, float val0, float val1, float val2, float val3) {
		int index = pushConstant(pipelineLayout, vertexOrFragment, offset, 16);

		setLongArg(1, index, (Float.floatToIntBits(val0) & 0xFFFFFFFFL) | ((Float.floatToIntBits(val1) & 0xFFFFFFFFL) << 32));
		setLongArg(2, index, (Float.floatToIntBits(val2) & 0xFFFFFFFFL) | ((Float.floatToIntBits(val3) & 0xFFFFFFFFL) << 32));

        cmdID.set(index, CMD_PUSH_CONSTANT);
        wakeThread(index);
    }


    public void bufferData(long srcBuffer, long dstBuffer, int size) {
        int index = getNextCMDIndex();
        setLongArg(0, index, srcBuffer);
        setLongArg(1, index, dstBuffer);
        setIntArg(0, index, size);
        // Remember, last thing we should set is cmdID, set it before and
        // our thread may begin executing drawArrays without all the commands
        // being properly set.
        cmdID.set(index, CMD_BUFFER_DATA);
        wakeThread(index);
    }

    public void bindPipeline(long pipeline) {
    	if (currentPipeline != pipeline) {
	        int index = getNextCMDIndex();
			println("call CMD_BIND_PIPELINE (index "+index+")");
			currentPipeline = pipeline;
			setLongArg(0, index, pipeline);
	        cmdID.set(index, CMD_BIND_PIPELINE);
	        wakeThread(index);
    	}
    }


	public void beginRecord(int currentFrame, int currentImage) {
		println("call begin record");
        int index = getNextCMDIndex();
        setIntArg(0, index, currentImage);
        setIntArg(1, index, currentFrame);
        cmdID.set(index, CMD_BEGIN_RECORD);

        wakeThread(index);
	}

	public void endRecord() {
        int index = getNextCMDIndex();
        cmdID.set(index, CMD_END_RECORD);
		println("call CMD_END_RECORD (index "+index+")");
        // No arguments
        wakeThread(index);
		currentPipeline = 0;
	}

	public void kill() {
		println("kill thread");
        int index = getNextCMDIndex();
        cmdID.set(index, CMD_KILL);
        // No arguments
        wakeThread(index);
	}

	public void killAndCleanup() {
		kill();
		// Wait for thread to end
		while (threadState.get() != STATE_KILLED) {
			// Do the classic ol "wait 1ms each loop"
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
		}
		// Now clean up our mess

		if (openCmdBuffer.get() == true) {
			vkEndCommandBuffer(cmdbuffers[currentFrame.get()]);
		}

		try(MemoryStack stack = stackPush()) {
			ArrayList<VkCommandBuffer> deleteList = new ArrayList<>();
			for (int i = 0; i < cmdbuffers.length; i++) {
				deleteList.add(cmdbuffers[i]);
			}
			vkFreeCommandBuffers(system.device, commandPool, Util.asPointerBuffer(stack, deleteList));
		}
		vkDestroyCommandPool(system.device, commandPool, null);
	}

	public VkCommandBuffer getBuffer() {
		return cmdbuffers[currentFrame.get()];
	}

	public void await() {
		int count = 0;
		// We wait until it has finished its commands

		// In order for the thread to be properly done its work it must:
		// - be in sleep mode
		// - its cmd buffer must be closed
		while (
				!(threadState.get() == STATE_SLEEPING &&
				openCmdBuffer.get() == false)
				) {

			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
			if (count == 500) {
				System.err.println("("+this.myID+") BUG WARNING  looplock'd waiting for a thread that won't respond (state "+threadState.get()+")");
			}
			count++;
		}
	}
}