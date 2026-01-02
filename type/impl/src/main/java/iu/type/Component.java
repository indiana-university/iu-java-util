/*
 * Copyright © 2026 Indiana University
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.UnsafeRunnable;
import edu.iu.type.IuAttribute;
import edu.iu.type.IuComponent;
import edu.iu.type.IuComponentVersion;
import edu.iu.type.IuProperty;
import edu.iu.type.IuResource;
import edu.iu.type.IuResourceReference;
import edu.iu.type.IuType;
import jakarta.annotation.Resource;

/**
 * Component Implementation
 * 
 * @see IuComponent
 */
class Component implements IuComponent {

	private static final Logger LOG = Logger.getLogger(Component.class.getName());
	private static final Module TYPE_MODULE = Component.class.getModule();

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void indexClass(String className, ClassLoader classLoader, Kind kind, Properties properties,
			Set<IuType<?, ?>> interfaces, Map<Class<?>, List<IuAttribute<?, ?>>> annotatedAttributes,
			Map<Class<?>, List<IuType<?, ?>>> annotatedTypes, List<ComponentResource<?>> resources,
			List<ComponentResourceReference<?, ?>> resourceReferences) {
		final Class<?> loadedClass;
		try {
			loadedClass = classLoader.loadClass(className);
		} catch (ClassNotFoundException | Error e) {
			LOG.log(Level.WARNING, e, () -> "Invalid class " + className + " in component");
			return;
		}

		var module = loadedClass.getModule();
		if (!IuObject.isPlatformName(loadedClass.getName()) //
				&& module.isOpen(loadedClass.getPackageName(), TYPE_MODULE)) {
			final var type = TypeFactory.resolveRawClass(loadedClass);

			for (final var o : IuIterable.cat((Iterable) type.fields(), (Iterable) type.properties())) {
				final var attribute = (DeclaredAttribute<?, ?>) o;
				for (var annotation : attribute.annotations()) {
					var annotationType = annotation.annotationType();

					var annotatedWithType = annotatedAttributes.get(annotationType);
					if (annotatedWithType == null) {
						annotatedWithType = new ArrayList<>();
						annotatedAttributes.put(annotationType, annotatedWithType);
					}

					annotatedWithType.add(attribute);
				}

				final Resource resource;
				if (attribute instanceof IuProperty property) {
					final var write = property.write();
					if (write != null)
						resource = write.annotation(Resource.class);
					else
						resource = null;
				} else
					resource = attribute.annotation(Resource.class);

				if (resource != null)
					resourceReferences.add(new ComponentResourceReference<>(attribute, resource));
			}

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

			for (var resource : ComponentResource.getResources(loadedClass))
				resources.add(resource);
		}
	}

	private final Component parent;
	private final ClassLoader classLoader;
	private final ModuleLayer moduleLayer;

	private final Kind kind;
	private final Set<ComponentVersion> versions;
	private final Properties properties;

	private final Set<IuType<?, ?>> interfaces;
	private final Map<Class<?>, List<IuType<?, ?>>> annotatedTypes;
	private final Map<Class<?>, List<IuAttribute<?, ?>>> annotatedAttributes;
	private final List<ComponentResource<?>> resources;
	private final List<ComponentResourceReference<?, ?>> resourceReferences;

	private final UnsafeRunnable onClose;

	private volatile boolean closed;

	/**
	 * Single entry constructor.
	 * 
	 * @param classLoader class loader
	 * @param moduleLayer module layer
	 * @param pathEntry   resource root
	 * @throws IOException if an I/O error occurs scanning the path provided for
	 *                     resources
	 */
	Component(ClassLoader classLoader, ModuleLayer moduleLayer, Path pathEntry) throws IOException {
		Set<IuType<?, ?>> interfaces = new LinkedHashSet<>();
		Map<Class<?>, List<IuType<?, ?>>> annotatedTypes = new LinkedHashMap<>();
		Map<Class<?>, List<IuAttribute<?, ?>>> annotatedAttributes = new LinkedHashMap<>();
		List<ComponentResource<?>> resources = new ArrayList<>();
		List<ComponentResourceReference<?, ?>> resourceReferences = new ArrayList<>();

		this.parent = null;

		this.classLoader = classLoader;
		this.moduleLayer = moduleLayer;

		final Set<ComponentVersion> versions = new LinkedHashSet<>();
		try {
			versions.add(ComponentVersion.of(pathEntry));
		} catch (IllegalArgumentException e) {
			// not required
		}
		this.versions = Collections.unmodifiableSet(versions);

		Set<String> resourceNames = PathEntryScanner.findResources(pathEntry);
		this.kind = Kind.ENTRY;

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

		IuException.checked(IOException.class, () -> TypeUtils.callWithContext(classLoader, () -> {
			for (final var resourceName : resourceNames)
				if (resourceName.endsWith(".class") //
						&& !resourceName.endsWith("-info.class") //
						&& resourceName.indexOf('$') == -1)
					indexClass(resourceName.substring(0, resourceName.length() - 6).replace('/', '.'), classLoader,
							kind, properties, interfaces, annotatedAttributes, annotatedTypes, resources,
							resourceReferences);
		}));

		this.interfaces = Collections.unmodifiableSet(interfaces);
		for (var annotatedTypeEntry : annotatedTypes.entrySet())
			annotatedTypeEntry.setValue(Collections.unmodifiableList(annotatedTypeEntry.getValue()));
		this.annotatedTypes = Collections.unmodifiableMap(annotatedTypes);
		for (var annotatedAttributeEntry : annotatedAttributes.entrySet())
			annotatedAttributeEntry.setValue(Collections.unmodifiableList(annotatedAttributeEntry.getValue()));
		this.annotatedAttributes = Collections.unmodifiableMap(annotatedAttributes);
		this.resources = Collections.unmodifiableList(resources);
		this.resourceReferences = Collections.unmodifiableList(resourceReferences);
		this.onClose = null;
	}

