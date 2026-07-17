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
package edu.iu.client;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RuntimeException} backed by {@link RemoteInvocationFailure}
 */
public class RemoteInvocationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** Class name of remote exception type **/
	private final String exceptionType;

	/**
	 * Creates a new {@link Throwable} reconstructed from a remote invocation
	 * failure
	 * 
	 * @param failure remote invocation failure
	 */
	public RemoteInvocationException(RemoteInvocationFailure failure) {
		this(failure, new Throwable());
	}

	private RemoteInvocationException(RemoteInvocationFailure failure, Throwable from) {
		super(failure.getMessage());
		exceptionType = failure.getExceptionType();

		final var stackTrace = failure.getStackTrace();
		final List<StackTraceElement> remoteTrace = new ArrayList<>();
		remoteTrace.add(new StackTraceElement(exceptionType, "<init>", null, -1));
		
		if (stackTrace != null)
			for (RemoteInvocationDetail rte : stackTrace)
				remoteTrace.add(new StackTraceElement(rte.getClassName(), rte.getMethodName(), rte.getFileName(),
						rte.getLineNumber()));

		final var remoteName = failure.getRemoteName();
		final var remoteMethod = failure.getRemoteMethod();
		if (remoteName != null)
			remoteTrace.add(new StackTraceElement(remoteName, remoteMethod, null, -1));

		if (from != null)
			for (final var ste : from.getStackTrace())
				remoteTrace.add(ste);

		setStackTrace(remoteTrace.toArray(StackTraceElement[]::new));

		final var cause = failure.getCause();
		if (cause != null)
			initCause(new RemoteInvocationException(cause, null));

		final var suppressed = failure.getSuppressed();
		if (suppressed != null)
			for (RemoteInvocationFailure s : suppressed)
				addSuppressed(new RemoteInvocationException(s, null));
	}

	/**
	 * Gets the exception type.
	 * 
	 * @return exception type
	 */
	public String getExceptionType() {
		return exceptionType;
	}

}
