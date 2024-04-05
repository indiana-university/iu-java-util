/*
 * Copyright © 2024 Indiana University
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
package edu.iu.type.base;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import edu.iu.IuException;
import edu.iu.IuStream;
import edu.iu.UnsafeFunction;
import edu.iu.UnsafeRunnable;

/**
 * Initializes a temporary file with fail-safe delete when initialization fails.
 */
public final class TemporaryFile {

	private static final ThreadLocal<Queue<Path>> PENDING = new ThreadLocal<>();

	/**
	 * Tracks temp files created by an initialization task, and either deletes all
	 * created if an error is thrown, or returns a thunk that may be used to deletes
	 * all files on teardown.
	 * 
	 * @param task initialization task
	 * @return destroy thunk
	 * @throws IOException from {@link IORunnable}
	 */
	public static UnsafeRunnable init(UnsafeRunnable task) throws IOException {
		final Queue<Path> tempFilesToRestore = PENDING.get();
		final Queue<Path> tempFiles = new ArrayDeque<>();
		final UnsafeRunnable teardown = () -> {
			Throwable e = null;
			for (final var path : tempFiles)
				e = IuException.suppress(e, () -> Files.deleteIfExists(path));
			if (e != null)
				throw IuException.checked(e, IOException.class);
		};

		try {
			PENDING.set(tempFiles);
			task.run();
			return teardown;
		} catch (Throwable e) {
			IuException.suppress(e, teardown);
			throw IuException.checked(e, IOException.class);
		} finally {
			if (tempFilesToRestore == null)
				PENDING.remove();
			else
				PENDING.set(tempFilesToRestore);
		}
	}

	/**
	 * Initializes a temp file.
	 * 
	 * @param <T>                 initialization result
	 * @param tempFileInitializer initialization function
	 * @return result of initialization; <em>must</em> contain
	 *         implementation-specific logic to delete the temp file as part of
	 *         destroying the initialized resource
	 * @throws IOException If an I/O error is thrown from the initializer
	 */
	public static <T> T init(UnsafeFunction<Path, T> tempFileInitializer) throws IOException {
		class Box {
			T initialized;
		}
		final var box = new Box();

		UnsafeRunnable init = () -> {
			Path temp = Files.createTempFile("iu-type-", ".jar");
			PENDING.get().offer(temp);
			box.initialized = tempFileInitializer.apply(temp);
		};

		if (PENDING.get() == null)
			init(init);
		else
			IuException.checked(IOException.class, init);

		return box.initialized;
	}

	/**
	 * Copies data from an input stream and outputs to a temporary file.
	 * 
	 * @param in supplies data
	 * @return {@link Path} to a temporary file with a copy of the data
	 */
	public static Path of(InputStream in) {
		return IuException.unchecked(() -> init(temporaryFile -> {
			try (final var out = Files.newOutputStream(temporaryFile)) {
				IuStream.copy(in, out);
			}
			return temporaryFile;
		}));
	}

	/**
	 * Copies data from an input stream and outputs to a temporary file.
	 * 
	 * @param url supplies data; <em>must</em> refer to a file or jar entry from a
	 *            local filesystem. The method <em>should</em> only be used with
	 *            values from {@link ClassLoader#getResource(String)} or
	 *            {@link ClassLoader#getResources(String)}
	 * @return {@link Path} to the canonical file indicated by the URL, or to a
	 *         temporary file with a copy of the Jar file entry
	 */
	public static Path of(URL url) {
		return with(url, o -> {
			if (o instanceof Path)
				return (Path) o;
			else
				return of((InputStream) o);
		});
	}

