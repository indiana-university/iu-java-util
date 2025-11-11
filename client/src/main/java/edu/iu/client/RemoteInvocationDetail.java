package edu.iu.client;

/**
 * Remote invocation stack trace element details.
 */
public interface RemoteInvocationDetail {

	/**
	 * Gets a stack trace element from {@link RemoteInvocationDetail}
	 * 
	 * @param detail {@link RemoteInvocationDetail}
	 * @return {@link StackTraceElement}
	 */
	public static StackTraceElement from(RemoteInvocationDetail detail) {
		return new StackTraceElement(detail.getClassName(), detail.getMethodName(), detail.getFileName(),
				detail.getLineNumber());
	}

	/**
	 * Gets the class name
	 * 
	 * @return class name
	 */
	String getClassName();

	/**
	 * Gets the method name
	 * 
	 * @return method name
	 */
	String getMethodName();

	/**
	 * Gets the file name
	 * 
	 * @return file name
	 */
	String getFileName();

	/**
	 * Gets the line number
	 * 
	 * @return line number
	 */
	int getLineNumber();

}
