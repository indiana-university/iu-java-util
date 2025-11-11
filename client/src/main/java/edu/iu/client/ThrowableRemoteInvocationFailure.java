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
package edu.iu.client;

import edu.iu.IuIterable;
import edu.iu.IuObject;

/**
 * {@link RemoteInvocationFailure} implementation backed by {@link Throwable}
 */
public class ThrowableRemoteInvocationFailure implements RemoteInvocationFailure {

	private final String remoteName;
	private final String remoteMethod;
	private final Throwable throwable;

	/**
	 * Constructor.
	 * 
	 * @param remoteName   remote name property
	 * @param remoteMethod remote method property
	 * @param throwable    throwable
	 */
	public ThrowableRemoteInvocationFailure(String remoteName, String remoteMethod, Throwable throwable) {
		this.remoteName = remoteName;
		this.remoteMethod = remoteMethod;
		this.throwable = throwable;
	}

	private ThrowableRemoteInvocationFailure(Throwable throwable) {
		this(null, null, throwable);
	}

	@Override
	public String getRemoteName() {
		return remoteName;
	}

	@Override
	public String getRemoteMethod() {
		return remoteMethod;
	}

	@Override
	public String getMessage() {
		return throwable.getMessage();
	}

	@Override
	public String getExceptionType() {
		return throwable.getClass().getName();
	}

	@Override
	public Iterable<RemoteInvocationDetail> getStackTrace() {
		return IuIterable.map(IuIterable.iter(throwable.getStackTrace()), RemoteInvocationStackTraceElementDetail::new);
	}

	@Override
	public RemoteInvocationFailure getCause() {
		return IuObject.convert(throwable.getCause(), ThrowableRemoteInvocationFailure::new);
	}

	@Override
	public Iterable<RemoteInvocationFailure> getSuppressed() {
		return IuIterable.map(IuIterable.iter(throwable.getSuppressed()), ThrowableRemoteInvocationFailure::new);
	}

}
