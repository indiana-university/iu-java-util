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
package edu.iu.type;

import java.lang.annotation.Annotation;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.jar.Manifest;

import iu.type.ComponentFactory;

/**
 * Application component facade interface.
 * 
 * <p>
 * A component is defined by one or more <a href=
 * "https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html">Java
 * Archive (jar)</a> files. In addition to operating as a element in a class
 * and/or module path, a application components are strictly validated and
 * loaded into an isolated context.
 * </p>
 * 
 * <h2>Modular Component</h2>
 * <p>
 * The common-case component type for IU Java Utilities is a <strong>Modular
 * Component</strong>. This format has strong parity with and <em>must</em> meet
 * all requireents of the <a href="https://openjdk.org/projects/jigsaw/">Java
 * Module System</a>.
 * </p>
 * 
 * <p>
 * A <strong>modular component</strong> <em>must</em>:
 * </p>
 * <ul>
 * <li>Include a {@link ModuleDescriptor module descriptor} (module-info.class)
 * for each element in its path.</li>
 * <li>If the {@link ModuleDescriptor module descriptor} is missing from the
 * first element in the path, the {@link #of(Path...)} method will attempt to
 * load it as an <strong>unnamed</strong> component.</li>
 * <li>If the {@link ModuleDescriptor module descriptor} is missing from any
 * subsequent path element, an {@link IllegalArgumentException} will be
 * thrown.</li>
 * </ul>
 * 
 * <h2>Legacy Component</h2>
 * <p>
 * A component <em>should</em> typically be <strong>modular</strong>. However,
 * when the component is unable to meet the requirements outlined above, it may
 * be loaded as an <strong>legacy</strong> component.
 * </p>
 * 
 * <h3>IU JEE 6 Compatibility</h3>
 * <p>
 * The primary purpose of the <strong>legacy</strong> component type is to
 * support application components developed for IU JEE 6.
 * </p>
 *
 * <h2>Web Component</h2>
 * <p>
 * A component <em>may</em> be defined as a <a href=
 * "https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0#web-application-archive-file">Web
 * Application Archive</a>
 * </p>
 * 
 * <h2>Remotable Types</h2>
 * <p>
 * The introspection methods on this interface only return
 * <strong>remotable</strong> types.
 * </p>
 * <p>
 * Remotable types...
 * </p>
 * <ul>
 * <li><em>must</em> include the {@link Modifier#PUBLIC public} modifier.</li>
 * <li><em>must not</em> enclosed by another {@link Class#getEnclosingClass()
 * type}, a {@link Class#getEnclosingConstructor() constructor}, or a
 * {@link Class#getEnclosingMethod() method}.</li>
 * <li>loaded by a <strong>modular</strong> component <em>must</em> be in a
 * {@link Class#getPackageName() package} that is {@link Module#isOpen(String)
 * unconditionally open} by a {@link Module module} in the component's
 * path.</li>
 * <li>loaded by a <strong>unnamed</strong> component <em>must</em> be defined
 * by a path entry that also includes a resource named
 * <strong>META-INF/iu.properties</strong>.</li>
 * <li><em>must not</em> be in a package or module that starts with
 * <ul>
 * <li>jakarta</li>
 * <li>java</li>
 * <li>javax</li>
 * <li>jdk</li>
 * <li>com.sun</li>
 * </ul>
 * </li>
 * <li><em>must</em> be opened by a module whose name is included in the
 * comma-separated value associated with the property named
 * {@code remotableModules}, when defined by the
 * <strong>META-INF/iu-type.properties</strong> resource in the first element in
 * a modular component's path. If the component is unnamed or this property is
 * not defined, then all type meeting the criteria outlined above are considered
 * <strong>remotable</strong>.</li>
 * </ul>
 * 
 * <h2>Unsupported Component Formats</h2>
 * <p>
 * Support for the formats outlined in this section are out of scope and will
 * result in {@link IllegalArgumentException} when passed to
 * {@link #of(Path...)}.
 * </p>
 * 
 * <h3>Enterprise Application Archives</h3>
 * <p>
 * An <a href=
 * "https://jakarta.ee/specifications/platform/10/jakarta-platform-spec-10.0#application-assembly-and-deployment">Enterprise
 * Application Archive (ear)</a> file <em>contains</em> components. The IU JEE 7
 * Enterprise Archive implementation will consume this interface to compose
 * applications, but an <strong>ear</strong> file is not <em>itself</em> a
 * component. An <strong>ear</strong> file <em>must not</em> be supplied to the
 * {@link #of(Path...)} method.
 * </p>
 * 
 * <h3>Resource Adapter Archives</h3>
 * <p>
 * An <a href=
 * "https://jakarta.ee/specifications/connectors/2.1/jakarta-connectors-spec-2.1#resource-adapter-archive">Resource
 * Adapter Archive (rar)</a> file <em>contains</em> Java and non-Java
 * components. The IU JEE 7 Resource Adapter implementation will consume this
 * interface to compose applications, but an <strong>ear</strong> file is not
 * <em>itself</em> a component. An <strong>ear</strong> file <em>must not</em>
 * be supplied to the {@link #of(Path...)} method.
 * </p>
 * 
 * <h3>Shaded (Uber-)Jar Archive</h3>
 * <p>
 * Maven provides the
 * <a href="https://maven.apache.org/plugins/maven-shade-plugin/">shade</a>
 * plugin for creating consolidated application archives (a.k.a. "uber-jar")
 * that include all dependencies in a standalone format. While this format is
 * useful for creating executable jars and other types of standalone Java
 * applications, it breaks encapsulation and so is considered an anti-pattern
 * for runtime-composable application components. An uber-jar <em>must not</em>
 * be supplied to the {@link #of(Path...)} method.
 * </p>
 */
