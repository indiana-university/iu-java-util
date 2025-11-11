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
