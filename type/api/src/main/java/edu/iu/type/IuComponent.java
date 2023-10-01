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

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.jar.Manifest;

import edu.iu.type.spi.TypeImplementation;

/**
 * Facade interface representing an application component.
 * 
 * <p>
 * A <strong>component</strong> is defined by one or more <a href=
 * "https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html">Java
 * (jar) Archives</a>. <strong>Components</strong> are <strong>named</strong>,
 * <strong><a href="https://semver.org">versioned</a></strong>, and
 * <strong>isolated</strong> at runtime.
 * </p>
 * 
 * <h2>Component Archives</h2>
 * <p>
 * A <strong>component archive</strong> provides the resources and type
 * definitions for a <strong>component</strong>. Each <strong>component
 * archive</strong> <em>must</em> be validated before the
 * <strong>component</strong> may be loaded.
 * </p>
 * 
 * <p>
 * All <strong>component archives</strong> <em>must</em>:
 * </p>
 * <ul>
 * <li>Be a <a href=
 * "https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html">jar
 * archive</a> built using <a href="https://maven.apache.org/">Apache
 * Maven</a>.</li>
 * <li><em>Not</em> be directory in a file system, either originally or
 * unpacked.</li>
 * <li>Include a well-formed {@link Manifest manifest} in
 * {@code META-INF/MANIFEST.MF}.</li>
 * <li><em>Not</em> include {@code Main-Class} in the
 * {@link Manifest#getMainAttributes() manifest's main attribute section}.</li>
 * <li>Include exactly one <a href=
 * "https://maven.apache.org/shared/maven-archiver/index.html">META-INF/maven/.../pom.properties</a>
 * entry.</li>
 * </ul>
 * 
 * <h2>Modular Components</h2>
 * <p>
 * <strong>Components</strong> <em>should</em> be <a href=
 * "https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html#modular-jar-files"><strong>modular</strong></a>.
 * A <strong>Modular component</strong> that includes {@link ModuleDescriptor
 * module-info.class} in its <strong>archive</strong> <em>must</em> meet all
 * requirements of the <a href="https://openjdk.org/projects/jigsaw/">Java
 * Module System</a>.
 * </p>
 * 
 * <p>
 * A <strong>component archive</strong> that does not include
 * {@link ModuleDescriptor module-info.class} <em>may</em> still be treated as a
 * <strong>modular component</strong> as long as it doesn't have any
 * <strong>legacy dependencies</strong>.
 * </p>
 * 
 * <h2>Legacy Components</h2>
 * <p>
 * To preserve compatibility for <strong>components</strong> that
 * <strong>depend</strong> on IU JEE 6, Servlet 4, and EJB 3 <strong>legacy
 * components</strong> with such dependencies <em>must</em> be loaded in an
 * {@link Module#isNamed() unnamed module} by a {@link ClassLoader class loader}
 * that does not delegate to the {@link ClassLoader#getSystemClassLoader()
 * system class loader}.
 * </p>
 * 
 * <p>
 * A <strong>component</strong> <em>must</em> be loaded as a <strong>legacy
 * components</strong> if its archive does not include {@link ModuleDescriptor
 * module-info.class} and meets at least one of the following additional
 * criteria:
 * </p>
 * <ul>
 * <li>Does not include {@code Extension-List} in its
 * {@link Manifest#getMainAttributes() manifest's main attributes section}, or
 * if {@code Extension-List} includes {@code javax.servlet-api},
 * {@code javax.ejb-api}, {@code jakarta.servlet-api} with version &lt; 6.0, or
 * {@code jakarta.ejb-api} with version &lt; 4.0. If both
 * {@link ModuleDescriptor module-info.class} and a <strong>legacy
 * dependency</strong> is named, the <strong>component</strong> will be loaded
 * as <strong>modular</strong>.</li>
 * <li>Includes {@code META-INF/iu.properties}. When both
 * {@code META-INF/iu.properties} and {@link ModuleDescriptor module-info.class}
 * are present, the <strong>archive</strong> will be rejected with
 * {@link IllegalArgumentException}.</li>
 * </ul>
 * 
 * <p>
 * Types defined by <strong>legacy components</strong> that
 * <strong>depend</strong> on packages named {@code javax.*} <em>may</em> be
 * converted to access equivalent types named {@code jakarta.*} when an
 * equivalent package is <strong>inherited</strong> from a <strong>modular
 * parent component</strong>. To facilitate this consideration,
 * {@link IuType#referTo(Type) type references} and {@link IuAnnotatedElement
 * annotated elements} <em>may</em> automatically be converted to
 * <strong>non-legacy</strong> equivalents when performing introspection on
 * <strong>legacy components</strong>.
 * </p>
 * 
 * <p>
 * <strong>Legacy components</strong> <em>may</em> consider "<em>should</em>"
 * requirements outlined below as <em>optional</em>.
 * </p>
 * 
 * <h2>Dependencies</h2>
 * 
 * <p>
 * A <strong>component</strong> <em>may</em> <strong>depend</strong> on other
 * <strong>components</strong>. All <strong>dependencies</strong> <em>must</em>
 * be present in the <strong>component's path</strong>, or as
 * <strong>dependencies</strong> of its <strong>parent component</strong>, when
 * the <strong>component</strong> is loaded.
 * </p>
 * 
 * <p>
 * <strong>Dependencies</strong> <em>may</em> be <strong>provided</strong> to a
 * <strong>component's path</strong> by passing additional {@link InputStream}
 * entries to {@link #of(InputStream, InputStream...)} or
 * {@link #extend(InputStream, InputStream...)}.
 * </p>
 * 
 * <p>
 * <strong>Dependencies</strong> <em>may</em> be added to a <strong>component's
 * path</strong> by <strong>bundling</strong> <a href=
 * "https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html">jars</a>
 * in its <strong>archive</strong>. <strong>Component archives</strong> with
 * <strong>bundled dependencies</strong> <em>should</em> include a
 * {@code Class-Path} entry in the {@link Manifest#getMainAttributes()
 * manifest's main attribute section}. <strong>Bundled dependencies</strong>
 * must not also be <strong>provided</strong>.
 * </p>
 * 
 * <p>
 * <strong>Dependencies</strong> <em>may</em> be <strong>inherited</strong> from
 * a <strong>{@link #extend(InputStream, InputStream...) parent
 * component}</strong>. <strong>Inherited dependencies</strong> must not also be
 * <strong>bundled</strong> or <strong>provided</strong>.
 * </p>
 * 
 * <p>
 * <strong>Components</strong> <em>should</em> enumerate dependencies using the
 * <strong>Extension-List</strong> manifest attribute. When present, the
 * <strong>dependencies</strong> referred to in the extension list will be
 * enforced using <a href=
 * "https://maven.apache.org/shared/maven-archiver/index.html">pom.properties</a>
 * {@code artifactId} and {@code version} as present the <strong>component's
 * path</strong> or <strong>inherited</strong> by its <strong>parent
 * component</strong>.
 * </p>
 * 
 * <p>
 * Note that {@code Extension-List} was originally intended for applets, which
 * are no longer supported by Java SE or related products. However, <a href=
 * "https://jakarta.ee/specifications/platform/10/jakarta-platform-spec-10.0#installed-libraries">JEE
 * 10 Section 8.2.2</a> <em>requires</em> this attribute to be supported for all
 * <strong>component</strong> types, and clarifies by example that
 * {@code extension-Specification-Version} may be used to declare that an
 * installed library meet a minimum specification version as opposed to
 * declaring a specific {@code extension-Implementation-Version}. This
 * requirement by JEE 10 supersedes the implied deprecation of
 * {@code Extension-List} by Java SE.
 * </p>
 * 
 * <p>
 * <a href="https://maven.apache.org/shared/maven-archiver/index.html">Maven
 * Archiver</a> includes support for generating the {@code Class-Path} and
 * {@code Extension-List} attributes from the project's dependency artifacts.
 * </p>
 * <ul>
 * <li>The {@code addClasspath} configuration parameter may be {@code true} to
 * enumerate all
 * <a href="https://maven.apache.org/pom.html#dependencies">compile and runtime
 * scoped dependencies</a> in the {@code Class-Path} attribute. However, this
 * does not bundle the related artifacts in the resulting <strong>component
 * archive</strong>. When using {@code addClasspath}, a <strong>component
 * archive's</strong> Maven project should also configure the <a href=
 * "https://maven.apache.org/plugins/maven-dependency-plugin/copy-dependencies-mojo.html">Maven
 * Dependency Plugin</a> to copy related artifacts from the local Maven
 * repository into the archive's output folder. For example:
 * 
 * <pre>
 *   &lt;plugin&gt;
 *      &lt;artifactId&gt;maven-jar-plugin&lt;/artifactId&gt;
 *      &lt;executions&gt;
 *        &lt;execution&gt;
 *          &lt;id&gt;default-jar&lt;/id&gt;
 *          &lt;configuration&gt;
 *            &lt;archive&gt;
 *              &lt;manifest&gt;
 *                &lt;addClasspath&gt;true&lt;/addClasspath&gt;
 *                &lt;classpathPrefix&gt;META-INF/lib&lt;/classpathPrefix&gt;
 *              &lt;/manifest&gt;
 *            &lt;/archive&gt;
 *          &lt;/configuration&gt;
 *        &lt;/execution&gt;
 *      &lt;/executions&gt;
 *    &lt;/plugin&gt;
 *    &lt;plugin&gt;
 *      &lt;artifactId&gt;maven-dependency-plugin&lt;/artifactId&gt;
 *      &lt;executions&gt;
 *        &lt;execution&gt;
 *          &lt;id&gt;embed-dependencies&lt;/id&gt;
 *          &lt;phase&gt;process-resources&lt;/phase&gt;
 *          &lt;goals&gt;
 *            &lt;goal&gt;copy-dependencies&lt;/goal&gt;
 *          &lt;/goals&gt;
 *          &lt;configuration&gt;
 *            &lt;outputDirectory&gt;${project.build.outputDirectory}/META-INF/lib&lt;/outputDirectory&gt;
 *          &lt;/configuration&gt;
 *        &lt;/execution&gt;
 *      &lt;/executions&gt;
 *    &lt;/plugin&gt;
 * </pre>
 * 
 * </li>
 * <li><a href="https://maven.apache.org/shared/maven-archiver/index.html">Maven
 * Archiver documentation</a> as well as the <a href=
 * "https://github.com/apache/maven-archiver/blob/master/src/main/java/org/apache/maven/archiver/MavenArchiver.java#L403">Maven
 * Archiver source code related to {@code addExtensions}</a> reveal only minimal
 * support for {@code Extension-List} with caveats related to applets indicated
 * in inline comments. This configuration parameter may be used by a
 * <strong>component archive's</strong> Maven project to declare that specific
 * versions of all
 * <a href="https://maven.apache.org/pom.html#dependencies">compile and runtime
 * scoped dependencies</a> be <strong>provided</strong> to the
 * <strong>component</strong>. The {@code addExtensions} archive configuration
 * parameter <em>should not</em> be {@code true} when {@code addClasspath} is
 * also {@code true}.
 * </ul>
 * 
 * <h2>Class Loading</h2>
 * 
 * <p>
 * {@link IuComponent} is an <a href=
 * "https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html">application</a>
 * bootstrap utility that shields loaded <strong>components</strong> from
 * {@link ClassLoader class loading} and type introspection details. An <a href=
 * "https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html">application</a>
 * that uses the {@code iu.util.type} module to compose
 * <strong>components</strong> <em>should</em> include {@code iu-java-type.jar}
 * in the JVM module path.
 * </p>
 * 
 * <p>
 * A <strong>component</strong> <em>must not</em> have
 * {@link AccessibleObject#canAccess(Object) access} to the {@code iu.util.type}
 * module instance it was loaded from or to any other module instances present
 * in the same {@link ModuleLayer module layer}.
 * </p>
 * 
 * <p>
 * A <strong>component</strong> <em>may</em> list the {@code iu.util.type}
 * module as a <strong>dependency</strong>. When listed as a
 * <strong>dependency</strong>, the {@code iu.util.type} module instance
 * available to the <strong>component</strong> <em>must</em> exist in a separate
 * {@link ModuleLayer layer} from the instance that loaded the
 * <strong>component</strong>.
 * 
 * <p>
 * Regardless of which {@code iu.util.type} module instance loaded a
 * <strong>component</strong>, Types loaded by the <strong>component</strong>
 * <em>must not</em> have {@link AccessibleObject#canAccess(Object) access} to
 * types loaded by the {@link ClassLoader#getSystemClassLoader() system class
 * loader}. Types loaded by the {@link ClassLoader#getSystemClassLoader() system
 * class loader} must not be visible to types loaded by the
 * <strong>component</strong>.
 * </p>
 * 
 * <p>
 * Conversely, types loaded by the {@link ClassLoader#getSystemClassLoader()
 * system class loader}, especially all types in the {@code iu.util.type} module
 * <em>must</em> have {@link AccessibleObject#canAccess(Object) access} to all
 * types in all <strong>components</strong>.
 * </p>
 * 
 * <p>
 * <strong>Components</strong> may be
 * {@link #extend(InputStream, InputStream...) extended}. An <strong>extended
 * component</strong> includes all types defined by its <strong>parent
 * component</strong> and delegates to its <strong>parent component's</strong>
 * {@link ClassLoader class loader}. A <strong>component</strong> <em>must
 * not</em> have {@link AccessibleObject#canAccess(Object) access} to other
 * <strong>components</strong> extended from the same <strong>parent</strong>.
 * </p>
 *
 * <h2>Web Component</h2>
 * <p>
 * A <strong>component</strong> <em>may</em> be defined by a <a href=
 * "https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0#web-application-archive-file">Web
 * Application Archive</a>. <strong>Web components</strong> <em>should</em>
 * adhere to the requirements outlined in <a href=
 * "https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0#web-applications">Java
 * Servlet 6.0 Section 10</a>.
 * </p>
 * 
 * <p>
 * <strong>Web components</strong> <em>must</em> only <strong>bundle</strong>
 * dependencies in the <strong>WEB-INF/lib/</strong> folder, and <em>should
 * not</em> include the <strong>Class-Path</strong> manifest attribute.
 * </p>
 * 
 * <p>
 * A <strong>web component</strong> <em>should</em> include
 * {@code Extension-List} in its {@link Manifest#getMainAttributes() manifest's
 * main attributes section} to declare all <strong>provided
 * dependencies</strong>.
 * </p>
 * 
 * <p>
 * A <strong>web component</strong> <em>must not</em> be listed as a dependency.
 * </p>
 * 
 * <h2>Unsupported Component Formats</h2>
 * <p>
 * Support for the formats outlined in this section are out of scope and will
 * result in {@link IllegalArgumentException} when passed to
 * {@link #of(InputStream, InputStream...)}.
 * </p>
 * 
 * <h3>Enterprise Application Archives</h3>
 * <p>
 * An <a href=
 * "https://jakarta.ee/specifications/platform/10/jakarta-platform-spec-10.0#application-assembly-and-deployment">Enterprise
 * Application Archive (ear)</a> <strong>contains components</strong>. The IU
 * JEE 7 Enterprise Archive implementation will consume this interface to
 * compose <strong>applications</strong>, but an <a href=
 * "https://jakarta.ee/specifications/platform/10/jakarta-platform-spec-10.0#application-assembly-and-deployment">ear</a>
 * is not a component and <em>must not</em> be supplied to the
 * {@link #of(InputStream, InputStream...)} method.
 * </p>
 * 
 * <h3>Resource Adapter Archives</h3>
 * <p>
 * An <a href=
 * "https://jakarta.ee/specifications/connectors/2.1/jakarta-connectors-spec-2.1#resource-adapter-archive">Resource
 * Adapter Archive (rar)</a> <strong>contains</strong> Java and non-Java shared
 * library archives. The IU JEE 7 Resource Adapter implementation will consume
 * this interface to compose resource adapters, but a <a href=
 * "https://jakarta.ee/specifications/connectors/2.1/jakarta-connectors-spec-2.1#resource-adapter-archive">rar</a>
 * file is not a <strong>component</strong> and <em>must not</em> be supplied to
 * the {@link #of(InputStream, InputStream...)} method.
 * </p>
 * 
 * <h3>Shaded (Uber-)Jar Archive</h3>
 * <p>
 * Maven provides the
 * <a href="https://maven.apache.org/plugins/maven-shade-plugin/">shade</a>
 * plugin for creating "uber-jar" archives that flatten all dependencies into a
 * single archive. While this format is useful for creating executable jars and
 * other types of standalone Java applications, it breaks encapsulation and so
 * is considered an anti-pattern for runtime-composable application components.
 * An uber-jar <em>must not</em> be supplied to the
 * {@link #of(InputStream, InputStream...)} method.
 * </p>
 * 
 * @see ClassLoader
 * @see ModuleLayer
 */
