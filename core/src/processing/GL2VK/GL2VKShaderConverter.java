package processing.GL2VK;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GL2VKShaderConverter {
	public final static int VERTEX = 1;
	public final static int FRAGMENT = 2;

	// Public so we can check them in tests sorry not sorry.
	public HashMap<String, Integer> vertexVaryingLocations = new HashMap<>();
	public int vertUniformSize = -1;

	// Unused
//	private boolean fragmentBlocked = false;
//	private String blockedFragmentString = "";

	public void reset() {
		vertexVaryingLocations.clear();
		vertUniformSize = -1;
	}


	// To convert the shader from OpenGL glsl to Vulkan GLSL:
	// 1-  append #version 450 if not done already
	// 2-  convert "attribute" keyword to "in"
	// 3-  Keep track and specify location for "in" variables, append "layout(location=...)"
	// 4-  convert "varying" keyword to "out"
	// 5-  Keep track and specify location for "out" variables, append "layout(location=...)"
	// 6-  Collect all uniform variables and put them into a block.
	// 7-  For the fragment shader, append "layout(offset=...)" from the uniform block size of
	//     the vertex shader (unfortunately means we have no choice but to compile vertex programs first)
	// 8-  Replace all instances of uniform names with the structure name e.g. u_time -> uni.u_time
	// 9-  replace gl_FragColor to gl2vkFragColor and add layout(location = 0) out vec4 gl2vkFragColor
	//     in fragment shader.

	// NOTES: we'll need to add some sort of cache to remember varying variables from the vertex
	// to fragment shader. Unfortunately, shaders are compiled at a stage where the pipeline (or "program")
	// isn't present, so it's the best option we've got.
	//
	// This is unfortunately one of the disadvantages of writing a GL to VK interpreter; best write your
	// programs in pure vulkan to begin with.
	//
	// Methods like "clearCache" upon program creation and specifying which shader is vertex and which is
	// fragment can help with ensuring the shaders are linked correctly.
	//
	// It's also important to add proper error checking techniques (perhaps put "glLinkProgram" to use)
	// to properly check if the cache from vertex to fragment, or fragment to vertex were properly found,
	// and if not, halt the program and give a GL2VK warning.
	//
	// On second thought, looking through the requirements, we'll need to compile vertex first then fragment.
	// There's no other way, unless we recompile fragment, which is so much overhead.
	// Yeah, I can really see why a gl-to-vk layer is a bad idea now.
	// And why OpenGL has a big overhead.
	//
	// Also, remember to ignore comments, and switch on ignoremode if we're in a comment block /* */
	public String convert(String source, int type) throws RuntimeException {

		// C1: remove comments
		source = removeComments(source);

		// C2: convert "attrib" to "in" VERTEX ONLY
		if (type == VERTEX) source = attribute2In(source);

		// C3: convert "varying" to out VERTEX ONLY
		if (type == VERTEX) source = varying2Out(source);

		// C4: convert "varying" to in FRAGMENT ONLY
		// May throw exception if a vertex shader hasn't been compiled.
		if (type == FRAGMENT) source = varying2In(source);

		// C5: convert uniforms (put into block, replace instances, add offset to fragment)
		source = convertUniforms(source, type);

		// C6: convert gl_FragColor to gl2vk_FragColor and add out variable.
		if (type == FRAGMENT) source = replaceFragOut(source);

		// C7: Finally, append the version
		source = appendVersion(source);


		return source;
	}

	// Let's just steal UniformParser's hasType method lol
	public static boolean hasType(String val) {
		return UniformParser.hasType(val);
	}

	// Let's steal that too
	public static String removeSemicolon(String input) {
		return UniformParser.removeSemicolon(input);
	}


	// TODO: also filter comment blocks /* */
	private String filterComments(String line) {
		// Single line
		int index = line.indexOf("//");
		if (index != -1) {
			return line.substring(0, index);
		}
		return line;
	}


	public static String spaceOutSymbols(String line) {
		line = line.replaceAll("\\+(?!=)", " + ");
		line = line.replace(",", " , ");
		line = line.replace("==", " == ");
		line = line.replace("+=", " += ");
		line = line.replace("-=", " -= ");
		line = line.replace("*=", " *= ");
		line = line.replace("/=", " /= ");
		line = line.replace(">=", " >= ");
		line = line.replace("<=", " <= ");
		line = line.replace("!=", " != ");
		line = line.replaceAll(">(?!=)", " > "); // Detect > but not >=
		line = line.replaceAll("<(?!=)", " < "); // Detect < but not <=
		line = line.replaceAll("!(?!=)", " ! "); // Detect > but not >=
		line = line.replaceAll("&(?!=)", " & ");  // single &
		line = line.replaceAll("\\|(?!=)", " | ");  // single |
		line = line.replaceAll("-(?!=)", " - ");
		line = line.replaceAll("\\*(?!=)", " * ");
		line = line.replaceAll("\\/(?!=)", " / ");
		line = line.replace("(", "( ");
		line = line.replace(")", " )");

		// Regex failed on me so let's just program it ourselves for ==
		String nline = "";
		char[] arr = line.toCharArray();
		for (int i = 0; i < arr.length; i++) {
			try {
				if (	arr[i-1] != '='
						&& arr[i-1] != '+'
						&& arr[i-1] != '-'
						&& arr[i-1] != '*'
						&& arr[i-1] != '/'
						&& arr[i-1] != '&'
						&& arr[i-1] != '|'

						&& arr[i] == '='
						&& arr[i+1] != '=') {
					nline += " = ";
				} else nline += arr[i];
			}
			catch (RuntimeException e) {
				nline += arr[i];
			}
		}
		line = nline;
		return line;
	}


	// All methods are public so that they can be tested
	public String removeComments(String source) {
		// Split it up
		String[] lines = source.split("\n");
		// Remove comments
		// And then reconstruct it
		String reconstructed = "";
		boolean blockComment = false;
		for (String line : lines) {
			// Single line
			String lineb = filterComments(line)+"\n";

			// Multiline
			for (int i = 0; i < lineb.length(); i++) {
				try {
					if ((lineb.charAt(i) == '/') && (lineb.charAt(i+1) == '*')) {
						blockComment = true;
					}
					else if ((lineb.charAt(i-2) == '*') && (lineb.charAt(i-1) == '/')) {
						blockComment = false;
					}
				}
				catch (IndexOutOfBoundsException e) {

				}

				if (!blockComment) {
					reconstructed += lineb.charAt(i);
				}
			}
		}


		return reconstructed;
	}

	// Very easy
	public String appendVersion(String source) {
		if (!source.contains("#version 450")) {
			source = "#version 450\n"+source;
		}
		return source;
	}

	// IMPORTANT NODE: vertex shaders only
	public String attribute2In(String source) {
		int inLocationIndex = 0;
		String[] lines = source.split("\n");
		String reconstructed = "";
		for (String line : lines) {
			// Variable names could easily contain the word "attribute" in them.
			// That's why we go through and actually check the attribute token


			String[] elements = line.replaceAll("\t", "").trim().split(" ");
			String reconstructedLine = "";
			for (int i = 0; i < elements.length; i++) {
				// Try catch block so I don't need to put index checks everywhere
				try {
					// Now look for "attribute" followed by a type.
					if (elements[i].equals("attribute") && hasType(elements[i+1])) {
						// Append layout variable
						reconstructedLine += "layout(location = "+inLocationIndex+") ";

						// Replace "attribute" with "in"
						reconstructedLine += "in ";
						inLocationIndex++;
					}
					else {
						reconstructedLine += elements[i]+" ";
					}
				}
				catch (RuntimeException e) {
					// Just continue with original line
					reconstructedLine = line;
					break;
				}
			}
			reconstructed += reconstructedLine;

			reconstructed += "\n";
		}
		return reconstructed;
	}


	// IMPORTANT NODE: vertex shaders only
	// Very similar to attribute2In but we're replacing "varying" instead.
	public String varying2Out(String source) {
		int outLocationIndex = 0;
		String[] lines = source.split("\n");
		String reconstructed = "";
		for (String line : lines) {


			String[] elements = line.replaceAll("\t", "").trim().split(" ");
			String reconstructedLine = "";
			for (int i = 0; i < elements.length; i++) {
				// Try catch block so I don't need to put index checks everywhere
				try {
					// Now look for "varying" followed by a type.

					if (elements[i].equals("varying") && hasType(elements[i+1])) {
						// Name should come right after the type
						String name = removeSemicolon(elements[i+2]);

						vertexVaryingLocations.put(name, outLocationIndex);

						reconstructedLine += "layout(location = "+outLocationIndex+") ";

						reconstructedLine += "out ";

						outLocationIndex++;
					}
					else {
						reconstructedLine += elements[i]+" ";
					}
				}
				catch (RuntimeException e) {
					// Just continue with original line
					reconstructedLine = line;
					break;
				}
			}
			reconstructed += reconstructedLine;

			reconstructed += "\n";
		}
		return reconstructed;
	}


	// IMPORTANT NODE: fragment shaders only
	public String varying2In(String source) throws RuntimeException {
		String[] lines = source.split("\n");
		String reconstructed = "";

		for (String line : lines) {


			String[] elements = line.replaceAll("\t", "").trim().split(" ");
			String reconstructedLine = "";
			for (int i = 0; i < elements.length; i++) {
				// Try catch block so I don't need to put index checks everywhere
				try {
					// Now look for "varying" followed by a type.

					if (elements[i].equals("varying") && hasType(elements[i+1])) {
						// Name should come right after the type
						String name = removeSemicolon(elements[i+2]);

						if (!vertexVaryingLocations.containsKey(name)) {
							// We used to have blocking functionality for when a fragment shader is
							// compiled before a vertex.
							// Too complicated, too unnecessary, let's just warn the user.
							Util.emergencyExit(
									"glCompileShader()",
									"One of the following possibilities occured: ",
									"- Syntax error in your vertex/fragment shader",
									"- Fragment shader was compiled before your vertex shader",
									"- Fragment shader was compiled out-of-order from your vertex shader.",
									"  E.g. compile vert0, compile frag1, compile frag0.",
									"In GL2VK, you must compile your vertex/fragment shader pair in order,",
									"one after the other."
									);
							break;
						}

						reconstructedLine += "layout(location = "+vertexVaryingLocations.get(name)+") ";

						reconstructedLine += "in ";
					}
					else {
						reconstructedLine += elements[i]+" ";
					}
				}
				catch (RuntimeException e) {
					// Just continue with original line
					reconstructedLine = line;
					break;
				}
			}

			// Unused scrapped feature.
//			if (block) {
//				fragmentBlocked = true;
//				blockedFragmentString = original;
//				throw new RuntimeException();
//			}
			reconstructed += reconstructedLine;

			reconstructed += "\n";
		}
		return reconstructed;
	}




	public String convertUniforms(String source, int type) {
		// Quick check before doing any processing
		if (type == FRAGMENT && vertUniformSize == -1) {
			// Unused auto-fixing feature
//			fragmentBlocked = true;
//			blockedFragmentString = source;

			Util.emergencyExit(
					"Uniform error",
					"One of the following possibilities occured: ",
					"- Syntax error in your vertex/fragment shader",
					"- Fragment shader was compiled before your vertex shader",
					"- Fragment shader was compiled out-of-order from your vertex shader.",
					"  E.g. compile vert0, compile frag1, compile frag0.",
					"In GL2VK, you must compile your vertex/fragment shader pair in order,",
					"one after the other."
				);

//			throw new RuntimeException();
		}
		else if (type == VERTEX) {
			// Mark as 0 to mark that the vertex has run
			// and to begin counting.
			vertUniformSize = 0;
		}

		// Step 1: get uniforms, erase them
		String[] lines = source.split("\n");

		String reconstructed1 = "";

		HashSet<String> uniformsSet = new HashSet<>();
		ArrayList<String> uniforms = new ArrayList<>();
		for (String line : lines) {

			String reconstructedLine = "";
			String[] elements = line.replaceAll("\t", "").trim().split(" ");
			for (int i = 0; i < elements.length; i++) {
				// Try catch block so I don't need to put index checks everywhere
				try {

					if (elements[i].equals("uniform") && hasType(elements[i+1])) {
						// Erase the line
						reconstructedLine = "";
						// Annnnd let's just slap on element[1 and 2] because that
						// should be the type and name (with semicolon)
						uniforms.add(elements[i+1]+" "+elements[i+2]);

						// Keep track of size on vertex shader
						if (type == VERTEX) vertUniformSize += UniformParser.typeToSize(elements[i+1]);

						// simultaneously populate the hashset
						uniformsSet.add(removeSemicolon(elements[i+2]));

						break;
					}
					else {
						reconstructedLine += elements[i]+" ";
					}
				}
				catch (RuntimeException e) {
					// Just continue with original line
					reconstructedLine = line;
				}
			}
			reconstructed1 += reconstructedLine;

			reconstructed1 += "\n";
		}

		// uniform block code
		// Only bother if we actually have any uniforms
		if (!uniforms.isEmpty()) {
			// And now append the block at the top.
			String block = "layout(push_constant) uniform gltovkuniforms_struct {";
			boolean once = true;
			for (String u : uniforms) {
				// For a fragment shader we need to add offset to go into
				// the constant push fragment range.
				if (once && type == FRAGMENT) {
					block += "\n    layout(offset="+vertUniformSize+") "+u;
					once = false;
				}
				else block += "\n    "+u;
			}
			block += "\n} gltovkuniforms;\n";

			// Add our uniform block to reconstructed
			reconstructed1 = block+reconstructed1;
		}

		String reconstructed2 = "";

		// Let's do it again
		lines = reconstructed1.split("\n");

		// TODO: variable arrays
		for (String line : lines) {

			String reconstructedLine = "";

			line = spaceOutSymbols(line);

			String[] elements = line.replaceAll("\t", "").trim().split(" ");

			for (int i = 0; i < elements.length; i++) {
				// Try catch block so I don't need to put index checks everywhere
				try {

//					System.out.println(elements[i]);
					if (uniformsSet.contains(elements[i])) {
						// Replace with gltovkuniforms.[varname]
						reconstructedLine += "gltovkuniforms."+elements[i];
					}
					else {
						reconstructedLine += elements[i]+" ";
					}
				}
				catch (RuntimeException e) {
					// Just continue with original line
					reconstructedLine = line;
				}
			}
			reconstructed2 += reconstructedLine;

			reconstructed2 += "\n";
		}


		return reconstructed2;
	}




	// IMPORTANT: fragment only
	public String replaceFragOut(String source) {

		String[] lines = source.split("\n");
		String reconstructed = "";

		for (String line : lines) {

			line = spaceOutSymbols(line);

			String[] elements = line.replaceAll("\t", "").trim().split(" ");
			String reconstructedLine = "";
			for (int i = 0; i < elements.length; i++) {
				// No try/catch block this time because no exceptions should happen there

				// This time just one condition
				if (elements[i].equals("gl_FragColor")) {
					reconstructedLine += "gl2vk_FragColor ";
				}
				else {
					reconstructedLine += elements[i]+" ";
				}
			}
			reconstructed += reconstructedLine;

			reconstructed += "\n";
		}

		// Then, we need to add
		// layout(location = 0) out vec4 gl2vk_FragColor;
		// Let's just add it at the top
		if (!reconstructed.contains("layout(location = 0) out vec4 gl2vk_FragColor;")) {
			reconstructed = "layout(location = 0) out vec4 gl2vk_FragColor;\n"+reconstructed;
		}

		return reconstructed;
	}

}