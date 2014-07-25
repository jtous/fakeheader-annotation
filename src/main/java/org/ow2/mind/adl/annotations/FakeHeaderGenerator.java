/**
 * Copyright (C) 2012 Schneider Electric
 *
 * This file is part of "Mind Compiler" is free software: you can redistribute
 * it and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: mind@ow2.org
 *
 * Authors: Julien TOUS
 * Contributors: Stéphane Seyvoz
 */

package org.ow2.mind.adl.annotations;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.objectweb.fractal.adl.ADLException;
import org.objectweb.fractal.adl.Definition;
import org.objectweb.fractal.adl.Loader;
import org.objectweb.fractal.adl.interfaces.Interface;
import org.objectweb.fractal.adl.interfaces.InterfaceContainer;
import org.objectweb.fractal.adl.types.TypeInterface;
import org.objectweb.fractal.adl.util.FractalADLLogManager;
import org.ow2.mind.adl.FlagExtractor;
import org.ow2.mind.adl.ast.ASTHelper;
import org.ow2.mind.adl.ast.Attribute;
import org.ow2.mind.adl.ast.AttributeContainer;
import org.ow2.mind.adl.ast.Data;
import org.ow2.mind.adl.ast.ImplementationContainer;
import org.ow2.mind.adl.ast.MindInterface;
import org.ow2.mind.adl.ast.Source;
import org.ow2.mind.adl.implementation.ImplementationLocator;
import org.ow2.mind.compilation.CompilerContextHelper;
import org.ow2.mind.idl.ast.ArrayOf;
import org.ow2.mind.idl.ast.ConstantDefinition;
import org.ow2.mind.idl.ast.EnumDefinition;
import org.ow2.mind.idl.ast.EnumReference;
import org.ow2.mind.idl.ast.IDL;
import org.ow2.mind.idl.ast.Include;
import org.ow2.mind.idl.ast.IncludeContainer;
import org.ow2.mind.idl.ast.InterfaceDefinition;
import org.ow2.mind.idl.ast.Method;
import org.ow2.mind.idl.ast.Parameter;
import org.ow2.mind.idl.ast.PointerOf;
import org.ow2.mind.idl.ast.PrimitiveType;
import org.ow2.mind.idl.ast.StructDefinition;
import org.ow2.mind.idl.ast.StructReference;
import org.ow2.mind.idl.ast.Type;
import org.ow2.mind.idl.ast.TypeCollectionContainer;
import org.ow2.mind.idl.ast.TypeDefReference;
import org.ow2.mind.idl.ast.TypeDefinition;
import org.ow2.mind.idl.ast.UnionDefinition;
import org.ow2.mind.idl.ast.UnionReference;
import org.ow2.mind.io.OutputFileLocator;
import org.ow2.mind.InputResourceLocator;
import org.ow2.mind.PathHelper;
import org.ow2.mind.idl.IDLLoader;

import com.google.inject.Inject;
import com.google.inject.name.Named;


public class FakeHeaderGenerator {
	/**
	 * Key used for Named Google Guice binding
	 */
	public static final String FAKE_HEADER = "FakeHeader";

	@Inject
	@Named(FAKE_HEADER)
	public ImplementationLocator implementationLocatorItf;

	@Inject
	public OutputFileLocator outputFileLocatorItf;

	@Inject
	FlagExtractor flagExtractorItf;

	@Inject
	Loader adlLoaderItf;

	@Inject
	IDLLoader idlLoaderItf;

	@Inject
	InputResourceLocator inputResourceLocatorItf;

	protected static Logger logger = FractalADLLogManager.getLogger("annotations");

	/**
	 * Generate a Path-like string for an header file to be generated from an interface. 
	 * @param itf The interface to use as reference.
	 * @return The Path-like string of the header file.
	 */
	private String itf2h(MindInterface itf, Map<Object, Object> context){
		String headerFileName = null;
		try {
			IDL idl = idlLoaderItf.load(itf.getSignature(), context);
			if (idl.getName().startsWith("/")) { //FIXME : Why this really needed ???
				headerFileName = PathHelper.replaceExtension(idl.getName(), ".itf.h");
			} else {
				headerFileName = PathHelper.fullyQualifiedNameToPath(idl.getName(), ".itf.h");		
			}
		} catch (ADLException e) {
			logger.info("interface " + itf.getName() + " cannot be loaded !");
			e.printStackTrace();
		}
		return headerFileName.substring(1);
	}

