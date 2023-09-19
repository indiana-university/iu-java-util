/*
 * Copyright © 2023 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.type;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import edu.iu.type.IuComponent;

/**
 * Creates component instances for {@link IuComponent}.
 */
public final class ComponentFactory {

	private static IuComponent newModuleJarComponent(Component parent, Manifest manifest, Set<String> classNames,
			Properties pomProperties, Properties typeProperties, Path... modulePath) {
		if (parent != null && parent.controller == null)
			throw new IllegalArgumentException("A component defined by a module cannot extend an unnamed component");

		Map<String, String> openPackagesToModuleName = new LinkedHashMap<>();
		Set<String> moduleNames = new LinkedHashSet<>();
		Consumer<ModuleDescriptor> mapModuleDescriptor = descriptor -> {
			var moduleName = descriptor.name();
			moduleNames.add(moduleName);
			for (var packageName : descriptor.packages())
				openPackagesToModuleName.put(packageName, moduleName);
		};

		var moduleFinder = ModuleFinder.of(modulePath);
		var moduleIterator = moduleFinder.findAll().iterator();
		var componentModuleDescriptor = moduleIterator.next().descriptor();
		mapModuleDescriptor.accept(componentModuleDescriptor);

		while (moduleIterator.hasNext()) {
			var descriptor = moduleIterator.next().descriptor();
			mapModuleDescriptor.accept(descriptor);
			moduleNames.add(descriptor.name());
		}

		ClassLoader parentClassLoader;
		ModuleLayer parentModuleLayer;
		if (parent == null) {
			parentClassLoader = ClassLoader.getPlatformClassLoader();
			parentModuleLayer = ModuleLayer.boot();
		} else {
			parentClassLoader = parent.classLoader();
			parentModuleLayer = parent.controller.layer();
		}

		var configuration = Configuration.resolveAndBind( //
				moduleFinder, List.of(parentModuleLayer.configuration()), ModuleFinder.of(), moduleNames);

		var controller = ModuleLayer.defineModulesWithOneLoader(configuration, List.of(parentModuleLayer),
				parentClassLoader);

		return new Component(parent, controller, manifest, pomProperties.getProperty("artifactId"),
				pomProperties.getProperty("version"), typeProperties,
				controller.layer().findLoader(moduleNames.iterator().next()), classNames);
	}

	private static File temp() throws IOException {
		File temp = File.createTempFile("IuComponent-", ".jar");
		temp.deleteOnExit();
		return temp;
	}