public interface IuComponent extends AutoCloseable {

	/**
	 * Enumerates the different kinds of components that may be loaded using this
	 * interface.
	 */
	enum Kind {
		/**
		 * Designates <strong>modular</strong> component defined by one or more Java
		 * Archive (jar) files.
		 */
		MODULAR_JAR(true, false),

		/**
		 * Designates an <strong>legacy</strong> component defined by a Java Archive
		 * (jar) file.
		 */
		LEGACY_JAR(false, false),

		/**
		 * Designates <strong>modular</strong> component defined by a Web Application
		 * Archive (war) file.
		 */
		MODULAR_WAR(true, true),

		/**
		 * Designates an <strong>legacy</strong> component defined by a Web Application
		 * Archive (war) file.
		 */
		LEGACY_WAR(false, true);

		private final boolean modular;
		private final boolean web;

		private Kind(boolean modular, boolean web) {
			this.modular = modular;
			this.web = web;
		}

		/**
		 * Determines if the <strong>component</strong> is <strong>modular</strong>.
		 * 
		 * @return {@code true} if <strong>modular</strong>; else {@code false}
		 */
		public boolean isModular() {
			return modular;
		}

		/**
		 * Determines if the <strong>component</strong> is a <strong>web
		 * component</strong>.
		 * 
		 * @return {@code true} if <strong>web component</strong>; else {@code false}
		 */
		public boolean isWeb() {
			return web;
		}
	}

