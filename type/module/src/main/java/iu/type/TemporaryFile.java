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
import java.nio.file.Files;
import java.nio.file.Path;

import edu.iu.IuException;
import edu.iu.UnsafeFunction;

/**
 * Initializes a temporary file with fail-safe delete when initialization fails.
 */
final class TemporaryFile {

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
	static <T> T init(UnsafeFunction<Path, T> tempFileInitializer) throws IOException {
		Path temp = Files.createTempFile("iu-type-", ".jar");

		try {
			return tempFileInitializer.apply(temp);
		} catch (Throwable e) {
			try {
				Files.deleteIfExists(temp);
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw IuException.checked(e, IOException.class);
		}
	}

	private TemporaryFile() {
	}

}