	private static IuComponent newLegacyComponent(Component parent, Set<String> classNames,
			Path componentDefiningPathElement) {
		Queue<URL> classpath = new ArrayDeque<>();
		try {
			File definingJar = temp();
			classpath.offer(definingJar.toURI().toURL());

			byte[] buf = new byte[16384];
			int r;
			try (var out = new FileOutputStream(definingJar); //
					var outJar = new JarOutputStream(out); //
					var in = componentDefiningPathElement.toUri().toURL().openStream(); //
					var inJar = new JarInputStream(in)) {
				JarEntry entry;
				while ((entry = inJar.getNextJarEntry()) != null) {
					var name = entry.getName();
					if (name.startsWith("META-INF/lib/")) {
						var temp = temp();
						try (var libOut = new FileOutputStream(temp)) {
							while ((r = inJar.read(buf, 0, 16384)) > 0)
								libOut.write(buf, 0, r);
						}

						Set<String> libClasses = new LinkedHashSet<>();
						boolean libIuProperties = false;
						try (var libIn = new FileInputStream(temp); //
								var libJar = new JarInputStream(libIn)) {
							JarEntry libEntry;
							while ((libEntry = libJar.getNextJarEntry()) != null) {
								var libName = libEntry.getName();
								if (!libName.equals("module-info.class") //
										&& !name.endsWith("package-info.class") //
										&& libName.endsWith(".class") //
										&& name.indexOf('$') == -1)
									libClasses.add(name.substring(0, name.length() - 6).replace('/', '.'));
								else if (name.equals("META-INF/iu.properties"))
									libIuProperties = true;
							}
						}

						if (libIuProperties)
							classNames.addAll(libClasses);

						classpath.add(temp.toURI().toURL());
					} else {
						JarEntry outEntry = new JarEntry(name);
						outJar.putNextEntry(outEntry);
						while ((r = inJar.read(buf, 0, 16384)) > 0)
							outJar.write(buf, 0, r);
						outJar.flush();
						outJar.closeEntry();
					}
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	private static void checkForEnterpriseOrResourceAdapterArchive(String name) {
		if (name.equals("META-INF/application.xml") //
				|| name.equals("META-INF/ra.xml") //
				|| (!name.startsWith("META-INF/lib/") // IU JEE 6 runtime component
						&& !name.startsWith("META-INF/ejb/endorsed/") // IU JEE 6 EJB component
						&& !name.startsWith("META-INF/ejb/lib/") // IU JEE 6 EJB component
						&& !name.startsWith("WEB-INF/lib/") // Web component
						&& name.endsWith(".jar")) //
				|| name.endsWith(".war") || name.endsWith(".rar") || name.endsWith(".dll") || name.endsWith(".so"))
			throw new IllegalArgumentException(
					"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file");
	}

	static IuComponent newComponent(Component parent, Path... path) {
		if (path.length == 0)
			throw new IllegalArgumentException("Must provide a component archive");

		Manifest manifest = null;
		Set<String> nonEnclosedClasses = new LinkedHashSet<>();
		Map<String, byte[]> webResources = new LinkedHashMap<>();
		Map<String, byte[]> embeddedLibs = new LinkedHashMap<>();
		Properties pomProperties = null;
		Properties typeProperties = null;
		Properties iuProperties = null;
		var componentDefiningPathElement = path[0];
		var isModule = false;
		var isWeb = false;

		byte[] buf = new byte[16384];
		int r;
		try ( //
				var in = Files.newInputStream(componentDefiningPathElement); //
				var jar = new JarInputStream(in)) {
			manifest = jar.getManifest();
			if (manifest == null)
				throw new IllegalArgumentException("Component archive must include a manifest");

			JarEntry entry;
			while ((entry = jar.getNextJarEntry()) != null) {
				var name = entry.getName();
				checkForEnterpriseOrResourceAdapterArchive(name);

				if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.properties")) {
					if (pomProperties != null)
						throw new IllegalArgumentException("Component must not be a shaded (uber-)jar");
					pomProperties = new Properties();
					pomProperties.load(jar);
					jar.closeEntry();
					continue;
				}

				if (name.startsWith("WEB-INF/")) {
					if (!nonEnclosedClasses.isEmpty())
						throw new IllegalArgumentException(
								"Web archive must not define classes outside WEB-INF/classes/");

					if (!embeddedLibs.isEmpty())
						throw new IllegalArgumentException(
								"Web archive must not define embedded libraries outside WEB-INF/lib/");

					if (typeProperties != null)
						throw new IllegalArgumentException(
								"Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties");

					if (iuProperties != null)
						throw new IllegalArgumentException(
								"Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties");

					isWeb = true;
				}

				if (name.endsWith(".class")) {
					String resourceName;
					if (isWeb)
						if (name.startsWith("WEB-INF/classes/"))
							resourceName = name.substring(16);
						else
							throw new IllegalArgumentException(
									"Web archive must not define classes outside WEB-INF/classes/");
					else {
						resourceName = name;
						webResources = null;
					}

					if (resourceName.equals("module-info.class") && !isModule) {
						if (iuProperties != null)
							throw new IllegalArgumentException(
									"Modular component must not define META-INF/iu.properties");

						if (!isWeb && !embeddedLibs.isEmpty())
							throw new IllegalArgumentException("Modular component must not embed dependent libraries");

						isModule = true;
					} else if (!resourceName.endsWith("package-info.class") //
							&& resourceName.indexOf('$') == -1) // check for '$' skips enclosed classes
						nonEnclosedClasses.add(resourceName.substring(0, name.length() - 6).replace('/', '.'));

					continue;
				}

				if (name.equals("WEB-INF/classes/META-INF/iu-type.properties") //
						|| name.equals("META-INF/iu-type.properties")) {
					if (isWeb && name.startsWith("META-INF/"))
						throw new IllegalArgumentException(
								"Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties");
					typeProperties = new Properties();
					typeProperties.load(jar);
					jar.closeEntry();
					continue;
				}

				if (name.equals("WEB-INF/classes/META-INF/iu.properties") //
						|| name.equals("META-INF/iu.properties")) {
					if (isModule)
						throw new IllegalArgumentException("Modular component must not define META-INF/iu.properties");
					if (isWeb && name.startsWith("META-INF/"))
						throw new IllegalArgumentException(
								"Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties");

					iuProperties = new Properties();
					iuProperties.load(jar);
					jar.closeEntry();
					continue;
				}

				if (name.startsWith("META-INF/lib/") //
						|| name.startsWith("META-INF/ejb/endorsed/") //
						|| name.startsWith("META-INF/ejb/lib/") //
						|| name.startsWith("WEB-INF/lib/")) {
					if (name.startsWith("META-INF/"))
						if (isModule)
							throw new IllegalArgumentException("Modular component must not define embedded libraries");
						else if (isWeb)
							throw new IllegalArgumentException(
									"Web archive must not define embedded libraries outside WEB-INF/lib/");

					ByteArrayOutputStream lib = new ByteArrayOutputStream();
					while ((r = jar.read(buf, 0, buf.length)) > 0)
						lib.write(buf, 0, r);
					embeddedLibs.put(name, lib.toByteArray());
				}

				if (webResources == null //
						|| name.startsWith("META-INF/") //
						|| name.startsWith("WEB-INF/classes/") //
						|| name.startsWith("WEB-INF/lib/") //
						|| name.equals("WEB-INF/web.xml") //
						|| name.charAt(name.length() - 1) == '/')
					continue;

				ByteArrayOutputStream resource = new ByteArrayOutputStream();
				while ((r = jar.read(buf, 0, buf.length)) > 0)
					resource.write(buf, 0, r);
				webResources.put(name, resource.toByteArray());
			}

		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid or unreadable component archive", e);
		}

		if (pomProperties == null)
			throw new IllegalArgumentException("Component must include Maven properties");

		if (isModule)
			return newModuleJarComponent(parent, manifest, nonEnclosedClasses, pomProperties, typeProperties, path);
		else if (iuProperties == null)
			throw new IllegalArgumentException("Component must include a module descriptor or META-INF/iu.properties");

		// TODO implementation stub
		throw new UnsupportedOperationException("TODO");

// TODO: REVIEW LINE
//						else if (name.charAt(name.length() - 1) != '/' //
//								&& !name.startsWith("META-INF/maven/")) {
//
//							switch (name) {
//							case "WEB-INF/classes/META-INF/iu.properties":
//
//								webIuProperties = true;
//								break;
//							case "META-INF/iu.properties":
//								iuProperties = true;
//								break;
//							case "WEB-INF/classes/META-INF/iu-type.properties":
//								webTypeProperties.load(jar);
//								break;
//							case "META-INF/iu-type.properties":
//								properties.load(jar);
//								break;
//							}
//
//							resources.put(name, new URL("jar:" + url + "!/" + name));
//						}
//					}
//
//					if (pathElement == componentDefiningPathElement) {
//						if (moduleDescriptor) {
//							isModule = true;
//							typeProperties = properties;
//						} else if (!iuProperties)
//							throw new IllegalArgumentException(
//									"Component must be defined by a module or include META-INF/iu.properties");
//
//						classNames = classes;
//
//					} else {
//						if (!moduleDescriptor)
//							throw new IllegalArgumentException(pathElement + " does not have a module descriptor");
//						else if (!isModule)
//							throw new IllegalArgumentException(
//									"Unnamed components must not include additional path elements");
//
//						classNames.addAll(classes);
//					}
//				}
//			} catch (IOException e) {
//				throw new IllegalArgumentException(e);
//			}
//
//		if (isModule)
//		else
//			return newUnnamedComponent(parent, classNames, componentDefiningPathElement);
	}

	/**
	 * Creates a new component from the provided module path elements.
	 * 
	 * @param modulePath Paths to the jar files that compose the component.
	 *                   <em>Must</em> contain at least one path; the first entry
	 *                   <em>must</em> refer to the jar that defines the component's
	 *                   primary module.
	 * 
	 * @return {@link IuComponent} instance
	 */
	public static IuComponent newComponent(Path... modulePath) {
		return newComponent(null, modulePath);
	}

	private ComponentFactory() {
	}

}
