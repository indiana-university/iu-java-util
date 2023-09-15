package edu.iu.type;

import java.nio.file.Path;

import iu.type.ComponentFactory;

/**
 * Defines an application component.
 */
public interface IuComponent {

	/**
	 * Creates a new component.
	 * 
	 * @param componentModuleJar   Path to a valid jar file that defines a
	 *                             {@link Module}.
	 * @param dependencyModuleJars Paths valid jar files that define a
	 *                             {@link Module} to include in the component's
	 *                             module path.
	 * @return component
	 */
	static IuComponent of(Path componentModuleJar, Path... dependencyModuleJars) {
		return ComponentFactory.newComponent(componentModuleJar, dependencyModuleJars);
	}

	/**
	 * Gets the {@link ClassLoader} for this component.
	 * 
	 * @return {@link ClassLoader}
	 */
	ClassLoader classLoader();

	/**
	 * Gets all public interfaces {@link Module#isOpen(String, Module) opened} by
	 * the component's {@link Module} that are {@link Module#canRead(Module)
	 * readable} by a target {@link Module}.
	 * 
	 * <p>
	 * The method <em>must</em> not return interfaces from dependency modules.
	 * </p>
	 * 
	 * @param module {@link Module} that intends to use the interfaces
	 * @return interface facades
	 */
	Iterable<IuType<?>> interfaces(Module module);

	/**
	 * Gets all types {@link Module#isOpen(String, Module) opened} by the
	 * component's {@link Module} that are {@link Module#canRead(Module) readable}
	 * by a target {@link Module}.
	 * 
	 * <p>
	 * The method <em>must</em> not include interfaces from dependent modules.
	 * </p>
	 * 
	 * @param module {@link Module} that intends to use the annotated types
	 * @return annotated type facades
	 */
	Iterable<IuType<?>> annotatedTypes(Module module);

	/**
	 * Gets resources defined by the component that may be
	 * {@link Module#canRead(Module) read} by a target {@link Module}.
	 * 
	 * @param module {@link Module} that intends to use the resources
	 * @return resources
	 */
	Iterable<IuResource<?>> resources(Module module);

}