	/**
	 * Generate headers for a component definition.
	 * - One header for the definition.
	 * - One header by provided and required interface type.
	 * - One header by source implementation.
	 * 
	 * @param definition The definition to generate header for.
	 * @param context The global context of the compilation.
	 */
	public void writeComponentHeaders(Definition definition, Map<Object, Object> context) {
		try {
			// The File for the header corresponding to the definition, and a PrintWriter to write in it.
			final File adlHeaderFile = outputFileLocatorItf.getCSourceOutputFile(
					PathHelper.fullyQualifiedNameToPath(definition.getName(), "adl.h"), context);
			final PrintWriter adlPrinter=new PrintWriter(adlHeaderFile,"ASCII");

			// The File for the Makefile for this definition, and a PrintWriter to write in it.
			final File makeFile = outputFileLocatorItf.getCSourceOutputFile(
					PathHelper.fullyQualifiedNameToPath(definition.getName(), "make"), context);
			final PrintWriter makePrinter= new PrintWriter(makeFile,"ASCII");

			// Head of the Makefile 
			// target name and "all" alias. The compile rules will be written below.
			makePrinter.println("all : " + definition.getName());
			makePrinter.println( definition.getName() + " : ");

			// Head of the adl header.
			// Include guard, definition name, and common includes.
			openIncludeGuard(definition.getName(), adlPrinter);
			adlPrinter.println("#include \"mindcommon.h\"");
			adlPrinter.println();
			adlPrinter.println("#define DEFINITION_NAME " + definition.getName().replace(".", "_"));
			adlPrinter.println();
			adlPrinter.println("#include \"commonMacro.h\"");
			adlPrinter.println();

			// Interfaces actions.
			if (definition instanceof InterfaceContainer) {
				Interface[] interfaces = ((InterfaceContainer) definition).getInterfaces();

				// Server interfaces.
				adlPrinter.println("/* Begin server interface listing */");
				for (Interface itf : interfaces) {
					MindInterface mindItf = (MindInterface) itf;
					if (mindItf.getRole().equals(TypeInterface.SERVER_ROLE)) {
						// Create a header for the interface, and include it.
						final String headerFileName = itf2h(mindItf, context);
						writeItfHeader(mindItf, context);
						adlPrinter.println("#include \"" + headerFileName + "\"" );
						// Variable definition for the interface and its size.
						//FIXME collection interface, interface method initialization UNDONE.
						adlPrinter.println(itf2type(mindItf) + " GET_MY_INTERFACE(" + mindItf.getName() + ");");
						adlPrinter.println("int GET_COLLECTION_SIZE(" + mindItf.getName() + ") = " + Math.abs(ASTHelper.getNumberOfElement(mindItf)) +";");
						// Prototype declaration of the interface methods
						methDeclare(mindItf, adlPrinter, context);
					}
				}
				adlPrinter.println("/* End server interface listing */");
				adlPrinter.println();

				// Client interfaces.
				adlPrinter.println("/* Begin client interface listing */");
				for (Interface itf : interfaces) {	
					MindInterface mindItf = (MindInterface) itf;
					if (mindItf.getRole().equals(TypeInterface.CLIENT_ROLE)) {
						// Create a header for the interface, and include it.
						final String headerFileName = itf2h(mindItf, context);
						adlPrinter.println("#include \"" + headerFileName + "\"" );
						writeItfHeader(mindItf, context);
						// collectionSuffix is filled with array size ([]) if needed or set to empty String
						String collectionSuffix = mindItf.getCardinality();
						String numString = mindItf.getNumberOfElement();
						if ((numString !=null)&&(collectionSuffix.equals("collection"))) {
							collectionSuffix = "[" + numString + "]"; 
						} else {
							collectionSuffix = "";
						}
						// Variable definition for the interface and its size.
						adlPrinter.println("extern " + itf2type(mindItf) + " GET_MY_INTERFACE(" + mindItf.getName() + collectionSuffix  + ");");
						adlPrinter.println("int GET_COLLECTION_SIZE(" + mindItf.getName() + ") = " + Math.abs(ASTHelper.getNumberOfElement(mindItf)) +";");
						// Prototype declaration of the interface methods.
						methDeclare(mindItf, adlPrinter, context);
					}
				}
				adlPrinter.println("/* End client interface listing */");
			}

			// data and source are both gotten form ImplementationContainer.
			if (definition instanceof ImplementationContainer) {
				// data actions
				Data data = ((ImplementationContainer)definition).getData();
				if (data != null) {
					adlPrinter.println();
					adlPrinter.println("/* Begin private data declaration */");
					adlPrinter.println("static ");
					// If the data are inlined.
					final String inlinedData = data.getCCode();
					if (inlinedData != null ) {
						adlPrinter.println(inlinedData);
					}
					// If the data are in a separate file.
					final String fileData = data.getPath();
					if (fileData != null ) {
						adlPrinter.println("#include \"" + fileData.substring(1) + "\"");
					}
					// FIXME What about if data is used both as inlined and separate file ? Doesn't make sense but ...
					adlPrinter.println("/* Begin private data declaration */");
					adlPrinter.println();
				}

				// source actions
				Source[] sources = ((ImplementationContainer)definition).getSources();
				if ((sources != null) && (sources.length != 0)) {
					for (Source source : sources) {
						// Calculate a .h file from the source file and a PrintWriter to write in it.
						String cSrc = source.getPath();
						int index = cSrc.lastIndexOf(".");
						String hSrc = cSrc.substring(0, index)+".impl.h";
						final URL srcURL = implementationLocatorItf.findSource(cSrc, context);
						final File headerFile = outputFileLocatorItf.getCSourceOutputFile( hSrc, context);
						PrintWriter srcPrinter = new PrintWriter(headerFile,"ASCII");

						// source header file only include the adl header, guarded against multiple inclusion.
						openIncludeGuard(headerFile.toPath().toString().replace("/","_"), srcPrinter);
						srcPrinter.println("#include \"" + PathHelper.fullyQualifiedNameToPath(definition.getName(), "adl.h").substring(1) + "\"");
						closeIncludeGuard(headerFile.toPath().toString().replace("/","_"), srcPrinter);
						srcPrinter.close();

						// Begin a compilation rule in the Make file for the source, with generated header passed as pre-included file.
						try {
							makePrinter.print("\t$(CC) -c " + (new File(srcURL.toURI())).getPath() + " -include " + hSrc.substring(1) );
						} catch (URISyntaxException e) {
							makePrinter.print("\t$(CC) -c " + srcURL.getPath() + " -include " + hSrc.substring(1) );
						}
						// Append the project wise compilation flags
						for (final String inc : CompilerContextHelper.getIncPath(context)) {
							makePrinter.print(" -I" + inc + " ");
						}
						List<String> cppflags = CompilerContextHelper.getCPPFlags(context);
						for (final String cppflag : cppflags) {
							makePrinter.print(" " + cppflag  + " " );
						}
						List<String> cflags = CompilerContextHelper.getCFlags(context);
						for (final String cflag : cflags) {
							makePrinter.print(" " + cflag  + " " );
						}

						try {
							// Append the definition wise compilation flags
							for (final String cflag : flagExtractorItf.getCPPFlags(definition, context) )
								makePrinter.print(" " + cflag  + " " );
							for (final String cflag : flagExtractorItf.getCFlags(definition, context) ) 
								makePrinter.print(" " + cflag  + " " );
							for (final String cflag : flagExtractorItf.getASFlags(definition, context) )
								makePrinter.print(" " + cflag  + " " );
							// Append the source wise compilation flags
							for (final String cflag : flagExtractorItf.getCPPFlags(source, context) )
								makePrinter.print(" " + cflag  + " " );
							for (final String cflag : flagExtractorItf.getCFlags(source, context) ) 
								makePrinter.print(" " + cflag  + " " );
							for (final String cflag : flagExtractorItf.getASFlags(source, context) )
								makePrinter.print(" " + cflag  + " " );
						} catch (ADLException e1) {
							logger.info("Problem extracting the compilation flags !");
							e1.printStackTrace();
						}

						// Append the include paths
						final URL[] inputResourceRoots = inputResourceLocatorItf.getInputResourcesRoot(context);
						if (inputResourceRoots != null) {
							for (final URL inputResourceRoot : inputResourceRoots) {
								File inputDir;
								try {
									inputDir = new File(inputResourceRoot.toURI());
									if (inputDir.isDirectory()) {
										makePrinter.print(" -I" + inputDir.getPath());
									}
								} catch (URISyntaxException e) {
									makePrinter.print(" -I" + inputResourceRoot.getPath());
								}
							}
						}

					}
				}
			}

			// attribute actions
			if (definition instanceof AttributeContainer) {
				Attribute[] attributes = ((AttributeContainer)definition).getAttributes();
				if ((attributes != null) && (attributes.length != 0)) {
					// Create a struct, a typedef and a variable to hold the attributes.
					adlPrinter.println();
					adlPrinter.println("/* Begin attributes declaration */");
					adlPrinter.println("struct " + def2type(definition) +"_attribue_s {");
					for (Attribute attribute : attributes) {
						adlPrinter.println(attribute.getType() + " " + attribute.getName() + ";");
						//FIXME no initialization is done.
					}
					adlPrinter.println("};");
					adlPrinter.println("typedef struct " +  def2type(definition) +"_attribue_s " + def2type(definition) +"_attribue_t;");
					adlPrinter.println("static " + def2type(definition) +"_attribue_t ATTRIBUTE_STRUCT_NAME;");
					adlPrinter.println("/* End attributes declaration */");
					adlPrinter.println();
				}
			}

			// Closing the adl header and Makefile.
			closeIncludeGuard(definition.getName(),adlPrinter);
			adlPrinter.close();
			makePrinter.close();
		} catch (FileNotFoundException e) {
			logger.info("Somehow calculated file path are wrong this is a BUG  !");
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			logger.info("ASCII encoding is not supported on your platform !");
			e.printStackTrace();
		}
	}

