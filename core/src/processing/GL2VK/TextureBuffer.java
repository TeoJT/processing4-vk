package processing.GL2VK;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;

import java.nio.LongBuffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

public class TextureBuffer {

  protected static VulkanSystem system;
  protected static VKSetup vkbase;

  private int bufferCount = 0;

  public long texture = -1;
  protected volatile long textureMemory = -1;

  public long stagingBuffer = -1;
  public long stagingBufferMemory = -1;

  boolean initialized = false;


  public TextureBuffer(VulkanSystem s) {
    system = s;
    vkbase = s.vkbase;
  }

  // Debug mode constructor
  public TextureBuffer() {

  }

  public void createTextureBuffer(int width, int height) {
    // TODO: For testing purposes.
    if (initialized) return;

    try(MemoryStack stack = stackPush()) {
        // Info
        VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack);
        imageInfo.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
        imageInfo.imageType(VK_IMAGE_TYPE_2D);
        imageInfo.extent().width(width);
        imageInfo.extent().height(height);
        imageInfo.extent().depth(1);
        imageInfo.mipLevels(1);
        imageInfo.arrayLayers(1);
        imageInfo.format(VK_FORMAT_R8G8B8A8_SRGB);
        imageInfo.tiling(VK_IMAGE_TILING_OPTIMAL);
        imageInfo.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        imageInfo.usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
        imageInfo.samples(VK_SAMPLE_COUNT_1_BIT);
        imageInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        // Actually creating the texture here.
        LongBuffer pTextureImage = stack.callocLong(1);
        if(vkCreateImage(system.device, imageInfo, null, pTextureImage) != VK_SUCCESS) {
          throw new RuntimeException("Failed to create image");
        }
        // Texture now set
        texture = pTextureImage.get(0);

        // Time to get some mem requirements for our texture
        VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc(stack);
        vkGetImageMemoryRequirements(system.device, texture, memRequirements);


        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack);
        allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        allocInfo.allocationSize(memRequirements.size());
        allocInfo.memoryTypeIndex(vkbase.findMemoryType(stack, memRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

        LongBuffer pTextureImageMemory = stack.callocLong(1);
        if(vkAllocateMemory(system.device, allocInfo, null, pTextureImageMemory) != VK_SUCCESS) {
          throw new RuntimeException("Failed to allocate image memory");
        }
        textureMemory = pTextureImageMemory.get(0);

      vkBindImageMemory(system.device, texture, textureMemory, 0);
    }


    // STAGING BUFFER
    try(MemoryStack stack = stackPush()) {
      LongBuffer pBuffer = stack.mallocLong(1);
      LongBuffer pBufferMemory = stack.mallocLong(1);
      int size = width*height*4;

      vkbase.createBuffer(size,
              VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
              VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
              pBuffer,
              pBufferMemory);

      this.stagingBuffer = pBuffer.get(0);
      this.stagingBufferMemory = pBufferMemory.get(0);
    }
    bufferCount++;
    initialized = true;
  }

  public void bufferData() {

  }
}


