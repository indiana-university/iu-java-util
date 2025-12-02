/*
 * Copyright © 2025 Indiana University
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
package iu.logging.boot;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.LogManager;

import edu.iu.UnsafeRunnable;

/**
 * May be used to restrict access to configuration methods to
 * {@link IuLoggingBootstrap} and its implementation module.
 * 
 * <p>
 * Use {@code -Djava.util.logging.manager=iu.logging.boot.IuLogManager} to
 * enable.
 * </p>
 */
public class IuLogManager extends LogManager {

	private static final ThreadLocal<Boolean> BOUNDED = new ThreadLocal<>();

	/**
	 * Default constructor.
	 */
	public IuLogManager() {
	}

	private static boolean isBound() {
		return Thread.currentThread().getClass().getEnclosingClass() == LogManager.class
				|| Boolean.TRUE.equals(BOUNDED.get());
	}

	/**
	 * Binds a configuration task to the current thread, allowing temporary access
	 * to use logging configuration methods.
	 * 
	 * @param task task to run with configuration methods enabled.
	 * @throws Throwable From {@link UnsafeRunnable#run()}
	 */
	static void bound(UnsafeRunnable task) throws Throwable {
		if (!isBound())
			try {
				BOUNDED.set(true);
				task.run();
			} finally {
				BOUNDED.remove();
			}
		else
			task.run();
	}

	/**
	 * Generates an error indicating that log configuration is read-only.
	 * 
	 * @return {@link UnsupportedOperationException}
	 */
	static UnsupportedOperationException readonly() {
		return new UnsupportedOperationException("Logging configuration is read-only");
	}

	/**
	 * Checks if the current thread is bound to a configuration task.
	 */
	static void checkReadonly() {
		if (isBound())
			return;

		final var error = readonly();
		for (final var element : error.getStackTrace())
			if (LogManager.class.getName().equals(element.getClassName()) //
					&& "readPrimordialConfiguration".equals(element.getMethodName()))
				return;

		error.printStackTrace();
		throw error;
	}

	@Override
	public void readConfiguration() throws IOException, SecurityException {
		checkReadonly();
		super.readConfiguration();
	}

	@Override
	public void reset() throws SecurityException {
		checkReadonly();
		super.reset();
	}

	@Override
	public void readConfiguration(InputStream ins) throws IOException, SecurityException {
		checkReadonly();
		super.readConfiguration(ins);
	}

	@Override
	public void updateConfiguration(Function<String, BiFunction<String, String, String>> mapper) throws IOException {
		checkReadonly();
		super.updateConfiguration(mapper);
	}

	@Override
	public void updateConfiguration(InputStream ins, Function<String, BiFunction<String, String, String>> mapper)
			throws IOException {
		checkReadonly();
		super.updateConfiguration(ins, mapper);
	}

	@Override
	public LogManager addConfigurationListener(Runnable listener) {
		checkReadonly();
		return super.addConfigurationListener(listener);
	}

	@Override
	public void removeConfigurationListener(Runnable listener) {
		checkReadonly();
		super.removeConfigurationListener(listener);
	}

}
