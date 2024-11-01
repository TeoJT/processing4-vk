package processing.GL2VK;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Pointer;

public class Util {
    public static final int UINT32_MAX = 0xFFFFFFFF;
    public static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL;


    public static PointerBuffer asPointerBuffer(MemoryStack stack, Collection<String> collection) {

        PointerBuffer buffer = stack.mallocPointer(collection.size());

        collection.stream()
                .map(stack::UTF8)
                .forEach(buffer::put);

        return buffer.rewind();
    }

    public static PointerBuffer asPointerBuffer(MemoryStack stack, List<? extends Pointer> list) {

        PointerBuffer buffer = stack.mallocPointer(list.size());

        list.forEach(buffer::put);

        return buffer.rewind();
    }

    public static int clamp(int min, int max, int value) {
        return Math.max(min, Math.min(max, value));
    }


    public static String readFile(String relativepath) {
    	File f = new File(".");
    	String path = f.getAbsolutePath().replaceAll("\\\\", "/");
    	path = path.substring(0, path.length()-1);
    	try {
			return new String(Files.readAllBytes(Paths.get(path+relativepath)));
		} catch (IOException e) {
			System.err.println("ERROR IOException "+e.getMessage());
			return "";
		}
    }



  public static HashSet<String> disableList = new HashSet<>();

  static {
    disableList.add("useProgram");
    disableList.add("uniformMatrix4fv");
    disableList.add("bindBuffer");
    disableList.add("glVertexAttribPointer");
    disableList.add("vertexAttribPointer");
    disableList.add("drawElementsImpl");
    disableList.add("drawElements");

    disableList.add("submitAndPresent");
    disableList.add("endrecords");
    disableList.add("awaits");
    disableList.add("executeCommands");
    disableList.add("endRenderpass");

  }

	// Lil debugging tools here
	private static long tmrnbefore = 0L;
	public static void beginTmr() {
		tmrnbefore = System.nanoTime();
	}
	public static long endTmr(String name) {
		long ns = ((System.nanoTime()-tmrnbefore));
		System.out.println(
        name+": "+ns+"ns"+
				(ns > 1000 ? " ("+(ns/1000L)+"us)" : "") +
				(ns > 1000000 ? " ("+(ns/1000000L)+"ms)" : "") +
        (ns > 1000000000 ? " ("+(ns/1000000000L)+"s)" : "")
		);
		return ns;
	}

	public static long endTmrWarn(String name) {
    long ns = ((System.nanoTime()-tmrnbefore));

    String msg =
        name+": "+ns+"ns"+
        (ns > 1000 ? " ("+(ns/1000L)+"us)" : "") +
        (ns > 1000000 ? " ("+(ns/1000000L)+"ms)" : "") +
        (ns > 1000000000 ? " ("+(ns/1000000000L)+"s)" : "");

    if (ns > 1000000L) {
      System.err.println(msg);
    }
    else {
      System.out.println(msg);
    }

    return ns;
  }

	public static int roundToMultiple8(int input) {
		// let's say we have 22
		// return 24
		int m = 8;
		int mod = (input%m);
		if (mod == 0) return input;
		return input+(m-mod);
	}

	public static void emergencyExit(String... mssg) {
		System.err.println("GL2VK EMERGENCY EXIT");
		for (String s : mssg) {
			System.err.println(s);
		}
		System.exit(1);
	}
}
