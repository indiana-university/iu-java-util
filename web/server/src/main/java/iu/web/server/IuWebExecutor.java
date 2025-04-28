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
package iu.web.server;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuException;

class IuWebExecutor extends ThreadPoolExecutor {

	private static final Logger LOG = Logger.getLogger(IuWebExecutor.class.getName());

	private static volatile int instanceCount;

	private static class Factory implements ThreadFactory {
		private final int serial = ++instanceCount;
		private volatile int num;

		private final ThreadGroup threadGroup = new ThreadGroup("iu-java-web-server");

		@Override
		public Thread newThread(Runnable r) {
			synchronized (this) {
				num = ++num;
			}
			return new Thread(threadGroup, r, "iu-java-web-server-" + serial + "/" + num);
		}
	}

	IuWebExecutor(int threads, Duration timeout) {
		super( //
				Integer.max(threads / 10, 2), // spawn 10% of max threads before queueing
				Integer.max(threads, 2), // limit total threads at 100%
				timeout.toNanos(), TimeUnit.NANOSECONDS, //
				new ArrayBlockingQueue<>( //
						Integer.max(threads / 2, 10) //
				), new Factory());
	}

	@Override
	public void execute(Runnable command) {
		try {
			super.execute(command);
		} catch (Throwable e) {
			LOG.log(Level.SEVERE, e, () -> "executor submit failure " + command);
			throw IuException.unchecked(e);
		}
	}
}
