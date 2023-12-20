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
package edu.iu.type.base;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

import edu.iu.IuException;
import edu.iu.UnsafeFunction;
import edu.iu.UnsafeRunnable;

/**
 * Initializes a temporary file with fail-safe delete when initialization fails.
 */
public final class TemporaryFile {

	private static final ThreadLocal<Queue<Path>> PENDING = new ThreadLocal<>();

	/**
	 * Refines {@link UnsafeRunnable}, restricts checked exceptions to only allow
	 * {@link IOException}.
	 */
	@FunctionalInterface
	public interface IORunnable extends UnsafeRunnable {
		@Override
		void run() throws IOException;
	}

	/**
	 * Refines {@link UnsafeFunction}, restricts input to {@link Path} and checked
	 * exceptions to only allow {@link IOException}.
	 * 
	 * @param <T> result type
	 */
	@FunctionalInterface
	public interface PathFunction<T> extends UnsafeFunction<Path, T> {
		@Override
		T apply(Path path) throws IOException;
	}

	/**
	 * Tracks temp files created by an initialization task, and either deletes all
	 * created if an error is thrown, or returns a thunk that may be used to deletes
	 * all files on teardown.
	 * 
	 * @param task initialization task
	 * @return destroy thunk
	 * @throws IOException from {@link IORunnable}
	 */
	public static IORunnable init(IORunnable task) throws IOException {
		final Queue<Path> tempFilesToRestore = PENDING.get();
		final Queue<Path> tempFiles = new ArrayDeque<>();
		final IORunnable teardown = () -> {
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
	public static <T> T init(PathFunction<T> tempFileInitializer) throws IOException {
		class Box {
			T initialized;
		}
		final var box = new Box();

		IORunnable init = () -> {
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

	private TemporaryFile() {
	}

}
