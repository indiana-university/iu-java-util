package iu.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import edu.iu.logging.IuLoggingContext;
import edu.iu.logging.IuLoggingEnvironment;

/**
 * Implementation of IuLogEvent interface.
 */
public class LogEvent implements IuLogEvent {

	private static final Formatter MESSAGE_FORMATTER = new Formatter() {
		@Override
		public String format(LogRecord record) {
			return formatMessage(record);
		}
	};

	// Values from LogRecord
	private Instant instant;
	private Level level;
	private String loggerName;
	private String message;
	private String sourceClassName;
	private String sourceMethodName;
	private String thrown;

	// Values from IuLoggingContext
	private String authenticatedPrincipal;
	private String calledUrl;
	private String remoteAddr;
	private String reqNum;
	private String userPrincipal;

	// Values from IuLoggingEnvironment
	private String application;
	private String component;
	private String environment;
	private String hostname;
	private RuntimeMode mode;
	private String module;
	private String nodeId;
	private String runtime;

	private String thread = Thread.currentThread().getName();

	/**
	 * Default constructor.
	 */
	public LogEvent() {

	}

	/**
	 * Create a LogEvent from the given LogRecord, IuLoggingContext, and
	 * IuLoggingEnvironment.
	 * 
	 * @param record
	 * @param context
	 * @param environment
	 */
	public LogEvent(LogRecord record, IuLoggingContext context, IuLoggingEnvironment environment) {
		this.instant = record.getInstant();
		this.level = record.getLevel();
		this.loggerName = record.getLoggerName();
		this.message = MESSAGE_FORMATTER.format(record);
		this.sourceClassName = record.getSourceClassName();
		this.sourceMethodName = record.getSourceMethodName();
		Throwable thrown = record.getThrown();
		if (thrown != null) {
			StringWriter w = new StringWriter();
			thrown.printStackTrace(new PrintWriter(w));
			this.thrown = w.toString();
		} else
			this.thrown = null;

		this.authenticatedPrincipal = context.getAuthenticatedPrincipal();
		this.calledUrl = context.getCalledUrl();
		this.remoteAddr = context.getRemoteAddr();
		this.reqNum = context.getReqNum();
		this.userPrincipal = context.getUserPrincipal();

		this.application = environment.getApplication();
		this.component = environment.getComponent();
		this.environment = environment.getEnvironment();
		this.hostname = environment.getHostname();
		this.mode = environment.getMode();
		this.module = environment.getModule();
		this.nodeId = environment.getNodeId();
		this.runtime = environment.getRuntime();
	}

	/**
	 * Return the Instant associated with this LogEvent.
	 */
	public Instant getInstant() {
		return instant;
	}

	/**
	 * Set the Instant associated with this LogEvent.
	 * 
	 * @param instant
	 */
	public void setInstant(Instant instant) {
		this.instant = instant;
	}

	/**
	 * Return the log Level associated with this LogEvent.
	 */
	public Level getLevel() {
		return level;
	}

	/**
	 * Set the Level associated with this LogEvent.
	 * 
	 * @param level
	 */
	public void setLevel(Level level) {
		this.level = level;
	}

	/**
	 * Return the String representation of the Logger name associated with this
	 * LogEvent.
	 */
	public String getLoggerName() {
		return loggerName;
	}

	/**
	 * Set the Logger name associated with this LogEvent.
	 * 
	 * @param loggerName
	 */
	public void setLoggerName(String loggerName) {
		this.loggerName = loggerName;
	}

	/**
	 * Return the String representation of the message associated with this
	 * LogEvent.
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Set the message associated with this LogEvent.
	 * 
	 * @param message
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	/**
	 * Return the String representation of the source class name associated with
	 * this LogEvent.
	 */
	public String getSourceClassName() {
		return sourceClassName;
	}

	/**
	 * Set the source class name associated with this LogEvent.
	 * 
	 * @param sourceClassName
	 */
	public void setSourceClassName(String sourceClassName) {
		this.sourceClassName = sourceClassName;
	}

	/**
	 * Return the String representation of the source method name associated with
	 * this LogEvent.
	 */
	public String getSourceMethodName() {
		return sourceMethodName;
	}

	/**
	 * Set the source method name associated with this LogEvent.
	 * 
	 * @param sourceMethodName
	 */
	public void setSourceMethodName(String sourceMethodName) {
		this.sourceMethodName = sourceMethodName;
	}

	/**
	 * Return the String representation of the thrown error associated with this
	 * LogEvent.
	 */
	public String getThrown() {
		return thrown;
	}

	/**
	 * Set the thrown value associated with this LogEvent. The thrown value will be
	 * the stack trace as a string.
	 * 
	 * @param thrown
	 */
	public void setThrown(String thrown) {
		this.thrown = thrown;
	}

