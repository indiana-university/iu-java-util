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
import java.io.InputStream;
import java.lang.ModuleLayer.Controller;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.type.IuComponent;
import edu.iu.type.IuComponentVersion;
import edu.iu.type.IuResource;
import edu.iu.type.IuType;

/**
 * Component Implementation
 * 
 * @see IuComponent
 */
class Component implements IuComponent {

	private static final Logger LOG = Logger.getLogger(Component.class.getName());
	private static final Module TYPE_MODULE = Component.class.getModule();

	private static void indexClass(String className, ClassLoader classLoader, ComponentVersion version, Kind kind,
			Properties properties, Set<IuType<?, ?>> interfaces, Map<Class<?>, List<IuType<?, ?>>> annotatedTypes,
			List<ComponentResource<?>> resources) {
		Class<?> loadedClass;
		try {
			loadedClass = classLoader.loadClass(className);
		} catch (ClassNotFoundException | Error e) {
			LOG.log(Level.WARNING, e, () -> "Invalid class " + className + " in component " + version);
			return;
		}

		var module = loadedClass.getModule();
		if ((module.isNamed() && module.isOpen(loadedClass.getPackageName(), TYPE_MODULE)) //
				|| (!module.isNamed() && !kind.isModular() && properties != null)) {
			var type = IuType.of(loadedClass);

			var mod = loadedClass.getModifiers();
			if ((mod & Modifier.PUBLIC) != mod && loadedClass.isInterface() && !loadedClass.isAnnotation())
				interfaces.add(IuType.of(loadedClass));

			for (var annotation : AnnotationBridge.getAnnotations(loadedClass)) {
				var annotationType = annotation.annotationType();

				var annotatedWithType = annotatedTypes.get(annotationType);
				if (annotatedWithType == null) {
					annotatedWithType = new ArrayList<>();
					annotatedTypes.put(annotationType, annotatedWithType);
				}

				annotatedWithType.add(type);
			}

			for (var resource : ComponentResource.getResources(loadedClass,
					() -> loadedClass.cast(type.constructor().exec())))
				resources.add(resource);
		}
	}

	private Component parent;

	private Controller controller;

	private ClassLoader classLoader;

	private Kind kind;
	private Set<ComponentVersion> versions;
	private Properties properties;

	private Set<IuType<?, ?>> interfaces;
	private Map<Class<?>, List<IuType<?, ?>>> annotatedTypes;
	private List<ComponentResource<?>> resources;

	private ComponentModuleFinder moduleFinder;
	private Queue<ComponentArchive> archives;
	private boolean closed;

	/**
	 * Single entry constructor.
	 * 
	 * @param classLoader class loader
	 * @param pathEntry   resource root
	 * @throws IOException if an I/O error occurs scanning the path provided for
	 *                     resources
	 */
	Component(ClassLoader classLoader, Path pathEntry) throws IOException {
		Set<IuType<?, ?>> interfaces = new LinkedHashSet<>();
		Map<Class<?>, List<IuType<?, ?>>> annotatedTypes = new LinkedHashMap<>();
		List<ComponentResource<?>> resources = new ArrayList<>();

		this.classLoader = classLoader;

		final var version = ComponentVersion.of(pathEntry);
		this.versions = Set.of(version);

		Set<String> resourceNames = PathEntryScanner.findResources(pathEntry);
		this.kind = resourceNames.contains("module-info.class") ? Kind.MODULAR_ENTRY : Kind.LEGACY_ENTRY;

		byte[] propertiesSource;
		if (resourceNames.contains("META-INF/iu.properties"))
			propertiesSource = PathEntryScanner.read(pathEntry, "META-INF/iu.properties");
		else if (resourceNames.contains("META-INF/iu-type.properties"))
			propertiesSource = PathEntryScanner.read(pathEntry, "META-INF/iu-type.properties");
		else
			propertiesSource = null;

		this.properties = new Properties();
		if (propertiesSource != null)
			this.properties.load(new ByteArrayInputStream(propertiesSource));

		for (final var resourceName : resourceNames)
			if (resourceName.endsWith(".class") //
					&& !resourceName.endsWith("-info.class") //
					&& resourceName.indexOf('$') == -1)
				indexClass(resourceName.substring(0, resourceName.length() - 6).replace('/', '.'), classLoader, version,
						kind, properties, interfaces, annotatedTypes, resources);
		
		this.interfaces = Collections.unmodifiableSet(interfaces);
		for (var annotatedTypeEntry : annotatedTypes.entrySet())
			annotatedTypeEntry.setValue(Collections.unmodifiableList(annotatedTypeEntry.getValue()));
		this.annotatedTypes = Collections.unmodifiableMap(annotatedTypes);
		this.resources = Collections.unmodifiableList(resources);
	}

