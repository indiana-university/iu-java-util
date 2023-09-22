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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

	private static final byte[] B0 = new byte[0];

	private static Path checkModule(Path pathElement) {
		byte[] buf = new byte[16384];
		try {
			return TemporaryFile.init(target -> {
				var hasPomProperties = false;
				var hasModuleInfo = false;

				try ( //
						var in = Files.newInputStream(pathElement); //
						var inJar = new JarInputStream(in); //
						var out = Files.newOutputStream(target); //
						var outJar = new JarOutputStream(out)) {
					if (inJar.getManifest() == null)
						throw new IllegalArgumentException("Module path entry must include a manifest");

					int r;
					JarEntry entry;
					while ((entry = inJar.getNextJarEntry()) != null) {
						var name = entry.getName();
						checkForEnterpriseOrResourceAdapterArchive("Module path entry", name);

						if (name.startsWith("WEB-INF/"))
							throw new IllegalArgumentException("Module path entry must not be a web archive");
						if (name.equals("META-INF/iu.properties"))
							throw new IllegalArgumentException(
									"Module path entry must not define META-INF/iu.properties");
						if (isEmbeddedLib(name))
							throw new IllegalArgumentException("Module path entry must not include embedded libraries");

						if (name.startsWith("META-INF/maven/")) {
							if (name.endsWith("/pom.properties")) {
								if (hasPomProperties)
									throw new IllegalArgumentException(
											"Module path entry must not be a shaded (uber-)jar");
								hasPomProperties = true;
							}
							continue;
						}

						if (name.equals("module-info.class"))
							hasModuleInfo = true;

						JarEntry outEntry = new JarEntry(name);
						outJar.putNextEntry(outEntry);
						while ((r = inJar.read(buf, 0, buf.length)) > 0)
							outJar.write(buf, 0, r);
						outJar.flush();
						outJar.closeEntry();
						inJar.closeEntry();
					}
				}

				if (!hasPomProperties)
					throw new IllegalArgumentException("Module path entry must include Maven properties");
				if (!hasModuleInfo)
					throw new IllegalArgumentException("Module path entry must include a module descriptor");

				return target;
			});
		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid or unreadable module path entry", e);
		}
	}

	private static IuComponent newModuleJarComponent(Component parent, Manifest manifest, Set<String> classNames,
			Properties pomProperties, Properties typeProperties, Path... path) {
		if (parent != null && parent.controller() == null)
			throw new IllegalArgumentException("Modular component must not extend a legacy component");

		Queue<Path> checkedPath = new ArrayDeque<>();
		checkedPath.add(path[0]); // checked by newComponent()
		for (int i = 1; i < path.length; i++)
			checkedPath.offer(checkModule(path[i]));
		Path[] modulePath = checkedPath.toArray(new Path[checkedPath.size()]);

		Map<String, String> openPackagesToModuleName = new LinkedHashMap<>();
		Set<String> moduleNames = new LinkedHashSet<>();
		Consumer<ModuleDescriptor> mapModuleDescriptor = descriptor -> {
			var moduleName = descriptor.name();
			moduleNames.add(moduleName);
			for (var packageName : descriptor.packages())
				openPackagesToModuleName.put(packageName, moduleName);
		};

		var moduleFinder = new ComponentModuleFinder(modulePath);
		try {
			var moduleIterator = moduleFinder.findAll().iterator();
			var componentModuleDescriptor = moduleIterator.next().descriptor();
			mapModuleDescriptor.accept(componentModuleDescriptor);

			while (moduleIterator.hasNext()) {
				var ref = moduleIterator.next();
				var descriptor = ref.descriptor();
				mapModuleDescriptor.accept(descriptor);
				moduleNames.add(descriptor.name());
			}

			ClassLoader parentClassLoader;
			ModuleLayer parentModuleLayer;
			if (parent == null) {
				parentClassLoader = null;
				parentModuleLayer = ModuleLayer.boot();
			} else {
				parentClassLoader = parent.classLoader();
				parentModuleLayer = parent.controller().layer();
			}

			var configuration = Configuration.resolveAndBind( //
					moduleFinder, List.of(parentModuleLayer.configuration()), ModuleFinder.of(), moduleNames);

			var controller = ModuleLayer.defineModulesWithOneLoader(configuration, List.of(parentModuleLayer),
					parentClassLoader);

			return new Component(parent, moduleFinder, controller, pomProperties.getProperty("artifactId"),
					pomProperties.getProperty("version"), typeProperties == null ? new Properties() : typeProperties,
					controller.layer().findLoader(moduleNames.iterator().next()), classNames, checkedPath);
		} catch (RuntimeException | Error e) {
			try {
				moduleFinder.close();
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw e;
		}
	}

	private static URL checkEmbeddedLib(byte[] rawArchive, Set<String> classNames) throws IOException {
		int r;
		return TemporaryFile.init(embeddedLib -> {
			try ( //
					var jar = new JarInputStream(new ByteArrayInputStream(rawArchive)); //
					var out = Files.newOutputStream(embeddedLib); //
					var outJar = new JarOutputStream(out)) {
				if (jar.getManifest() == null)
					throw new IllegalArgumentException("Embedded library archive must include a manifest");

			}
//
//				JarEntry entry;
//				while ((entry = jar.getNextJarEntry()) != null) {
//					var name = entry.getName();
//					checkForEnterpriseOrResourceAdapterArchive("Component", name);
//
//					if (name.startsWith("META-INF/maven/")) {
//						if (name.endsWith("/pom.properties")) {
//							if (pomProperties != null)
//								throw new IllegalArgumentException("Component must not be a shaded (uber-)jar");
//							pomProperties = new Properties();
//							pomProperties.load(jar);
//							jar.closeEntry();
//						}
//						continue;
//					}
//
//					if (name.startsWith("WEB-INF/")) {
//						if (!nonEnclosedClasses.isEmpty())
//							throw new IllegalArgumentException(
//									"Web archive must not define classes outside WEB-INF/classes/");
//
//						if (!embeddedLibs.isEmpty())
//							throw new IllegalArgumentException(
//									"Web archive must not define embedded libraries outside WEB-INF/lib/");
//
//						if (typeProperties != null)
//							throw new IllegalArgumentException(
//									"Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties");
//
//						if (iuProperties != null)
//							throw new IllegalArgumentException(
//									"Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties");
//
//						isWeb = true;
//					}
//
//					if (name.equals("WEB-INF/classes/META-INF/iu-type.properties") //
//							|| name.equals("META-INF/iu-type.properties")) {
//						if (isWeb && name.startsWith("META-INF/"))
//							throw new IllegalArgumentException(
//									"Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties");
//						typeProperties = new Properties();
//						typeProperties.load(jar);
//						jar.closeEntry();
//						continue;
//					}
//
//					if (name.equals("WEB-INF/classes/META-INF/iu.properties") //
//							|| name.equals("META-INF/iu.properties")) {
//						if (isModule)
//							throw new IllegalArgumentException(
//									"Modular component must not define META-INF/iu.properties");
//						if (isWeb && name.startsWith("META-INF/"))
//							throw new IllegalArgumentException(
//									"Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties");
//
//						iuProperties = new Properties();
//						iuProperties.load(jar);
//						jar.closeEntry();
//						continue;
//					}
//
//					if (isEmbeddedLib(name)) {
//						if (name.startsWith("META-INF/"))
//							if (isModule)
//								throw new IllegalArgumentException(
//										"Modular jar component must not include embedded libraries");
//							else if (isWeb)
//								throw new IllegalArgumentException(
//										"Web archive must not define embedded libraries outside WEB-INF/lib/");
//
//						if (name.charAt(name.length() - 1) == '/')
//							continue;
//						if (!name.endsWith(".jar"))
//							throw new IllegalArgumentException("Embedded library must be a Java Archive (jar) file");
//
//						ByteArrayOutputStream lib = new ByteArrayOutputStream();
//						while ((r = jar.read(buf, 0, buf.length)) > 0)
//							lib.write(buf, 0, r);
//						jar.closeEntry();
//
//						embeddedLibs.put(name, lib.toByteArray());
//						continue;
//					}
//
//					if (name.endsWith(".class")) {
//						String resourceName;
//						if (isWeb)
//							if (name.startsWith("WEB-INF/classes/"))
//								resourceName = name.substring(16);
//							else
//								throw new IllegalArgumentException(
//										"Web archive must not define classes outside WEB-INF/classes/");
//						else {
//							resourceName = name;
//
//							if (webResources != null) {
//								for (var resourceEntry : webResources.entrySet()) {
//									var key = resourceEntry.getKey();
//									JarEntry outEntry = new JarEntry(key);
//									outJar.putNextEntry(outEntry);
//									if (key.charAt(key.length() - 1) != '/') {
//										outJar.write(resourceEntry.getValue());
//										outJar.flush();
//										outJar.closeEntry();
//									}
//								}
//								webResources = null;
//							}
//						}
//
//						if (resourceName.equals("module-info.class") && !isModule) {
//							if (iuProperties != null)
//								throw new IllegalArgumentException(
//										"Modular component must not define META-INF/iu.properties");
//
//							if (!isWeb && !embeddedLibs.isEmpty())
//								throw new IllegalArgumentException(
//										"Modular jar component must not include embedded libraries");
//
//							isModule = true;
//						} else if (!resourceName.endsWith("package-info.class") //
//								&& resourceName.indexOf('$') == -1) // check for '$' skips enclosed classes
//							nonEnclosedClasses.add(resourceName.substring(0, name.length() - 6).replace('/', '.'));
//					}
//
//					if (webResources == null) {
//						JarEntry outEntry = new JarEntry(name);
//						outJar.putNextEntry(outEntry);
//						if (name.charAt(name.length() - 1) != '/') {
//							while ((r = jar.read(buf, 0, buf.length)) > 0)
//								outJar.write(buf, 0, r);
//							outJar.flush();
//							outJar.closeEntry();
//						}
//						jar.closeEntry();
//
//					} else if (!name.startsWith("WEB-INF/classes/") //
//							&& !name.startsWith("WEB-INF/lib/") //
//							&& !name.equals("WEB-INF/web.xml")) {
//
//						if (name.charAt(name.length() - 1) != '/') {
//							ByteArrayOutputStream resource = new ByteArrayOutputStream();
//							while ((r = jar.read(buf, 0, buf.length)) > 0)
//								resource.write(buf, 0, r);
//							webResources.put(name, resource.toByteArray());
//							jar.closeEntry();
//						} else
//							webResources.put(name, B0);
//					}
//
//				}
//			}
//
//			if (pomProperties == null)
//				throw new IllegalArgumentException("Component must include Maven properties");
//
//			if (isWeb) {
//				// TODO implementation stub
//				throw new UnsupportedOperationException("TODO");
//			}
//
//			if (isModule) {
//				path[0] = componentArchive;
//				return newModuleJarComponent(parent, manifest, nonEnclosedClasses, pomProperties, typeProperties, path);
//			} //
//			else if (iuProperties != null)
//				return newLegacyJarComponent(parent, manifest, nonEnclosedClasses, embeddedLibs, pomProperties,
//						iuProperties, componentArchive);
//			else
//				throw new IllegalArgumentException(
//						"Component must include a module descriptor or META-INF/iu.properties");

			return embeddedLib.toUri().toURL();
		});
	}

	private static IuComponent newLegacyJarComponent(Component parent, Manifest manifest, Set<String> classNames,
			Map<String, byte[]> embeddedLibs, Properties pomProperties, Properties iuProperties, Path path) {

		try {
			Queue<URL> lib = new ArrayDeque<>();
			lib.offer(path.toUri().toURL());

			Set<String> endorsed = new LinkedHashSet<>();
			for (var embeddedLibEntry : embeddedLibs.entrySet())
				lib.offer(checkEmbeddedLib(embeddedLibEntry.getValue(),
						embeddedLibEntry.getKey().startsWith("META-INF/ejb/endorsed/") ? endorsed : null));

		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid legacy component archive", e);
		}

		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	private static boolean isEmbeddedLib(String name) {
		return name.startsWith("META-INF/lib/") // IU JEE 6 Runtime
				|| name.startsWith("META-INF/ejb/endorsed/") // IU JEE 6 EJB
				|| name.startsWith("META-INF/ejb/lib/") // IU JEE 6 EJB
				|| name.startsWith("WEB-INF/lib/");
	}

	private static void checkForEnterpriseOrResourceAdapterArchive(String title, String name) {
		if (name.equals("META-INF/application.xml") //
				|| name.equals("META-INF/ra.xml") //
				|| (name.endsWith(".jar") && !isEmbeddedLib(name)) //
				|| name.endsWith(".war") //
				|| name.endsWith(".rar") //
				|| name.endsWith(".dll") //
				|| name.endsWith(".so"))
			throw new IllegalArgumentException(title
					+ " must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file");
	}

	static IuComponent newComponent(Component parent, Path... path) {
		if (path.length == 0)
			throw new IllegalArgumentException("Must provide a component archive");

		byte[] buf = new byte[16384];
		try {
			return TemporaryFile.init(componentArchive -> {
				Manifest manifest = null;
				Set<String> nonEnclosedClasses = new LinkedHashSet<>();
				Map<String, byte[]> webResources = new LinkedHashMap<>();
				Map<String, byte[]> embeddedLibs = new LinkedHashMap<>();
				Properties pomProperties = null;
				Properties typeProperties = null;
				Properties iuProperties = null;
				var firstPathEntry = path[0];
				var isModule = false;
				var isWeb = false;

				int r;
				try ( //
						var in = Files.newInputStream(firstPathEntry); //
						var jar = new JarInputStream(in); //
						var out = Files.newOutputStream(componentArchive); //
						var outJar = new JarOutputStream(out)) {
					manifest = jar.getManifest();
					if (manifest == null)
						throw new IllegalArgumentException("Component archive must include a manifest");

					JarEntry entry;
					while ((entry = jar.getNextJarEntry()) != null) {
						var name = entry.getName();
						checkForEnterpriseOrResourceAdapterArchive("Component", name);

						if (name.startsWith("META-INF/maven/")) {
							if (name.endsWith("/pom.properties")) {
								if (pomProperties != null)
									throw new IllegalArgumentException("Component must not be a shaded (uber-)jar");
								pomProperties = new Properties();
								pomProperties.load(jar);
								jar.closeEntry();
							}
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
								throw new IllegalArgumentException(
										"Modular component must not define META-INF/iu.properties");
							if (isWeb && name.startsWith("META-INF/"))
								throw new IllegalArgumentException(
										"Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties");

							iuProperties = new Properties();
							iuProperties.load(jar);
							jar.closeEntry();
							continue;
						}

						if (isEmbeddedLib(name)) {
							if (name.startsWith("META-INF/"))
								if (isModule)
									throw new IllegalArgumentException(
											"Modular jar component must not include embedded libraries");
								else if (isWeb)
									throw new IllegalArgumentException(
											"Web archive must not define embedded libraries outside WEB-INF/lib/");

							if (name.charAt(name.length() - 1) == '/')
								continue;
							if (!name.endsWith(".jar"))
								throw new IllegalArgumentException(
										"Embedded library must be a Java Archive (jar) file");

							ByteArrayOutputStream lib = new ByteArrayOutputStream();
							while ((r = jar.read(buf, 0, buf.length)) > 0)
								lib.write(buf, 0, r);
							jar.closeEntry();

							embeddedLibs.put(name, lib.toByteArray());
							continue;
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

								if (webResources != null) {
									for (var resourceEntry : webResources.entrySet()) {
										var key = resourceEntry.getKey();
										JarEntry outEntry = new JarEntry(key);
										outJar.putNextEntry(outEntry);
										if (key.charAt(key.length() - 1) != '/') {
											outJar.write(resourceEntry.getValue());
											outJar.flush();
											outJar.closeEntry();
										}
									}
									webResources = null;
								}
							}

							if (resourceName.equals("module-info.class") && !isModule) {
								if (iuProperties != null)
									throw new IllegalArgumentException(
											"Modular component must not define META-INF/iu.properties");

								if (!isWeb && !embeddedLibs.isEmpty())
									throw new IllegalArgumentException(
											"Modular jar component must not include embedded libraries");

								isModule = true;
							} else if (!resourceName.endsWith("package-info.class") //
									&& resourceName.indexOf('$') == -1) // check for '$' skips enclosed classes
								nonEnclosedClasses.add(resourceName.substring(0, name.length() - 6).replace('/', '.'));
						}

						if (webResources == null) {
							JarEntry outEntry = new JarEntry(name);
							outJar.putNextEntry(outEntry);
							if (name.charAt(name.length() - 1) != '/') {
								while ((r = jar.read(buf, 0, buf.length)) > 0)
									outJar.write(buf, 0, r);
								outJar.flush();
								outJar.closeEntry();
							}
							jar.closeEntry();

						} else if (!name.startsWith("WEB-INF/classes/") //
								&& !name.startsWith("WEB-INF/lib/") //
								&& !name.equals("WEB-INF/web.xml")) {

							if (name.charAt(name.length() - 1) != '/') {
								ByteArrayOutputStream resource = new ByteArrayOutputStream();
								while ((r = jar.read(buf, 0, buf.length)) > 0)
									resource.write(buf, 0, r);
								webResources.put(name, resource.toByteArray());
								jar.closeEntry();
							} else
								webResources.put(name, B0);
						}

					}
				}

				if (pomProperties == null)
					throw new IllegalArgumentException("Component must include Maven properties");

				if (isWeb) {
					// TODO implementation stub
					throw new UnsupportedOperationException("TODO");
				}

				if (isModule) {
					path[0] = componentArchive;
					return newModuleJarComponent(parent, manifest, nonEnclosedClasses, pomProperties, typeProperties,
							path);
				} //
				else if (iuProperties != null)
					return newLegacyJarComponent(parent, manifest, nonEnclosedClasses, embeddedLibs, pomProperties,
							iuProperties, componentArchive);
				else
					throw new IllegalArgumentException(
							"Component must include a module descriptor or META-INF/iu.properties");
			});
		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid or unreadable component archive", e);
		}
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
