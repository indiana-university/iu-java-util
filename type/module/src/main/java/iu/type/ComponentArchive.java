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
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.iu.type.IuComponent.Kind;

record ComponentArchive(Path path, Kind kind, ComponentVersion version, Properties properties,
		Set<String> nonEnclosedTypeNames, Map<String, byte[]> webResources, Set<String> allResources,
		Collection<ArchiveSource> bundledDependencies) {

	private static record ScannedAttributes(Kind kind, ComponentVersion version, Properties properties,
			Set<String> nonEnclosedTypeNames, Map<String, byte[]> webResources, Set<String> allResources,
			Collection<ArchiveSource> bundledDependencies) {
	}

	private static ScannedAttributes scan(ArchiveSource source, ComponentTarget target) throws IOException {
		final Set<String> nonEnclosedTypeNames = new LinkedHashSet<>();
		final Map<String, byte[]> webResources = new LinkedHashMap<>();
		final Set<String> allResources = new LinkedHashSet<>();
		final Map<String, ArchiveSource> bundledDependencies = new LinkedHashMap<>();

		final Set<String> classPath = new HashSet<>();
		source.classPath().forEach(classPath::add);

		var isWeb = false;
		var hasModuleDescriptor = false;
		var hasNonWebTypes = false;
		Properties pomProperties = null;
		Properties typeProperties = null;
		Properties iuProperties = null;

		while (source.hasNext()) {
			var componentEntry = source.next();
			var name = componentEntry.name();

			if (name.startsWith("META-INF/maven/")) {
				if (name.endsWith("/pom.properties")) {
					if (pomProperties != null)
						throw new IllegalArgumentException("Component archive must not be a shaded (uber-)jar");
					pomProperties = new Properties();
					componentEntry.read(pomProperties::load);
				}
				continue;
			}

			// Detect web component first; later logic expects isWeb == true if any
			// component entry begins with WEB-INF, including the current entry
			if (!isWeb && name.startsWith("WEB-INF/")) {
				if (hasNonWebTypes)
					throw new IllegalArgumentException("Web archive must not define types outside WEB-INF/classes/");

				if (!bundledDependencies.isEmpty())
					throw new IllegalArgumentException("Web archive must not embed components outside WEB-INF/lib/");

				if (typeProperties != null)
					throw new IllegalArgumentException(
							"Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties");

				if (iuProperties != null)
					throw new IllegalArgumentException(
							"Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties");

				isWeb = true;
			}

			if (name.endsWith(".jar")) {
				if (isWeb && !name.startsWith("WEB-INF/lib/"))
					throw new IllegalArgumentException("Web archive must not embed components outside WEB-INF/lib/");

				if (classPath.remove(name) // allow embedded names from manifest Class-Path attribute
						// also temporarily allow IU JEE 6 embedded class path entries
						// these will be validated for the presence of META-INF/iu.properties
						// after scanning the full archive
						|| name.startsWith("WEB-INF/lib/") //
						|| name.startsWith("META-INF/lib/") //
						|| name.startsWith("META-INF/ejb/endorsed/") //
						|| name.startsWith("META-INF/ejb/lib/")) {
					var embeddedDependencyData = componentEntry.data();
					var embeddedDependencyInput = new ByteArrayInputStream(embeddedDependencyData);
					var embeddedDependency = new ArchiveSource(embeddedDependencyInput);
					bundledDependencies.put(name, embeddedDependency);
					continue;
				}
			}

			if (name.equals("META-INF/application.xml") //
					|| name.equals("META-INF/ra.xml") //
					|| name.endsWith(".jar") //
					|| name.endsWith(".war") //
					|| name.endsWith(".rar") //
					|| name.endsWith(".dll") //
					|| name.endsWith(".so"))
				throw new IllegalArgumentException(
						"Component archive must not be an Enterprise Application (ear) or Resource Adapter Archive (rar) file");

			if (name.equals("WEB-INF/classes/META-INF/iu-type.properties") //
					|| name.equals("META-INF/iu-type.properties")) {
				if (isWeb && name.startsWith("META-INF/"))
					throw new IllegalArgumentException(
							"Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties");
				typeProperties = new Properties();
				componentEntry.read(typeProperties::load);
				continue;
			}

			if (name.equals("WEB-INF/classes/META-INF/iu.properties") //
					|| name.equals("META-INF/iu.properties")) {
				if (hasModuleDescriptor)
					throw new IllegalArgumentException(
							"Modular component archive must not include META-INF/iu.properties");
				if (isWeb && name.startsWith("META-INF/"))
					throw new IllegalArgumentException(
							"Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties");

				iuProperties = new Properties();
				componentEntry.read(iuProperties::load);
				continue;
			}

			// after this point, source resources are captured

			if (name.endsWith(".class")) {
				String resourceName;
				if (isWeb)
					if (name.startsWith("WEB-INF/classes/"))
						resourceName = name.substring(16);
					else
						// this error will also be triggered if a WEB-INF/ entry is read after a class
						throw new IllegalArgumentException( // is detected outside WEB-INF/classes/
								"Web archive must not define types outside WEB-INF/classes/");
				else {
					hasNonWebTypes = true;
					resourceName = name;

					if (!webResources.isEmpty()) {
						// This is not a web archive, so write all resources collected so far to
						// the target archive and stop collecting web resource. Further entries
						// with name starting with WEB-INF/ will trigger the same error as above
						for (var resourceEntry : webResources.entrySet()) {
							var resourceEntryName = resourceEntry.getKey();
							if (resourceEntryName.charAt(resourceEntryName.length() - 1) != '/') {
								target.put(resourceEntryName, new ByteArrayInputStream(resourceEntry.getValue()));
								allResources.add(resourceEntryName);
							}
						}
						webResources.clear();
					}
				}

				if (resourceName.equals("module-info.class")) {
					if (iuProperties != null)
						throw new IllegalArgumentException(
								"Modular component archive must not include META-INF/iu.properties");

					hasModuleDescriptor = true;
				} else if (!resourceName.endsWith("package-info.class") //
						&& resourceName.indexOf('$') == -1) // check for '$' skips enclosed classes
					nonEnclosedTypeNames.add(resourceName.substring(0, resourceName.length() - 6).replace('/', '.'));

				componentEntry.read(in -> target.put(resourceName, in));

			} else if (name.startsWith("WEB-INF/classes/")) {
				var resourceName = name.substring(16);
				if (resourceName.startsWith("META-INF/") && resourceName.charAt(resourceName.length() - 1) == '/')
					continue;

				componentEntry.read(in -> target.put(resourceName, in));
				allResources.add(resourceName);

			} else if (name.startsWith("META-INF/") && name.charAt(name.length() - 1) == '/')
				continue;

			else if (!hasNonWebTypes)
				webResources.put(name, componentEntry.data());

			else if (name.charAt(name.length() - 1) != '/') {
				componentEntry.read(in -> target.put(name, in));
				allResources.add(name);
			}
		}

		if (!classPath.isEmpty())
			throw new IllegalArgumentException(
					"Component archive didn't include all bundled dependencies, missing " + classPath);

		if (pomProperties == null)
			throw new IllegalArgumentException("Component archive missing META-INF/maven/.../pom.properties");

		var componentName = pomProperties.getProperty("artifactId");
		if (componentName == null)
			throw new IllegalArgumentException("Component archive must provide a name as artifactId in pom.properties");

		var version = pomProperties.getProperty("version");
		if (version == null)
			throw new IllegalArgumentException("Component archive must provide a version in pom.properties");

		final Kind kind;
		final Properties properties;
		if (isWeb) {

			var webResourceIterator = webResources.entrySet().iterator();
			while (webResourceIterator.hasNext())
				if (webResourceIterator.next().getKey().startsWith("META-INF/"))
					webResourceIterator.remove();

			var isServlet6 = false;
			for (var dependency : source.dependencies())
				if (dependency.compareTo(ComponentVersion.SERVLET_6) >= 0) {
					isServlet6 = true;
					continue;
				}

			if (hasModuleDescriptor || isServlet6) {
				kind = Kind.MODULAR_WAR;
				properties = typeProperties;
			} else {
				kind = Kind.LEGACY_WAR;
				properties = iuProperties;
			}
		} else if (iuProperties == null) {
			kind = Kind.MODULAR_JAR;
			properties = typeProperties;
		} else {
			kind = Kind.LEGACY_JAR;
			properties = iuProperties;
		}

		return new ScannedAttributes( //
				kind, //
				new ComponentVersion(componentName, version), //
				properties, //
				Collections.unmodifiableSet(nonEnclosedTypeNames), //
				Collections.unmodifiableMap(webResources), //
				Collections.unmodifiableSet(allResources), //
				Collections.unmodifiableCollection(bundledDependencies.values()));
	}

	static ComponentArchive from(ArchiveSource source) throws IOException {
		return TemporaryFile.init(path -> {
			try (var target = new ComponentTarget(path)) {

				var scannedAttributes = scan(source, target);

				return new ComponentArchive(path, //
						scannedAttributes.kind, //
						scannedAttributes.version, //
						scannedAttributes.properties, //
						scannedAttributes.nonEnclosedTypeNames, //
						scannedAttributes.webResources, //
						scannedAttributes.allResources, //
						scannedAttributes.bundledDependencies //
				);
			}
		});
	}

}
