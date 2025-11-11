package edu.iu.client;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RuntimeException} backed by {@link RemoteInvocationFailure}
 */
public class RemoteInvocationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

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