	/**
	 * Write a header corresponding to an interface.
	 * @param itf
	 * @param context
	 */
	private void writeItfHeader(MindInterface itf, Map<Object, Object> context) {
		try {
			// Creating a File and a PrintWriter to write in it
			final File headerFile = outputFileLocatorItf.getCSourceOutputFile(
					PathHelper.fullyQualifiedNameToPath(itf.getSignature(), "itf.h"), context);
			PrintWriter itfWriter = new PrintWriter(headerFile,"ASCII");
			openIncludeGuard(itf.getSignature(), itfWriter);
			try {
				IDL idl = idlLoaderItf.load(itf.getSignature(), context);
				// Propagating the include directive
				if (idl instanceof IncludeContainer) {
					Include[] includes = ((IncludeContainer)idl).getIncludes();
					if ((includes != null) && (includes.length !=0)){
						itfWriter.println();
						itfWriter.println("/* Begin includes imported from interface */");
						for (Include include : includes) {
							String incPath = include.getPath();
							//Strip the leading annoying / from the include
							if (incPath.startsWith("\"")) incPath = "\"" + incPath.substring(2);
							itfWriter.println("#include " + incPath );
						}
						itfWriter.println("/* End includes imported from interface */");
						itfWriter.println();
					}
				}	
				
				// propagating types defined directly in the .itf
				if (idl instanceof TypeCollectionContainer) {
					for (final Type type : ((TypeCollectionContainer) idl).getTypes()) {
						if (type instanceof TypeDefinition)
							itfWriter.println("typedef " + typeToString(((TypeDefinition)type).getType()) + " " + typeToString(type) + ";");
					}
				}

				// define a struct and a typedef for the interface.
				if (idl instanceof InterfaceDefinition) {
					Method[] meths = ((InterfaceDefinition)idl).getMethods();
					if ((meths != null) && (meths.length !=0)){
						itfWriter.println();
						itfWriter.println("/* Begin interface type definition */");
						itfWriter.println("struct " + itf2type(itf) + "_s {");
						for (Method meth : meths) {
							itfWriter.print("\t" + typeToString(meth.getType()) + " (*" + meth.getName() + ")(" );
							Parameter[] parameters = meth.getParameters();
							writeCFunctionParameters(parameters,itfWriter);
							itfWriter.println(");");
						}
						itfWriter.println("};" );
						itfWriter.println();
						itfWriter.println("typedef struct " + itf2type(itf) + "_s " + itf2type(itf) + ";");
						itfWriter.println("/* End interface type definition */");
						itfWriter.println();
					}
				}
			} 
			catch (ADLException e) {
				logger.info("Interface cannot be resolved to a IDL !");
				e.printStackTrace();
			}
			// Close the interface header file.
			closeIncludeGuard(itf.getSignature(), itfWriter);
			itfWriter.close();

		} catch (FileNotFoundException e) {
			logger.info("Somehow calculated file path are wrong this is a BUG  !");
			e.printStackTrace();
		} catch (UnsupportedEncodingException e1) {
			logger.info("ASCII encoding is not supported on your platform !");
			e1.printStackTrace();
		}		
	}

