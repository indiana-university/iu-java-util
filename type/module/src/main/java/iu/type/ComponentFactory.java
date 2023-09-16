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

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import edu.iu.type.IuComponent;

/**
 * Creates component instances for {@link IuComponent}.
 */
public final class ComponentFactory {

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
		Set<String> classNames = new LinkedHashSet<>();
		Map<String, URL> resourcesByName = new LinkedHashMap<>();

		for (Path componentModuleJar : modulePath)
			try {
				var url = componentModuleJar.toUri().toURL();
				try (var in = url.openStream(); var jar = new JarInputStream(in)) {
					JarEntry entry = jar.getNextJarEntry();
					while (entry != null) {
						var name = entry.getName();
						if (name.endsWith(".class")) {
							if (!name.equals("module-info.class") //
									&& !name.endsWith("package-info.class") //
									&& name.indexOf('$') == -1)
								classNames.add(name.substring(0, name.length() - 6).replace('/', '.'));
						} else if (name.charAt(name.length() - 1) != '/' //
								&& !name.startsWith("META-INF/maven/"))
							resourcesByName.put(name, new URL("jar:" + url + "!/" + name));

						jar.closeEntry();
						entry = jar.getNextJarEntry();
					}
				}
			} catch (IOException e) {
				throw new IllegalArgumentException(e);
			}

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
//		if (parent == null) {
			parentClassLoader = ClassLoader.getPlatformClassLoader();
			parentModuleLayer = ModuleLayer.boot();
//		} else {
//			parentClassLoader = parent.classLoader();
//			parentModuleLayer = parent.controller().layer();
//		}

		var configuration = Configuration.resolveAndBind( //
				moduleFinder, List.of(parentModuleLayer.configuration()), ModuleFinder.of(), moduleNames);

		var controller = ModuleLayer.defineModulesWithOneLoader(configuration, List.of(parentModuleLayer),
				parentClassLoader);

		return new Component(null, moduleFinder, controller);
	}

	private ComponentFactory() {
	}

}
