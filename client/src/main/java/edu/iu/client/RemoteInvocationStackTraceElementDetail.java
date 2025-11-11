package edu.iu.client;

/**
 * Provides remote invocation failure details fro a {@link StackTraceElement}
 */
public class RemoteInvocationStackTraceElementDetail implements RemoteInvocationDetail {

	private StackTraceElement element;

	/**
	 * Constructor
	 * 
	 * @param element {@link StackTraceElement}
	 */
	public RemoteInvocationStackTraceElementDetail(StackTraceElement element) {
		this.element = element;
	}

	@Override
	public String getClassName() {
		return element.getClassName();
	}

	@Override
	public String getMethodName() {
		return element.getMethodName();
	}

	@Override
	public String getFileName() {
		return element.getFileName();
	}

	@Override
	public int getLineNumber() {
		return element.getLineNumber();
	}

}
