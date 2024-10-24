package processing.GL2VK;

import java.util.ArrayList;

public class UniformParser {


	public static ArrayList<GLUniform> parseUniforms(String shaderSource) {

		// Split into array of lines
		String[] lines = shaderSource.split("\n");

		ArrayList<GLUniform> uniforms = new ArrayList<>();

		// Set to true while we're inside of a uniform struct.
		// While in this state, any variables (i.e. items that start with
		// vec3, float, mat4 etc) will be added as uniforms with their names.
		boolean uniformStruct = false;

		int bracketDepth = 0;
		for (String s : lines) {

			s = filterComments(s);

			// Here we're going to do a comparison to see if we're exiting struct
			int beforeBracketDepth = bracketDepth;

			// Bracket depth so we only get attribs from outside bodies.
			// You'll never see attribs/uniforms in brackets for example.
			bracketDepth += countChars(s, "{");
			bracketDepth -= countChars(s, "}");


			// If there's a change in bracketDepth to 0 and we're in uniformStruct
			// state, it means we're at the end of the struct block.
			if (
					bracketDepth != beforeBracketDepth &&
					bracketDepth == 0 &&
					uniformStruct
				) {
				uniformStruct = false;
			}

			// If we're in the state of looking for uniforms
			if (uniformStruct) {
				// If it's got a variable type in it, that's a uniform.
				if (hasType(s)) {
					// Try/catch block because there's probably cases where it will crash
					// but it seriously doesn't matter if it breaks.
					try {

						// Lil filtering
						// Elements makes it easier to get each individual token.
						String[] elements = s.replaceAll("\t", "").trim().split(" ");
						String line = s.replaceAll(" ", "");
						int offset = -1;

						// layout(offset ... )
						int startIndex = line.indexOf("layout(offset=");
						if (startIndex != -1) {
							int endBracked = line.indexOf(')');

							startIndex += "layout(offset=".length();

							// Remember if something odd happens here, the try/catch block will catch it.
							offset = Integer.parseInt(line.substring(startIndex, endBracked));
						}

						// Look for the element which contains the size.
						// Remember, if we have "layout( offset = ..."
						// we're going to have a list of
						// ["layout(", "offset" "=" ... ]
						int size = -1;
						for (int i = 0; i < elements.length; i++) {
//							System.out.println(elements[i]);
							size = typeToSize(elements[i]);
							// Size found? Element that follows?
							// Time to assign our variable.
							if (size != -1 && i+1 < elements.length) {
								// Next element should be name
								String uniformName = removeSemicolon(elements[i+1]);

								// Remove ';' at the end

								// Unfortunately, we can't assign a uniform yet since the vertex and fragment
								// uniforms need to be combined into one, and here if we try to assign an offset,
								// currUniformOffset starts at 0 for fragment uniforms, which is incorrect.
								// We will assign the offset later.

								// HOWEVER,
								// If we have a layout(offset=...) then we know the offset and can
								// set it directly. Later code will see that offset has already been
								// assigned and won't assign it automatically.
								if (offset != -1) {
									uniforms.add(new GLUniform(uniformName, size, offset));
								}
								else {
									uniforms.add(new GLUniform(uniformName, size));
								}
								break;
							}
						}
						continue;
					}
					catch (RuntimeException e) {
						System.out.println(e.getMessage());
					}
				}
			}


			// Uniforms
//			System.out.println((bracketDepth == 0) + " " + s.contains(" uniform "));
			if (
//					bracketDepth == 0 &&       // Not sure if this will break anything but I don't think it should...?
					s.contains(" uniform ")
			) {
				// Lil filtering
				// Elements makes it easier to get each individual token.
				String[] elements = s.split(" ");
				// No spaces allowed.
				String line = s.replaceAll(" ", "");


				// Get the type of uniform (push constant or descriptor)
				int pushConstantIndex = line.indexOf("push_constant");

				// TODO: Descriptor layouts.
				// Impossible (always false) condition here.
				int descriptorIndex = line.indexOf("(([amongus]))");

				// PUSH CONSTANT PATH
				if (pushConstantIndex != -1) {

					// Prolly don't need that.
					String structName = "";

					// Scroll until we find (uniform)
					try {
						// Search for the element "in".
						for (int i = 0; i < elements.length; i++) {
							if (elements[i].equals("uniform")) {
								// The element after that should be the struct name.
								structName = elements[i+1];

								// We shall start to traverse the struct adding any variable we come
								// across as uniforms
								uniformStruct = true;
								break;
							}
						}
					}
					catch (IndexOutOfBoundsException e) {
//						System.err.println("Woops");
					}
					continue;
				}
				// Descriptor path
				else if (descriptorIndex != -1) {
					continue;
				}
				// Unknown uniform type.
				else {
					// No continue because the line might have something else for us to scan.
				}
			}
		}
		return uniforms;
	}

	public static int countChars(String line, String c) {
		return line.length() - line.replace(c, "").length();
	}

	public static String removeSemicolon(String line) {
		if (line.charAt(line.length()-1) == ';') line = line.substring(0, line.length()-1);
		return line;
	}

	public static int typeToSize(String val) {
		if (val.equals("float")) return 1 * Float.BYTES;
		else if (val.equals("vec2")) return 2 * Float.BYTES;
		else if (val.equals("vec3")) return 3 * Float.BYTES;
		else if (val.equals("vec4")) return 4 * Float.BYTES;
		else if (val.equals("int")) return 1 * Integer.BYTES;
		else if (val.equals("ivec2")) return 2 * Integer.BYTES;
		else if (val.equals("ivec3")) return 3 * Integer.BYTES;
		else if (val.equals("ivec4")) return 4 * Integer.BYTES;
		else if (val.equals("uint")) return 1 * Integer.BYTES;
		else if (val.equals("uvec2")) return 2 * Integer.BYTES;
		else if (val.equals("uvec3")) return 3 * Integer.BYTES;
		else if (val.equals("uvec4")) return 4 * Integer.BYTES;
		else if (val.equals("bool")) return 1;
		else if (val.equals("bvec2")) return 2;
		else if (val.equals("bvec3")) return 3;
		else if (val.equals("bvec4")) return 4;
		else if (val.equals("mat2")) return 2 * 2 * Float.BYTES;
		else if (val.equals("mat3")) return 3 * 3 * Float.BYTES;
		else if (val.equals("mat4")) return 4 * 4 * Float.BYTES;
		else return -1;
	}

	public static boolean hasType(String val) {
		if (val.contains("float")) return true;
		else if (val.contains("vec2")) return true;
		else if (val.contains("vec3")) return true;
		else if (val.contains("vec4")) return true;
		else if (val.contains("int")) return true;
		else if (val.contains("ivec2")) return true;
		else if (val.contains("ivec3")) return true;
		else if (val.contains("ivec4")) return true;
		else if (val.contains("uint")) return true;
		else if (val.contains("uvec2")) return true;
		else if (val.contains("uvec3")) return true;
		else if (val.contains("uvec4")) return true;
		else if (val.contains("bool")) return true;
		else if (val.contains("bvec2")) return true;
		else if (val.contains("bvec3")) return true;
		else if (val.contains("bvec4")) return true;
		else if (val.contains("mat2")) return true;
		else if (val.contains("mat3")) return true;
		else if (val.contains("mat4")) return true;
		else return false;
	}

	// TODO: We also need for the /* */ comments
	private static String filterComments(String line) {
		int index = line.indexOf("//");
		if (index != -1) {
			return line.substring(index);
		}
		return line;
	}
}