	/**
	 * Create prototypes of the methods of an interface.
	 * @param itf The interface to be prototyped.
	 * @param writer The PrintWriter where to put write the prototypes. 
	 * @param context The global compilation context.
	 */
	private void methDeclare(MindInterface itf, PrintWriter writer, Map<Object, Object> context) {
		try {
			IDL idl = idlLoaderItf.load(itf.getSignature(), context);
			if (idl instanceof InterfaceDefinition) {
				Method[] meths = ((InterfaceDefinition)idl).getMethods();
				if ((meths != null) && (meths.length !=0)){
					writer.println();
					writer.println("/* Begin METH declaration */");
					// Mangling is left out for the C preprocessor 
					for (Method meth : meths) {
						writer.print(typeToString(meth.getType()) + " METH(" + itf.getName() + ", " + meth.getName() + ")(" );
						Parameter[] parameters = meth.getParameters();
						writeCFunctionParameters(parameters,writer);
						writer.println(");");
					}
					writer.println("/* End  METH declaration */");
					writer.println();
				}
			}
		} catch (ADLException e) {
			logger.info("Interface cannot be resolved to a IDL !");
			e.printStackTrace();
		}
	}

	/**
	 * Layout the C function parameters.
	 * @param parameters an array of Parameter.
	 * @param writer The writer to write in.
	 */
	private void writeCFunctionParameters(Parameter[] parameters, PrintWriter writer) {
		// paramDelimiter is put in front of parameters. 
		// The first is a space the next are comas (space param coma param)
		String paramDelimiter = " ";
		if ((parameters != null) && (parameters.length != 0)) {
			for (Parameter parameter : parameters) {
				writer.print(paramDelimiter + typeToString(parameter.getType()) + " " + parameter.getName());
				paramDelimiter = ", ";
			}
		} else {
			writer.print("void");
		}
	}
	
