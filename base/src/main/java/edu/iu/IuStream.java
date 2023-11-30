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
package edu.iu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Efficient stream utilities.
 */
public final class IuStream {

	private static class Buffers {
		private final byte[] binary = new byte[16384];
		private final char[] text = new char[16384];
	}

	private static final ThreadLocal<Buffers> BUFFERS = new ThreadLocal<Buffers>() {
		@Override
		protected Buffers initialValue() {
			return new Buffers();
		}
	};

	/**
	 * Reads all data from an {@link InputStream} and returns as a {@code byte[]}.
	 * 
	 * @param in {@link InputStream}
	 * @return {@code byte[]} containing all data
	 * @throws IOException If a read error occurs
	 */
	public static byte[] read(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		copy(in, baos);
		return baos.toByteArray();
	}

	/**
	 * Reads all text from a {@link Reader} and returns as a {@link String}.
	 * 
	 * @param in Reader
	 * @return String
	 * @throws IOException If a read error occurs
	 */
	public static String read(Reader in) throws IOException {
		StringWriter sw = new StringWriter();
		copy(in, sw);
		return sw.toString();
	}

	/**
	 * Copies all data from an {@link InputStream} to an {@link OutputStream}.
	 * 
	 * @param in  InputStream
	 * @param out OutputStream
	 * @throws IOException If an error occurs on either stream
	 */
	public static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buf = BUFFERS.get().binary;
		int r;
		while ((r = in.read(buf)) > 0)
			out.write(buf, 0, r);
		out.flush();
	}

	/**
	 * Copies all characters from a {@link Reader} to a {@link Writer}.
	 * 
	 * @param in  Reader
	 * @param out Writer
	 * @throws IOException If an error occurs on either stream
	 */
	public static void copy(Reader in, Writer out) throws IOException {
		char[] buf = BUFFERS.get().text;
		int r;
		while ((r = in.read(buf)) > 0)
			out.write(buf, 0, r);
		out.flush();
	}

	private IuStream() {
	}

}
