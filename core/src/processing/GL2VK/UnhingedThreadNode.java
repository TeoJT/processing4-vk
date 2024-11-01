package processing.GL2VK;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT16;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdDraw;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexed;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VkPhysicalDeviceIndexTypeUint8FeaturesKHR.INDEXTYPEUINT8;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

public class UnhingedThreadNode extends ThreadNode {

    public UnhingedThreadNode(VulkanSystem vk, int id) {
      super(vk, id);
    }

    @Override
    protected void startThread() {
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


//                  if (threadState.get() == STATE_WAKING) {
//                    if (id == NO_CMD) System.err.println(myID+" NO_CMD warning");
//                    threadState.set(STATE_RUNNING);
//                  }
//                  Util.beginTmr();


                  // ======================
                  // CMD EXECUTOR
                  // ======================


                  threadState.set(STATE_NEXT_CMD);

                  switch (cmdID.getAndSet(index, 0)) {
                  case NO_CMD:
                    threadState.set(STATE_RUNNING);
                    myIndex--;
                    break;
                  case CMD_DRAW_ARRAYS: {
                    threadState.set(STATE_RUNNING);
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
//                          vkCmdBindPipeline(cmdbuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, system.graphicsPipeline);
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

                          // RATIO
//                          System.out.println("("+myID+") Sleep time "+(sleepTime/1000L)+"us  Run time "+(runTime/1000L)+"us");

//                          System.out.println("("+myID+") Active-to-sleep ratio "+(int)((((double)runTime)/((double)(runTime+sleepTime)))*100d)+"%");

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

//                    vkCmdEndRenderPass(system.currentCommandBuffer);
                    system.copyBufferFast(cmdbuffer, cmdLongArgs[0].get(index), cmdLongArgs[1].get(index), cmdIntArgs[0].get(index));
//                    vkCmdBeginRenderPass(system.currentCommandBuffer, system.renderPassInfo, VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS);
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

//                    System.out.println("expected type: "+VK_INDEX_TYPE_UINT16+"  type: "+vkType+"  offset: "+offset+"  indicesSize: "+indiciesSize+"  numBuffers: "+numBuffers+"  indicesBuffer: "+indicesBuffer);



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
//                    size = ((size/8))*8;

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



    @Override
    protected void wakeThread(int cmdindex) {
      if (cmdID.get(cmdindex) == NO_CMD) {
        return;
      }

      if (threadState.get() == STATE_SLEEPING || threadState.get() == STATE_ENTERING_SLEEP) {
        println("INTERRUPT");

        // We need to set status for only one interrupt otherwise we will keep calling
        // interrupt interrupt interrupt interrupt interrupt interrupt interrupt interrupt
        // and it seems to be stored in some sort of queue. That means, when the thread tries
        // to go back to sleep, it immediately wakes up because those interrupts are still in
        // the queue. We tell it "it's been interrupted once, don't bother it any further."
        threadState.set(STATE_SLEEPING_INTERRUPTED);
        thread.interrupt();
      }
    }
}