	/**
	 * Reads a mixed class/module path for loading a component from a bundled
	 * classpath resource.
	 * 
	 * <p>
	 * This package format expected by this method is a specific instance of
	 * Enterprise Archive (EAR) useful for bootstrapping a resource adapter or
	 * application client module as a iu-java-type compatible component. In the
	 * sample Maven config below, the project that includes the embedding pom
	 * segment uses this method to convert the embedded component EAR to a path.
	 * </p>
	 * 
	 * <pre>
	 * final var path = TemporaryFile.of(getClass().getClassLoader().getResource("META-INF/client/my-bundle.jar");
	 * </pre>
	 * 
	 * <h2>bundle pom</h2>
	 * 
	 * <pre>
	&lt;build&gt;
	&lt;plugins&gt;
		&lt;plugin&gt;
			&lt;artifactId&gt;maven-assembly-plugin&lt;/artifactId&gt;
			&lt;executions&gt;
				&lt;execution&gt;
					&lt;id&gt;bundle-distribution&lt;/id&gt;
					&lt;goals&gt;
						&lt;goal&gt;single&lt;/goal&gt;
					&lt;/goals&gt;
					&lt;phase&gt;package&lt;/phase&gt;
					&lt;configuration&gt;
						&lt;descriptors&gt;
							&lt;descriptor&gt;src/assembly/bundle.xml&lt;/descriptor&gt;
						&lt;/descriptors&gt;
					&lt;/configuration&gt;
				&lt;/execution&gt;
			&lt;/executions&gt;
		&lt;/plugin&gt;
	 * </pre>
	 * 
	 * <h2>
	 * src/assembly/bundle.xml
	 * </h2>
	 * 
	 * <pre>
	&lt;assembly xmlns="http://maven.apache.org/ASSEMBLY/2.2.0"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation=
	"http://maven.apache.org/ASSEMBLY/2.2.0 http://maven.apache.org/xsd/assembly-2.2.0.xsd"&gt;
	
	&lt;id&gt;bundle&lt;/id&gt;
	
	&lt;includeBaseDirectory&gt;false&lt;/includeBaseDirectory&gt;
	&lt;formats&gt;
		&lt;format&gt;jar&lt;/format&gt;
	&lt;/formats&gt;
	
	&lt;dependencySets&gt;
		&lt;dependencySet&gt;
			&lt;includes&gt;
				&lt;include&gt;${project.groupId}:${project.artifactId}&lt;/include&gt;
			&lt;/includes&gt;
		&lt;/dependencySet&gt;
		&lt;dependencySet&gt;
			&lt;scope&gt;runtime&lt;/scope&gt;
			&lt;outputDirectory&gt;lib/&lt;/outputDirectory&gt;
			&lt;excludes&gt;
				&lt;exclude&gt;${project.groupId}:${project.artifactId}&lt;/exclude&gt;
			&lt;/excludes&gt;
		&lt;/dependencySet&gt;
	&lt;/dependencySets&gt;
	
	&lt;/assembly&gt;
	 * </pre>
	 * 
	 * <h2>embedding pom</h2>
	 * 
	 * <pre>
	&lt;build&gt;
	&lt;plugins&gt;
	    &lt;plugin&gt;
	        &lt;artifactId&gt;maven-dependency-plugin&lt;/artifactId&gt;
	        &lt;executions&gt;
	            &lt;execution&gt;
	                &lt;id&gt;import-bundle&lt;/id&gt;
	                &lt;phase&gt;prepare-package&lt;/phase&gt;
	                &lt;goals&gt;
	                    &lt;goal&gt;copy&lt;/goal&gt;
	                &lt;/goals&gt;
	                &lt;configuration&gt;
	                    &lt;artifactItems&gt;
	                        &lt;artifactItem&gt;
	                            &lt;groupId&gt;${project.groupId}&lt;/groupId&gt;
	                            &lt;artifactId&gt;my-client&lt;/artifactId&gt;
	                            &lt;version&gt;${project.version}&lt;/version&gt;
	                            &lt;classifier&gt;bundle&lt;/classifier&gt;
	                        &lt;/artifactItem&gt;
	                    &lt;/artifactItems&gt;
	                    &lt;stripVersion&gt;true&lt;/stripVersion&gt;
	                    &lt;outputDirectory&gt;
	                        ${project.build.outputDirectory}/META-INF/client&lt;/outputDirectory&gt;
	                &lt;/configuration&gt;
	            &lt;/execution&gt;
	        &lt;/executions&gt;
	    &lt;/plugin&gt;
	 * </pre>
	 * 
	 * @param bundleResource File or Jar {@link URL} indicating location of the
	 *                       bundled EAR resource i.e., from
	 *                       {@link ClassLoader#getResource(String)}
	 * @return class/module path suitable for use with
	 *         {@link ModularClassLoader#ModularClassLoader(boolean, Iterable, ModuleLayer, ClassLoader, java.util.function.Consumer)}.
	 * 
	 * @see <a href=
	 *      "https://jakarta.ee/specifications/platform/10/jakarta-platform-spec-10.0#a2948">JEE
	 *      10 8.2.1</a>
	 */
	public static Iterable<Path> readBundle(URL bundleResource) {
		final Deque<Path> libs = new ArrayDeque<>();
		with(bundleResource, o -> {
			try (final var in = (o instanceof Path) ? Files.newInputStream((Path) o) : (InputStream) o;
					final var bundleJar = new JarInputStream(in)) {
				JarEntry entry;
				while ((entry = bundleJar.getNextJarEntry()) != null) {
					final var name = entry.getName();
					if (name.endsWith(".jar")) {
						final var lib = name.startsWith("lib/");
						final var bundledLib = TemporaryFile.init(path -> {
							try (final var out = Files.newOutputStream(path)) {
								IuStream.copy(bundleJar, out);
							}
							return path;
						});
						if (lib)
							libs.offer(bundledLib);
						else
							libs.offerFirst(bundledLib);
					}
				}
				bundleJar.closeEntry();
			}
			return null;
		});
		return libs;
	}

	private static <T> T with(URL url, UnsafeFunction<Object, T> then) {
		return IuException.unchecked(() -> {
			final var uri = url.toURI();
			final var scheme = uri.getScheme();

			if ("file".equals(scheme))
				return then.apply(Path.of(uri).toRealPath());

			if (!"jar".equals(scheme))
				throw new IllegalArgumentException();

			final var jarSpec = uri.getSchemeSpecificPart();
			final var bangSlash = jarSpec.indexOf("!/");
			if (bangSlash == -1)
				throw new IllegalArgumentException();

			final var jarUri = URI.create(jarSpec.substring(4, bangSlash));
			if (!"file".equals(jarUri.getScheme()))
				throw new IllegalArgumentException();

			final var entryName = jarSpec.substring(bangSlash + 2);
			try (final var jarFile = Files.newInputStream(Path.of(jarUri).toRealPath());
					final var jar = new JarInputStream(jarFile)) {
				JarEntry entry;
				while ((entry = jar.getNextJarEntry()) != null)
					if (entry.getName().equals(entryName))
						return then.apply(jar);
			}

			throw new IllegalArgumentException();
		});
	}

	private TemporaryFile() {
	}

}