	/**
	 * Validates a <strong>component archive</strong>, all <strong>dependency
	 * archives</strong>, and loads a <strong>component</strong>.
	 * 
	 * @param componentArchiveSource           {@link InputStream} for reading the
	 *                                         <strong>component archive</strong>.
	 * @param providedDependencyArchiveSources {@link InputStream}s for reading all
	 *                                         <strong>provided dependency
	 *                                         archives</strong>.
	 * @return component
	 * @throws IOException              If the <strong>component archive</strong> or
	 *                                  any <strong>dependency archives</strong> are
	 *                                  unreadable.
	 * @throws IllegalArgumentException If the <strong>component archive</strong> or
	 *                                  any <strong>dependency archives</strong>
	 *                                  invalid.
	 */
	static IuComponent of(InputStream componentArchiveSource, InputStream... providedDependencyArchiveSources)
			throws IOException, IllegalArgumentException {
		return TypeImplementation.SPI.createComponent(componentArchiveSource, providedDependencyArchiveSources);
	}

	/**
	 * Validates <strong>component archives</strong>, all <strong>dependency
	 * archives</strong>, and loads a <strong>component</strong> that
	 * <strong>extends</strong> this <strong>component</strong>.
	 * 
	 * @param componentArchiveSource           {@link InputStream} for reading the
	 *                                         <strong>component archive</strong>.
	 * @param providedDependencyArchiveSources {@link InputStream}s for reading all
	 *                                         <strong>provided dependency
	 *                                         archive</strong>.
	 * @return component
	 * @throws IOException              If the <strong>component archive</strong> or
	 *                                  any <strong>dependency archives</strong> are
	 *                                  unreadable.
	 * @throws IllegalArgumentException If the <strong>component archive</strong> or
	 *                                  any <strong>dependency archives</strong>
	 *                                  invalid.
	 */
	IuComponent extend(InputStream componentArchiveSource, InputStream... providedDependencyArchiveSources)
			throws IOException, IllegalArgumentException;

