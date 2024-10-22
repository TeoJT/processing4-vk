package processing.GL2VK;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
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


	// Lil debugging tools here
	long tmrnbefore = 0L;
	public void beginTmr() {
		tmrnbefore = System.nanoTime();
	}
	public void endTmr(String name) {
		long us = ((System.nanoTime()-tmrnbefore)/1000L);
		System.out.println(
				name+": "+us+"us"+
				(us > 1000 ? " ("+(us/1000L)+"ms)" : "") +
				(us > 1000000 ? " ("+(us/1000000L)+"s)" : "")
		);
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