	/**
	 * Constructor for use from {@link ComponentFactory}.
	 * 
	 * @param parent       parent component, see
	 *                     {@link #extend(InputStream, InputStream...)}
	 * @param controller   module controller; must be non-null when first archive is
	 *                     {@link edu.iu.type.IuComponent.Kind#isModular() modular}.
	 * @param classLoader  component context loader
	 * @param moduleFinder module finder backing the controller and classLoader
	 *                     arguments, to close with this component
	 * @param archives     archives dedicated to this component, to close and delete
	 *                     when the component is closed
	 */
	Component(Component parent, Controller controller, ClassLoader classLoader, ComponentModuleFinder moduleFinder,
			Queue<ComponentArchive> archives) {
		Set<IuType<?, ?>> interfaces = new LinkedHashSet<>();
		Map<Class<?>, List<IuType<?, ?>>> annotatedTypes = new LinkedHashMap<>();
		List<ComponentResource<?>> resources = new ArrayList<>();
		if (parent != null) {
			if (parent.kind.isWeb())
				throw new IllegalArgumentException("Component must not extend a web component");

			interfaces.addAll(parent.interfaces);
			for (var annotatedTypeEntry : parent.annotatedTypes.entrySet())
				annotatedTypes.put(annotatedTypeEntry.getKey(), new ArrayList<>(annotatedTypeEntry.getValue()));
			resources.addAll(parent.resources);
		}

		this.parent = parent;

		this.controller = controller;
		this.classLoader = Objects.requireNonNull(classLoader, "classLoader");

		this.moduleFinder = moduleFinder;
		this.archives = Objects.requireNonNull(archives, "archives");

		var firstArchive = archives.iterator().next();
		kind = firstArchive.kind();
		properties = firstArchive.properties();

		versions = new LinkedHashSet<>();
		for (var archive : archives) {
			versions.add(archive.version());
			if (archive.kind().isWeb())
				if (archive == firstArchive)
					for (var webResource : archive.webResources().entrySet())
						resources
								.add(ComponentResource.createWebResource(webResource.getKey(), webResource.getValue()));
				else
					throw new IllegalArgumentException("Component must not include a web component as a dependency");

			for (var className : archive.nonEnclosedTypeNames())
				indexClass(className, classLoader, archive.version(), archive.kind(), archive.properties(), interfaces,
						annotatedTypes, resources);
		}

		if (parent != null)
			versions.addAll(parent.versions);

		this.interfaces = Collections.unmodifiableSet(interfaces);
		for (var annotatedTypeEntry : annotatedTypes.entrySet())
			annotatedTypeEntry.setValue(Collections.unmodifiableList(annotatedTypeEntry.getValue()));
		this.annotatedTypes = Collections.unmodifiableMap(annotatedTypes);
		this.resources = Collections.unmodifiableList(resources);
	}

	private void checkClosed() {
		if (closed)
			throw new IllegalStateException("closed");
	}

	/**
	 * Gets the parent component.
	 * 
	 * @return parent component
	 */
	Component parent() {
		checkClosed();
		return parent;
	}

	/**
	 * Gets the controller for the component context's module loader.
	 * 
	 * @return {@link Controller}
	 */
	Controller controller() {
		checkClosed();
		return controller;
	}

	/**
	 * Gets the {@code META-INF/iu-type.properties} for a
	 * {@link edu.iu.type.IuComponent.Kind#isModular() modular component}, or
	 * {@code META-INF/iu.properties} for a legacy component.
	 * 
	 * @return parsed properties
	 */
	Properties properties() {
		checkClosed();
		return properties;
	}

	/**
	 * Gets the version information for this component and all dependencies included
	 * in its path.
	 * 
	 * <p>
	 * The return value is ordered; this component version is the first returned by
	 * the iterator.
	 * </p>
	 * 
	 * @return version information
	 */
	Set<ComponentVersion> versions() {
		checkClosed();
		return versions;
	}

	@Override
	public Component extend(InputStream componentArchiveSource, InputStream... providedDependencyArchiveSources)
			throws IOException, IllegalArgumentException {
		checkClosed();
		return ComponentFactory.createComponent(this, componentArchiveSource, providedDependencyArchiveSources);
	}

	@Override
	public Kind kind() {
		checkClosed();
		return kind;
	}

	@Override
	public IuComponentVersion version() {
		checkClosed();
		return versions.iterator().next();
	}

	@Override
	public ClassLoader classLoader() {
		checkClosed();
		return classLoader;
	}

	@Override
	public Set<? extends IuType<?, ?>> interfaces() {
		checkClosed();
		return interfaces;
	}

	@Override
	public Iterable<? extends IuType<?, ?>> annotatedTypes(Class<? extends Annotation> annotationType) {
		checkClosed();
		var annotatedTypes = this.annotatedTypes.get(annotationType);
		if (annotatedTypes == null)
			return Collections.emptySet();
		else
			return annotatedTypes;
	}

	@Override
	public Iterable<? extends IuResource<?>> resources() {
		checkClosed();
		return resources;
	}

	@Override
	public void close() {
		if (moduleFinder != null)
			try {
				moduleFinder.close();
			} catch (Throwable e) {
				LOG.log(Level.WARNING, e, () -> "Failed to close module finder");
			}

		if (classLoader instanceof URLClassLoader)
			try {
				((URLClassLoader) classLoader).close();
			} catch (Throwable e) {
				LOG.log(Level.WARNING, e, () -> "Failed to close class loader");
			}

		if (archives != null)
			while (!archives.isEmpty()) {
				var archive = archives.poll();
				try {
					Files.delete(archive.path());
				} catch (Throwable e) {
					LOG.log(Level.WARNING, e, () -> "Failed to clean up archive " + archive.path());
				}
			}

		parent = null;
		moduleFinder = null;
		controller = null;
		kind = null;
		versions = null;
		properties = null;
		classLoader = null;
		interfaces = null;
		closed = true;
	}

}
