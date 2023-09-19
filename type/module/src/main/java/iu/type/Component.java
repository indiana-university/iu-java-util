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
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Properties;
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

	final Component parent;
	final Controller controller;
	private final Kind kind;
	private final Manifest manifest;
	private final String name;
	private final String version;
	private final Properties properties;
	private final ClassLoader classLoader;
	private final Set<TypeFacade<?>> interfaces;

	private Component(Component parent, Controller controller, Kind kind, Manifest manifest, String name,
			String version, Properties properties, ClassLoader classLoader, Set<String> classNames) {
		this.parent = parent;
		this.controller = controller;
		this.kind = Objects.requireNonNull(kind, "kind");
		this.manifest = Objects.requireNonNull(manifest, "manifest");
		this.name = Objects.requireNonNull(name, "name");
		this.version = Objects.requireNonNull(name, "version");
		this.properties = Objects.requireNonNull(properties, "properties");
		this.classLoader = Objects.requireNonNull(classLoader, "classLoader");

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

	Component(Component parent, Controller controller, Manifest manifest, String name, String version,
			Properties properties, ClassLoader classLoader, Set<String> classNames) {
		this(parent, controller, Kind.MODULAR_JAR, manifest, name, version, properties, classLoader, classNames);
	}

	@Override
	public IuComponent extend(Path... modulePath) {
		return ComponentFactory.newComponent(this, modulePath);
	}

	@Override
	public Kind kind() {
		return kind;
	}

	@Override
	public Manifest manifest() {
		return manifest;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String version() {
		return version;
	}

	@Override
	public ClassLoader classLoader() {
		return classLoader;
	}

	@Override
	public Set<? extends IuType<?>> interfaces() {
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

}
