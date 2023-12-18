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

	public void setInstant(Instant instant) {
		this.instant = instant;
	}

	/**
	 * Return the log Level associated with this LogEvent.
	 */
	public Level getLevel() {
		return level;
	}

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

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	/**
	 * Return the RuntimeMode enum value associated with this LogEvent.
	 */
	public RuntimeMode getMode() {
		return mode;
	}

	public void setMode(RuntimeMode mode) {
		this.mode = mode;
	}

	/**
	 * Return the String representation of the module associated with this LogEvent.
	 */
	public String getModule() {
		return module;
	}

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

	public void setThread(String thread) {
		this.thread = thread;
	}
}