	/**
	 * Return the String representation of the authenticated principal associated
	 * with this LogEvent.
	 */
	public String getAuthenticatedPrincipal() {
		return authenticatedPrincipal;
	}

	/**
	 * Set the String representation of the authenticated principal associated with
	 * this LogEvent.
	 * 
	 * @param authenticatedPrincipal
	 */
	public void setAuthenticatedPrincipal(String authenticatedPrincipal) {
		this.authenticatedPrincipal = authenticatedPrincipal;
	}

	/**
	 * Return the String representation of the called URL associated with this
	 * LogEvent.
	 */
	public String getCalledUrl() {
		return calledUrl;
	}

	/**
	 * Set the called URL associated with this LogEvent.
	 * 
	 * @param calledUrl
	 */
	public void setCalledUrl(String calledUrl) {
		this.calledUrl = calledUrl;
	}

	/**
	 * Return the String representation of the remote address associated with this
	 * LogEvent.
	 */
	public String getRemoteAddr() {
		return remoteAddr;
	}

	/**
	 * Set the remote address associated with this LogEvent.
	 * 
	 * @param remoteAddr
	 */
	public void setRemoteAddr(String remoteAddr) {
		this.remoteAddr = remoteAddr;
	}

	/**
	 * Return the String representation of the request number associated with this
	 * LogEvent.
	 */
	public String getReqNum() {
		return reqNum;
	}

	/**
	 * Set the request number associated with this LogEvent.
	 * 
	 * @param reqNum
	 */
	public void setReqNum(String reqNum) {
		this.reqNum = reqNum;
	}

	/**
	 * Return the String representation of the user principal associated with this
	 * LogEvent.
	 */
	public String getUserPrincipal() {
		return userPrincipal;
	}

	/**
	 * Set the user principal associated with this LogEvent.
	 * 
	 * @param userPrincipal
	 */
	public void setUserPrincipal(String userPrincipal) {
		this.userPrincipal = userPrincipal;
	}

	/**
	 * Return the String representation of the application associated with this
	 * LogEvent.
	 */
	public String getApplication() {
		return application;
	}

	/**
	 * Set the application associated with this LogEvent.
	 * 
	 * @param application
	 */
	public void setApplication(String application) {
		this.application = application;
	}

	/**
	 * Return the String representation of the component associated with this
	 * LogEvent.
	 */
	public String getComponent() {
		return component;
	}

	/**
	 * Set the component associated with this LogEvent.
	 * 
	 * @param component
	 */
	public void setComponent(String component) {
		this.component = component;
	}

	/**
	 * Return the String representation of the environment associated with this
	 * LogEvent.
	 */
	public String getEnvironment() {
		return environment;
	}

	/**
	 * Set the environment associated with this LogEvent.
	 * 
	 * @param environment
	 */
	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	/**
	 * Return the String representation of the host name associated with this
	 * LogEvent.
	 */
	public String getHostname() {
		return hostname;
	}

	/**
	 * Set the hostname associated with this LogEvent.
	 * 
	 * @param hostname
	 */
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	/**
	 * Return the RuntimeMode enum value associated with this LogEvent.
	 */
	public RuntimeMode getMode() {
		return mode;
	}

	/**
	 * Set the RuntimeMode associated with this LogEvent.
	 * 
	 * @param mode
	 */
	public void setMode(RuntimeMode mode) {
		this.mode = mode;
	}

	/**
	 * Return the String representation of the module associated with this LogEvent.
	 */
	public String getModule() {
		return module;
	}

	/**
	 * Set the module associated with this LogEvent.
	 * 
	 * @param module
	 */
	public void setModule(String module) {
		this.module = module;
	}

	/**
	 * Return the String representation of the node id associated with this
	 * LogEvent.
	 */
	public String getNodeId() {
		return nodeId;
	}

	/**
	 * Set the node Id associated with this LogEvent.
	 * 
	 * @param nodeId
	 */
	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	/**
	 * Return the String representation of the runtime associated with this
	 * LogEvent.
	 */
	public String getRuntime() {
		return runtime;
	}

	/**
	 * Set the runtime associated with this LogEvent.
	 * 
	 * @param runtime
	 */
	public void setRuntime(String runtime) {
		this.runtime = runtime;
	}

	/**
	 * Return name of the current thread
	 * 
	 * @return String representation of the current thread's name
	 */
	public String getThread() {
		return thread;
	}

	/**
	 * Set the thread associated with this LogEvent.
	 * 
	 * @param thread
	 */
	public void setThread(String thread) {
		this.thread = thread;
	}
}
