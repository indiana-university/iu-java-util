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

import java.lang.ModuleLayer.Controller;
import java.lang.annotation.Annotation;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.type.IuComponent;
import edu.iu.type.IuResource;
import edu.iu.type.IuType;

class Component implements IuComponent {

	private static final Logger LOG = Logger.getLogger(Component.class.getName());

	private static final Set<String> NON_REMOTEABLE = Set.of("java", "javax", "jakarta", "jdk", "com.sun");

	private static boolean isRemotable(String name) {
		for (var nonRemoteable : NON_REMOTEABLE)
			if (name.startsWith(nonRemoteable))
				return false;
		return true;
	}

	private static boolean isRemotable(Class<?> ifc) {
		if (!ifc.isInterface())
			return false;
		if (ifc.isAnnotation())
			return false;

		var module = ifc.getModule();
		if (!module.isNamed())
			return isRemotable(ifc.getPackageName());

		if (!module.isOpen(ifc.getPackageName()))
			return false;

		return isRemotable(module.getName());
	}

	private Component parent;
	private ComponentModuleFinder moduleFinder;
	private Controller controller;
	private Kind kind;
	private String name;
	private String version;
	private Properties properties;
	private ClassLoader classLoader;
	private Set<TypeFacade<?>> interfaces;
	private Queue<Path> tempFiles;
	private boolean closed;

	private Component(Component parent, ComponentModuleFinder moduleFinder, Controller controller, Kind kind,
			String name, String version, Properties properties, ClassLoader classLoader,
			Set<String> classNames, Queue<Path> tempFiles) {
		this.parent = parent;
		this.moduleFinder = moduleFinder;
		this.controller = controller;
		this.kind = Objects.requireNonNull(kind, "kind");
		this.name = Objects.requireNonNull(name, "name");
		this.version = Objects.requireNonNull(version, "version");
		this.properties = Objects.requireNonNull(properties, "properties");
		this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
		this.tempFiles = Objects.requireNonNull(tempFiles, "tempFiles");

		Set<TypeFacade<?>> interfaces = new LinkedHashSet<>();
		if (parent != null)
			interfaces.addAll(parent.interfaces);
		for (var className : classNames)
			try {
				var loadedClass = classLoader.loadClass(className);
				if (isRemotable(loadedClass))
					interfaces.add((TypeFacade<?>) TypeFactory.resolve(loadedClass));
			} catch (Throwable e) {
				LOG.log(Level.WARNING, e, () -> "Invalid class " + className + " in component " + name);
			}
		this.interfaces = Collections.unmodifiableSet(interfaces);
	}

	Component(Component parent, ComponentModuleFinder moduleFinder, Controller controller,
			String name, String version, Properties properties, ClassLoader classLoader, Set<String> classNames,
			Queue<Path> tempFiles) {
		this(parent, moduleFinder, controller, Kind.MODULAR_JAR, name, version, properties, classLoader,
				classNames, tempFiles);
	}

	Controller controller() {
		return controller;
	}

	@Override
	public IuComponent extend(Path... modulePath) {
		if (closed)
			throw new IllegalStateException();
		return ComponentFactory.newComponent(this, modulePath);
	}

	@Override
	public Kind kind() {
		if (closed)
			throw new IllegalStateException();
		return kind;
	}

	@Override
	public String name() {
		if (closed)
			throw new IllegalStateException();
		return name;
	}

	@Override
	public String version() {
		if (closed)
			throw new IllegalStateException();
		return version;
	}

	@Override
	public ClassLoader classLoader() {
		if (closed)
			throw new IllegalStateException();
		return classLoader;
	}

	@Override
	public Set<? extends IuType<?>> interfaces() {
		if (closed)
			throw new IllegalStateException();
		return interfaces;
	}

	@Override
	public Iterable<IuType<?>> annotatedTypes(Class<? extends Annotation> annotationType) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<IuResource<?>> resources() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
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

		if (tempFiles != null)
			while (!tempFiles.isEmpty()) {
				var tempFile = tempFiles.poll();
				try {
					Files.delete(tempFile);
					if (Files.exists(tempFile))
						LOG.log(Level.WARNING, () -> "Ttemp file still exists after delete " + tempFile);
				} catch (Throwable e) {
					LOG.log(Level.WARNING, e, () -> "Failed to clean up temp file " + tempFile);
				}
			}

		parent = null;
		moduleFinder = null;
		controller = null;
		kind = null;
		name = null;
		version = null;
		properties = null;
		classLoader = null;
		interfaces = null;
		closed = true;
	}

}