	/**
	 * Gets the kind of component.
	 * 
	 * @return {@link Kind}
	 */
	Kind kind();

	/**
	 * Gets the component version.
	 * 
	 * @return {@link IuComponentVersion}
	 */
	IuComponentVersion version();

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
	 * APIs defined by the applications. A <strong>modular component</strong>
	 * <em>Must not</em> include interfaces from a module that does not
	 * {@link Module#isOpen(String, Module) open} the package containing the
	 * interface to the {@code iu.util.type.impl} module. A <strong>legacy
	 * component</strong> <em>Must not</em> include interfaces from any
	 * <strong>dependencies</strong> unless the <strong>dependency</strong> is a
	 * {@link Kind#LEGACY_JAR} that includes an {@code META-INF/iu.properties}
	 * resource.
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
	 * <p>
	 * Includes:
	 * </p>
	 * <ul>
	 * <li>Static web resources when {@link #kind()}.{@link Kind#isWeb() isWeb()} is
	 * true, with {@link IuResource#type() type} {@code byte[]}. This includes
	 * zero-length binary resources for folder entries ({@link IuResource#name()}
	 * ends with '/').</li>
	 * <li>All types in the <strong>component</strong> and all
	 * <strong>dependencies</strong> declared as part of an package open to
	 * {@code iu.util.type.impl}, or in a <strong>legacy component</strong> with
	 * {@code META-INF/iu.properties}, that includes the @Resource or @Resources
	 * annotation where either the <strong>resource type</strong> designated by the
	 * annotation {@link Class#isAssignableFrom(Class) is assignable from} the
	 * <strong>annotated type</strong>, or the designated type is an interface and
	 * the <strong>annotated type</strong> is an {@link InvocationHandler}.</li>
	 * </ul>
	 * 
	 * <p>
	 * <em>Must not</em> include resources available from
	 * {@link ClassLoader#getResources(String)} and related methods. Although the
	 * name and functionality are similar, the {@code iu.util.type} module is
	 * intended only to simplify, not duplicate, base platform functionality
	 * provided by the platform.
	 * </p>
	 * 
	 * <p>
	 * This method discovers resources in a manner that deviates from the behavior
	 * described in <a href=
	 * "https://jakarta.ee/specifications/annotations/2.1/annotations-spec-2.1#jakarta-annotation-resource">Jakarta
	 * Annotations 2.1 Section 3.3</a>. This deviation from the standard is an
	 * important element of IU JEE's self-defining behavior for application
	 * resources, and eliminates the need for a deployment descriptor that describes
	 * the <strong>resources</strong> available to a <strong>component</strong>.
	 * </p>
	 * <p>
	 * The standard specification describes the behavior of @Resource on a type as
	 * declaring a dependency that must be available for lookup via JNDI and
	 * requires that name() be non-empty. That behavior <em>should</em> be preserved
	 * for cases when the <strong>resource type</strong> is not assignable from the
	 * <strong>annotated type</strong>, however when the <strong>resource
	 * type</strong> is assignable or is and interface and the <strong>annotated
	 * type</strong> implements {@link InvocationHandler}, the <strong>annotated
	 * type</strong> itself is considered the <strong>resource implementation
	 * type</strong> and the default <strong>resource name</strong> is the
	 * {@link Class#getSimpleName() simple name} of <strong>resource type</strong>.
	 * When the <strong>annotated type</strong> is an {@link InvocationHandler},
	 * then the <strong>resource type</strong> <em>must</em> be an {@code interface}
	 * in order to be discovered, otherwise the default <strong>resource
	 * type</strong> is the first non-platform interface implemented by the
	 * <strong>resource implementation type</strong>, or the <strong>resource
	 * implementation type</strong> itself if no non-platform interfaces are
	 * implemented.
	 * </p>
	 * <p>
	 * <strong>Resources</strong> discovered in this manner use
	 * {@link IuConstructor#exec(Object...)} with no args to create an instance.
	 * When shareable(), {@link IuResource#get()} returns a pre-constructed single
	 * instance; else each invocation returns a new instance. When the
	 * <strong>annotated type</strong> is an {@link InvocationHandler}, a
	 * {@link Proxy} is created with the <strong>resource type</strong> as its only
	 * interface.
	 * </p>
	 * <p>
	 * Note that <strong>resources</strong> provided by this method do not have
	 * {@link IuField field} or {@link IuProperty property} bindings applied. That
	 * binding behavior would require a fully formed JNDI context be available and
	 * is the responsibility JEE Container, via the {@code iu.jee.resources} module,
	 * not this base utilities module.
	 * </p>
	 * 
	 * @return resources
	 */
	Iterable<? extends IuResource<?>> resources();

	@Override
	void close();

}
