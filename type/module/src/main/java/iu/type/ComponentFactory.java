package iu.type;

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
		var componentModuleJar = modulePath[0];

		Set<String> classNames = new LinkedHashSet<>();
		Map<String, URL> resourcesByName = new LinkedHashMap<>();
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

		var loader = new ComponentClassLoader(ModuleFinder.of(modulePath), null);

		// TODO implementation stub
		System.out.println(classNames);
		System.out.println(resourcesByName);
		throw new UnsupportedOperationException("TODO");
	}

	private ComponentFactory() {
	}

}
