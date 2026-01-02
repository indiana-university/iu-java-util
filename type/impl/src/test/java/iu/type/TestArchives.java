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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

@SuppressWarnings("javadoc")
public class TestArchives extends IuTypeTestCase {

	public static final byte[] EMPTY_JAR;

	static {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (JarOutputStream jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry("META-INF/"));
			jar.closeEntry();

			jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
			var manifest = new Manifest();
			var mainAttributes = manifest.getMainAttributes();
			mainAttributes.put(Name.MANIFEST_VERSION, "1.0");
			manifest.write(jar);
			jar.closeEntry();
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
		EMPTY_JAR = out.toByteArray();
	}

	public static InputStream getComponentArchive(String componentName) throws IOException {
		return Files.newInputStream(Path.of("target/dependency/iu-java-type-" + componentName
				+ (componentName.equals("testweb") ? ".war" : ".jar")));
	}

	public static InputStream[] getProvidedDependencyArchives(String componentName) throws IOException {
		Queue<InputStream> providedDependencyArchives = new ArrayDeque<>();
		for (var jar : Files.newDirectoryStream(Path.of("target/dependency/iu-java-type-" + componentName + "-deps")))
			if (jar.toString().endsWith(".jar"))
				providedDependencyArchives.offer(Files.newInputStream(jar));
		return providedDependencyArchives.toArray(new InputStream[providedDependencyArchives.size()]);
	}

	public static URL[] getClassPath(String componentName) throws IOException {
		Queue<URL> path = new ArrayDeque<>();
		path.offer(Path.of(
				"target/dependency/iu-java-type-" + componentName + (componentName.equals("testweb") ? ".war" : ".jar"))
				.toUri().toURL());
		final var deps = Path.of("target/dependency/iu-java-type-" + componentName + "-deps");
		if (Files.exists(deps))
			for (var jar : Files.newDirectoryStream(deps))
				if (jar.toString().endsWith(".jar"))
					path.offer(jar.toUri().toURL());
		return path.toArray(path.toArray(new URL[path.size()]));
	}

	public static Path[] getModulePath(String componentName) throws IOException {
		Queue<Path> path = new ArrayDeque<>();
		try (var in = getComponentArchive(componentName); var source = new ArchiveSource(in)) {
			path.add(ComponentArchive.from(source).path());
		}
		for (var in : getProvidedDependencyArchives(componentName)) {
			try (var source = new ArchiveSource(in)) {
				path.add(ComponentArchive.from(source).path());
			}
			in.close();
		}
		return path.toArray(path.toArray(new Path[path.size()]));
	}

}
