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
package iu.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Writes entries to a {@link Component}'s dedicated temporary path files.
 */
class ComponentTarget implements AutoCloseable {

	private final OutputStream out;
	private final JarOutputStream jar;
	private final byte[] buf = new byte[16384];

	/**
	 * Creates a new target at a given temporary file location.
	 * 
	 * @param path temp file path
	 * @throws IOException If an I/O error occurs writing to the file
	 */
	ComponentTarget(Path path) throws IOException {
		out = Files.newOutputStream(path);
		jar = new JarOutputStream(out);
	}

	/**
	 * Adds an entry.
	 * 
	 * @param name entry name
	 * @param data input for reading raw data to copy to the entry
	 * @throws IOException If an I/O error occurs writing to the file
	 */
	void put(String name, InputStream data) throws IOException {
		int r;
		jar.putNextEntry(new JarEntry(name));
		while ((r = data.read(buf, 0, buf.length)) > 0)
			jar.write(buf, 0, r);
		jar.closeEntry();
	}

	@Override
	public void close() throws IOException {
		jar.close();
		out.close();
	}

}