public interface IuComponent {

	/**
	 * Enumerates the different kinds of components that may be loaded using this
	 * interface.
	 */
	enum Kind {
		/**
		 * Designates <strong>modular</strong> component defined by one or more Java
		 * Archive (jar) files.
		 */
		MODULAR_JAR,

		/**
		 * Designates an <strong>legacy</strong> component defined by a Java Archive
		 * (jar) file.
		 */
		LEGACY_JAR,

		/**
		 * Designates <strong>modular</strong> component defined by a Web Application
		 * Archive (war) file.
		 */
		MODULAR_WAR,

		/**
		 * Designates an <strong>legacy</strong> component defined by a Web Application
		 * Archive (war) file.
		 */
		LEGACY_WAR;
	}

	/**
	 * Creates a new component.
	 * 
	 * @param path Path to the component archive, optionally followed by addition
	 *             modular jar files if the component is a modular jar.
	 * @return component
	 * @throws IllegalArgumentException If the jar is missing, unreadable, or
	 *                                  invalid.
	 */
	static IuComponent of(Path... path) throws IllegalArgumentException {
		return ComponentFactory.newComponent(path);
	}

	/**
	 * Creates a component that delegates to this component.
	 * <p>
	 * A delegating component <em>must</em>:
	 * </p>
	 * <ul>
	 * <li>Be loaded by an isolated {@link #classLoader() class loader} that
	 * delegates to this component's {@link #classLoader()}.</li>
	 * <li>Include all of this component's {@link #interfaces() interfaces},
	 * {@link #annotatedTypes(Class) annotation types}, and
	 * {@link #resources()}.</li>
	 * <li>Delegate to a component whose {@link #kind() kind} is
	 * {@link Kind#MODULAR_JAR} or {@link Kind#LEGACY_JAR}.</li>
	 * <li>Delegate to a <strong>modular</strong> component if the delegating
	 * component is <strong>modular</strong>.</li>
	 * </ul>
	 * 
	 * @param modulePath Paths to valid {@link Module}-defining jar files.
	 * @return delegating component
	 */
	IuComponent extend(Path... modulePath);

	/**
	 * Gets the kind of component.
	 * 
	 * @return {@link Kind}
	 */
	Kind kind();

	/**
	 * Gets the {@link Manifest} for the component-defining archive.
	 * 
	 * @return {@link Manifest}
	 */
	Manifest manifest();

	/**
	 * Gets the component name.
	 * 
	 * @return component name
	 */
	String name();

	/**
	 * Gets the component version.
	 * 
	 * @return component version
	 */
	String version();

	/**
	 * Gets the {@link ClassLoader} for this component.
	 * 
	 * @return {@link ClassLoader}
	 */
	ClassLoader classLoader();

	/**
	 * Gets all of the component's public interfaces.
	 * 
	 * <p>
	 * This method is intended to support resource and service discovery of public
	 * APIs defined by the applications. <em>Must not</em> include interfaces from a
	 * standard Java or JEE module.
	 * </p>
	 * 
	 * @return interface facades
	 */
	Iterable<? extends IuType<?>> interfaces();

	/**
	 * Gets all types in the component annotated by a specific type.
	 * 
	 * @param annotationType annotation type
	 * @return annotated type facades
	 */
	Iterable<? extends IuType<?>> annotatedTypes(Class<? extends Annotation> annotationType);

	/**
	 * Gets component's resources.
	 * 
	 * @return resources
	 */
	Iterable<? extends IuResource<?>> resources();

}
