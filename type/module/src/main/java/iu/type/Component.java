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
import java.io.InputStream;
import java.lang.ModuleLayer.Controller;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.file.Files;
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

class Component implements IuComponent {

	private static final Logger LOG = Logger.getLogger(Component.class.getName());
	private static final Module TYPE_MODULE = Component.class.getModule();

	private Component parent;

	private Controller controller;

	private ClassLoader classLoader;

	private Kind kind;
	private ComponentVersion version;
	private Properties properties;

	private Set<IuType<?>> interfaces;
	private Map<Class<?>, List<IuType<?>>> annotatedTypes;
	private List<ComponentResource<?>> resources;

	private ComponentModuleFinder moduleFinder;
	private Queue<ComponentArchive> archives;
	private boolean closed;

	Component(Component parent, Controller controller, ClassLoader classLoader, ComponentModuleFinder moduleFinder,
			Queue<ComponentArchive> archives) {
		Set<IuType<?>> interfaces = new LinkedHashSet<>();
		Map<Class<?>, List<IuType<?>>> annotatedTypes = new LinkedHashMap<>();
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
		version = firstArchive.version();
		properties = firstArchive.properties();

		for (var archive : archives) {
			if (archive.kind().isWeb())
				if (archive == firstArchive)
					for (var webResource : archive.webResources().entrySet())
						resources.add(new ComponentResource<>(true, webResource.getKey(), IuType.of(byte[].class),
								webResource::getValue));
				else
					throw new IllegalArgumentException("Component must not include a web component as a dependency");

			for (var className : archive.nonEnclosedTypeNames())
				try {
					var loadedClass = classLoader.loadClass(className);
					var module = loadedClass.getModule();
					if ((module.isNamed() && module.isOpen(loadedClass.getPackageName(), TYPE_MODULE)) //
							|| (!module.isNamed() && !archive.kind().isModular() && archive.properties() != null)) {

						var mod = loadedClass.getModifiers();
						if ((mod & Modifier.PUBLIC) != mod && loadedClass.isInterface())
							interfaces.add(IuType.of(loadedClass));

						for (var annotation : loadedClass.getAnnotations()) {
							var annotationType = annotation.annotationType();

							var annotatedWithType = annotatedTypes.get(annotationType);
							if (annotatedWithType == null) {
								annotatedWithType = new ArrayList<>();
								annotatedTypes.put(annotationType, annotatedWithType);
							}

							annotatedWithType.add(IuType.of(loadedClass));
							
						}
					}
				} catch (Throwable e) {
					LOG.log(Level.WARNING, e, () -> "Invalid class " + className + " in component " + version);
				}
		}

		this.interfaces = Collections.unmodifiableSet(interfaces);
	}

	private void checkClosed() {
		if (closed)
			throw new IllegalStateException("closed");
	}

	Component parent() {
		checkClosed();
		return parent;
	}

	Controller controller() {
		checkClosed();
		return controller;
	}

	Properties properties() {
		checkClosed();
		return properties;
	}

	@Override
	public IuComponent extend(InputStream componentArchiveSource, InputStream... providedDependencyArchiveSources)
			throws IOException, IllegalArgumentException {
		checkClosed();
		throw new UnsupportedOperationException();
	}

	@Override
	public Kind kind() {
		checkClosed();
		return kind;
	}

	@Override
	public IuComponentVersion version() {
		checkClosed();
		return version;
	}

	@Override
	public ClassLoader classLoader() {
		checkClosed();
		return classLoader;
	}

	@Override
	public Set<? extends IuType<?>> interfaces() {
		checkClosed();
		return interfaces;
	}

	@Override
	public Iterable<? extends IuType<?>> annotatedTypes(Class<? extends Annotation> annotationType) {
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
		version = null;
		properties = null;
		classLoader = null;
		interfaces = null;
		closed = true;
	}

}
