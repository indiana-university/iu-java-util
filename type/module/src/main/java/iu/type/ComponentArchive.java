package iu.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.iu.type.IuComponent.Kind;

record ComponentArchive(Path path, Kind kind, String name, String version, Properties properties,
		Set<String> nonEnclosedTypeNames, Map<String, byte[]> webResources, Map<String, URL> allResources,
		Iterable<ArchiveSource> embeddedComponents) {

	private static record ScannedAttributes(Kind kind, String name, String version, Properties properties) {
	}

	private static ScannedAttributes scan(ArchiveSource source, ComponentTarget target,
			Set<String> nonEnclosedTypeNames, Map<String, byte[]> webResources, Map<String, URL> allResources,
			List<ArchiveSource> embeddedDependencies) throws IOException {

		final Set<String> classPath = new HashSet<>();
		source.classPath().forEach(classPath::add);

		var isWeb = false;
		var hasModuleDescirptor = false;
		var hasNonWebTypes = false;
		var componentName = source.name();
		var version = source.version();
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
			if (name.startsWith("WEB-INF/")) {
				if (source.name() != null)
					throw new IllegalArgumentException(
							"Web archive must not include Extension-Name in META-INF/MANIFEST.MF");

				if (source.version() != null)
					throw new IllegalArgumentException(
							"Web archive must not include Implementation-Version in META-INF/MANIFEST.MF");

				if (hasNonWebTypes)
					throw new IllegalArgumentException("Web archive must not define types outside WEB-INF/classes/");

				if (!embeddedDependencies.isEmpty())
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
						|| name.startsWith("META-INF/lib/") || name.startsWith("META-INF/ejb/endorsed/")
						|| name.startsWith("META-INF/ejb/lib/")) {
					var embeddedDependencyData = componentEntry.data();
					var embeddedDependencyInput = new ByteArrayInputStream(embeddedDependencyData);
					var embeddedDependency = new ArchiveSource(embeddedDependencyInput);
					embeddedDependencies.add(embeddedDependency);
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
				if (hasModuleDescirptor)
					throw new IllegalArgumentException(
							"Modular component archive must not include META-INF/iu.properties");
				if (isWeb && name.startsWith("META-INF/"))
					throw new IllegalArgumentException(
							"Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties");

				iuProperties = new Properties();
				componentEntry.read(iuProperties::load);
				continue;
			}

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
						for (var resourceEntry : webResources.entrySet())
							target.put(resourceEntry.getKey(), new ByteArrayInputStream(resourceEntry.getValue()));
					}
				}

				if (resourceName.equals("module-info.class")) {
					if (iuProperties != null)
						throw new IllegalArgumentException(
								"Modular component archive must not include META-INF/iu.properties");

					hasModuleDescirptor = true;
				} else if (!resourceName.endsWith("package-info.class") //
						&& resourceName.indexOf('$') == -1) // check for '$' skips enclosed classes
					nonEnclosedTypeNames.add(resourceName.substring(0, name.length() - 6).replace('/', '.'));
			}

		}

		if ((componentName == null || componentName.isBlank()) && pomProperties != null)
			componentName = pomProperties.getProperty("artifactId");
		if (componentName == null || componentName.isBlank())
			throw new IllegalArgumentException(
					"Component archive must provide a name as either Extension-Name in META-INF/MANIFEST.MF or artifactId in pom.properties");

		if ((version == null || version.isBlank()) && pomProperties != null)
			version = pomProperties.getProperty("version");
		if (version == null || version.isBlank())
			throw new IllegalArgumentException(
					"Component archive must provide a version as either Implementation-Version in META-INF/MANIFEST.MF or version in pom.properties");

		final Kind kind;
		final Properties properties;
		if (isWeb)
			if (iuProperties == null) {
				kind = Kind.MODULAR_WAR;
				properties = typeProperties;
			} else {
				kind = Kind.LEGACY_WAR;
				properties = iuProperties;
			}
		else if (iuProperties == null) {
			kind = Kind.MODULAR_JAR;
			properties = typeProperties;
		} else {
			kind = Kind.LEGACY_JAR;
			properties = iuProperties;
		}

		return new ScannedAttributes(kind, componentName, version, properties);
	}

	static ComponentArchive of(ArchiveSource source) throws IOException {
		return TemporaryFile.init(path -> {
			try (var target = new ComponentTarget(path)) {
				Set<String> nonEnclosedTypeNames = new LinkedHashSet<>();
				Map<String, byte[]> webResources = new LinkedHashMap<>();
				Map<String, URL> allResources = new LinkedHashMap<>();
				List<ArchiveSource> embeddedComponents = new ArrayList<>();

				var scannedAttributes = scan(source, target, nonEnclosedTypeNames, webResources, allResources,
						embeddedComponents);

				return new ComponentArchive(path, //
						scannedAttributes.kind, //
						scannedAttributes.name, //
						scannedAttributes.version, //
						scannedAttributes.properties, //
						Collections.unmodifiableSet(nonEnclosedTypeNames), //
						Collections.unmodifiableMap(webResources), //
						Collections.unmodifiableMap(allResources), //
						Collections.unmodifiableList(embeddedComponents) //
				);
			}
		});
	}

//		if (webResources == null) {
//			JarEntry outEntry = new JarEntry(name);
//			outJar.putNextEntry(outEntry);
//			if (name.charAt(name.length() - 1) != '/') {
//				while ((r = jar.read(buf, 0, buf.length)) > 0)
//					outJar.write(buf, 0, r);
//				outJar.flush();
//				outJar.closeEntry();
//			}
//			jar.closeEntry();
//
//		} else if (!name.startsWith("WEB-INF/classes/") //
//				&& !name.startsWith("WEB-INF/lib/") //
//				&& !name.equals("WEB-INF/web.xml")) {
//
//			if (name.charAt(name.length() - 1) != '/') {
//				ByteArrayOutputStream resource = new ByteArrayOutputStream();
//				while ((r = jar.read(buf, 0, buf.length)) > 0)
//					resource.write(buf, 0, r);
//				webResources.put(name, resource.toByteArray());
//				jar.closeEntry();
//			} else
//				webResources.put(name, B0);
//		}
//
//	}
//}
//
//if (pomProperties == null)
//	throw new IllegalArgumentException("Component must include Maven properties");

}
