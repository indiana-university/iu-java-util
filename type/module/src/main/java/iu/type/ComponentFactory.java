package iu.type;

import java.nio.file.Path;

import edu.iu.type.IuComponent;

/**
 * Creates component instances for {@link IuComponent}.
 */
public final class ComponentFactory {

	/**
	 * Creates a new component from the provided module path elements.
	 * 
	 * @param componentModuleJar   Path to the primary component jar
	 * @param dependencyModuleJars Path to all non-JDK dependency jars required by
	 *                             the component
	 * @return {@link IuComponent} instance
	 */
	public static IuComponent newComponent(Path componentModuleJar, Path[] dependencyModuleJars) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	private ComponentFactory() {}
	
}