	/**
	 * get a C type from an interface instance. 
	 * @param itf The interface.
	 * @return The C type name.
	 */
	private String itf2type(MindInterface itf) {
		return itf.getSignature().replace(".", "_");
	}

	/**
	 * get a C type from a definition. 
	 * @param def The definition.
	 * @return The C type name.
	 */
	private String def2type(Definition def) {
		return def.getName().replace(".", "_");
	}

	/**
	 * Typical C include guard header from fully qualified like Strings
	 * @param name The fully qualified name of the element to guard.
	 * @param writer The PrintWriter to write in.
	 */
	static private void openIncludeGuard(String name, PrintWriter writer) {
		final String macroName = name.replace(".", "_").toUpperCase();
		writer.println("#ifndef " + macroName);
		writer.println("#define " + macroName);
		writer.println();
	}
	
	/**
	 * Typical C include guard footer from fully qualified like Strings
	 * @param name The fully qualified name of the element to guard.
	 * @param writer The PrintWriter to write in.
	 */
	static private void closeIncludeGuard(String name, PrintWriter writer) {
		final String macroName = name.replace(".", "_").toUpperCase();
		writer.println();
		writer.println("#endif /* " + macroName + " */");
		writer.println();
	}
	
	/**
	 * Shamelessly copied from OptimCPLChecker
	 */
	private String typeToString(Type type) {
		if (type instanceof EnumDefinition) {
			return ((EnumDefinition) type).getName();
		} else if (type instanceof EnumReference) {
			return ((EnumReference) type).getName();
		} else if (type instanceof StructDefinition) {
			return ((StructDefinition) type).getName();
		} else if (type instanceof StructReference) {
			return ((StructReference) type).getName();
		} else if (type instanceof UnionDefinition) {
			return ((UnionDefinition) type).getName();
		} else if (type instanceof UnionReference) {
			return ((UnionReference) type).getName();
		} else if (type instanceof TypeDefinition) {
			return ((TypeDefinition) type).getName();
		} else if (type instanceof TypeDefReference) {
			return ((TypeDefReference) type).getName();
		} else if (type instanceof ConstantDefinition) {
			return ((ConstantDefinition) type).getName();
		} else if (type instanceof PrimitiveType) {
			return ((PrimitiveType) type).getName();
		} else if (type instanceof ArrayOf) {
			// TODO:see IDL2C.stc arrayOfVarName for cleaner handling
			return typeToString(((ArrayOf) type).getType()) + " * "; 
		} else if (type instanceof PointerOf) {
			return typeToString(((PointerOf) type).getType()) + " * ";
		} else return ""; // TODO: check even if this should never happen, or raise an error
	}
}