	/**
	 * Constructor for use from {@link ComponentFactory}.
	 * 
	 * @param parent      parent component, see
	 *                    {@link #extend(InputStream, InputStream...)}
	 * @param classLoader component context loader
	 * @param moduleLayer module layer
	 * @param archives    archives dedicated to this component, to close and delete
	 *                    when the component is closed
	 * @param onClose     thunk for tearing down resources after closing the
	 *                    component
	 */
	Component(Component parent, ClassLoader classLoader, ModuleLayer moduleLayer, Iterable<ComponentArchive> archives,
			UnsafeRunnable onClose) {
		Set<IuType<?, ?>> interfaces = new LinkedHashSet<>();
		Map<Class<?>, List<IuType<?, ?>>> annotatedTypes = new LinkedHashMap<>();
		Map<Class<?>, List<IuAttribute<?, ?>>> annotatedAttributes = new LinkedHashMap<>();
		List<ComponentResource<?>> resources = new ArrayList<>();
		List<ComponentResourceReference<?, ?>> resourceReferences = new ArrayList<>();

		if (parent != null) {
			if (parent.kind.isWeb())
				throw new IllegalArgumentException("Component must not extend a web component");

			interfaces.addAll(parent.interfaces);
			for (var annotatedTypeEntry : parent.annotatedTypes.entrySet())
				annotatedTypes.put(annotatedTypeEntry.getKey(), new ArrayList<>(annotatedTypeEntry.getValue()));
			resources.addAll(parent.resources);
		}

		this.parent = parent;
		this.classLoader = classLoader;
		this.moduleLayer = moduleLayer;

		var firstArchive = archives.iterator().next();
		kind = firstArchive.kind();
		properties = firstArchive.properties();

		versions = new LinkedHashSet<>();
		IuException.unchecked(() -> TypeUtils.callWithContext(classLoader, () -> {
			for (var archive : archives) {
				versions.add(archive.version());
				if (archive.kind().isWeb())
					if (archive == firstArchive)
						for (var webResource : archive.webResources().entrySet())
							resources.add(
									ComponentResource.createWebResource(webResource.getKey(), webResource.getValue()));
					else
						throw new IllegalArgumentException(
								"Component must not include a web component as a dependency");

				for (var className : archive.nonEnclosedTypeNames())
					indexClass(className, classLoader, archive.kind(), archive.properties(), interfaces,
							annotatedAttributes, annotatedTypes, resources, resourceReferences);
			}
		}));

		if (parent != null)
			versions.addAll(parent.versions);

		this.interfaces = Collections.unmodifiableSet(interfaces);
		for (var annotatedTypeEntry : annotatedTypes.entrySet())
			annotatedTypeEntry.setValue(Collections.unmodifiableList(annotatedTypeEntry.getValue()));
		this.annotatedTypes = Collections.unmodifiableMap(annotatedTypes);
		for (var annotatedAttributeEntry : annotatedAttributes.entrySet())
			annotatedAttributeEntry.setValue(Collections.unmodifiableList(annotatedAttributeEntry.getValue()));
		this.annotatedAttributes = Collections.unmodifiableMap(annotatedAttributes);
		this.resources = Collections.unmodifiableList(resources);
		this.resourceReferences = Collections.unmodifiableList(resourceReferences);
		this.onClose = onClose;
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
	 * Gets the {@code META-INF/iu-type.properties}.
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
		return extend(null, componentArchiveSource, providedDependencyArchiveSources);
	}

	@Override
	public Component extend(Consumer<Controller> controllerCallback, InputStream componentArchiveSource,
			InputStream... providedDependencyArchiveSources) throws IOException, IllegalArgumentException {
		checkClosed();
		return ComponentFactory.createComponent(this, classLoader, moduleLayer, controllerCallback,
				componentArchiveSource, providedDependencyArchiveSources);
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
	public ModuleLayer moduleLayer() {
		checkClosed();
		return moduleLayer;
	}

	@Override
	public Set<? extends IuType<?, ?>> interfaces() {
		checkClosed();
		return interfaces;
	}

	@Override
	public Iterable<? extends IuAttribute<?, ?>> annotatedAttributes(Class<? extends Annotation> annotationType) {
		checkClosed();
		final List<IuAttribute<?, ?>> annotatedAttributes;
		try {
			final var compatibleClass = BackwardsCompatibility.getCompatibleClass(annotationType, classLoader);
			annotatedAttributes = this.annotatedAttributes.get(compatibleClass);
		} catch (NoClassDefFoundError e) {
			return Collections.emptySet();
		}

		if (annotatedAttributes == null)
			return Collections.emptySet();
		else
			return annotatedAttributes;
	}

	@Override
	public Iterable<? extends IuType<?, ?>> annotatedTypes(Class<? extends Annotation> annotationType) {
		checkClosed();
		List<IuType<?, ?>> annotatedTypes;
		try {
			annotatedTypes = this.annotatedTypes
					.get(BackwardsCompatibility.getCompatibleClass(annotationType, classLoader));
		} catch (NoClassDefFoundError e) {
			return Collections.emptySet();
		}

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
	public Iterable<? extends IuResourceReference<?, ?>> resourceReferences() {
		checkClosed();
		return resourceReferences;
	}

	@Override
	public synchronized void close() throws Exception {
		if (closed)
			return;

		closed = true;
		if (onClose != null)
			IuException.checked(onClose::run);
	}

	@Override
	public String toString() {
		return "Component [parent=" + parent + ", kind=" + kind + ", versions=" + versions + ", closed=" + closed + "]";
	}

}
