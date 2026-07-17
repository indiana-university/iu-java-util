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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import edu.iu.IuStream;

/**
 * Scans a path entry: either a {@code jar} file or filesystem directory, for
 * non-folder resources.
 */
class PathEntryScanner {

	/**
	 * Reads a single path entry.
	 * 
	 * <p>
	 * This method is not efficient for multiple reads.
	 * </p>
	 * 
	 * @param pathEntry    path entry: jar file or filesystem directory
	 * @param resourceName resource name
	 * @return full binary contents of the resource
	 * @throws IOException         if an error occurs while reading the file
	 * @throws NoSuchFileException if the resourceName doesn't match an entry in the
	 *                             jar file, or doesn't name a filesystem resource
	 *                             relative to {@code pathEntry}.
	 */
	static byte[] read(Path pathEntry, String resourceName) throws IOException {

		// TODO: evaluate this guard condition; either flesh out boundaries or remove
		// as unneeded for an internal tool. At present, only two values for
		// resourceName are possible: META-INF/iu-type.properties and
		// META-INF/iu.properties.
		// Both of these may be removed in the course of completing JEE.

		// https://github.com/indiana-university/iu-java-util/pull/50:
		// @anderjak:I'm a little confused by this. the check seems to be looking for
		// both
		// indicators of relative paths and for an absolute path. It looks like it is
		// linux/unix-specific, but if it is looking for relative paths, should it look
		// for '~' as the first character too? Then the exception says the "resourceName
		// must be relative". At first I thought maybe it should say "resourceName must
		// not be relative", but the check for a leading '/' made me wonder whether the
		// message was correct and the check was wrong or vice versa or if I'm missing
		// something.

		final var first = resourceName.charAt(0);
		if (first == '.' || first == '/' || resourceName.endsWith("/..") || resourceName.indexOf("/../") != -1)
			throw new IllegalArgumentException("resourceName must be relative");

		if (Files.isDirectory(pathEntry))
			try (final var in = Files.newInputStream(pathEntry.resolve(resourceName))) {
				return IuStream.read(in);
			}
		else
			try (final var in = Files.newInputStream(pathEntry); //
					final var jar = new JarInputStream(in)) {
				JarEntry entry;
				while ((entry = jar.getNextJarEntry()) != null)
					if (entry.getName().equals(resourceName))
						return IuStream.read(jar);
			}

		throw new NoSuchFileException(resourceName + " not found at " + pathEntry);
	}

	/**
	 * Scans a path entry.
	 * 
	 * @param pathEntry path entry: jar file or filesystem directory, to scan
	 * @return mutable set initialized with all discovered resource names; may be
	 *         modified after return
	 * @throws IOException if an I/O error occurs scanning the path entry
	 */
	static Set<String> findResources(Path pathEntry) throws IOException {
		final Set<String> allResources;
		if (Files.isDirectory(pathEntry))
			allResources = findResourcesInFolder(pathEntry);
		else
			allResources = findResourcesInJar(pathEntry);
		return allResources;
	}

	private static Set<String> findResourcesInJar(Path pathEntry) throws IOException {
		final Set<String> resourceNames = new LinkedHashSet<>();
		try (final var in = Files.newInputStream(pathEntry); final var jar = new JarInputStream(in)) {
			JarEntry entry;
			while ((entry = jar.getNextJarEntry()) != null) {
				final var name = entry.getName();
				if (name.charAt(name.length() - 1) != '/')
					resourceNames.add(name);
			}
		}
		return resourceNames;
	}

	private static Set<String> findResourcesInFolder(Path pathEntry) throws IOException {
		final int rootLength = pathEntry.toUri().toString().length();
		final Set<String> resourceNames = new LinkedHashSet<>();
		final Queue<Path> toScan = new ArrayDeque<>();
		toScan.offer(pathEntry);
		while (!toScan.isEmpty()) {
			final var next = toScan.poll();
			if (Files.isDirectory(next))
				Files.list(next).forEach(toScan::offer);
			else
				resourceNames.add(next.toUri().toString().substring(rootLength));
		}
		return resourceNames;
	}

	private PathEntryScanner() {
	}

}
