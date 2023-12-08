package iu.logging;

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
	 * Create a LogEvent from the given LogRecord, IuLoggingContext, and IuLoggingEnvironment.
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
		this.thrown = record.getThrown() != null ? record.getThrown().getMessage(): null;
		
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

	public Instant getInstant() {
		return instant;
	}

//	public void setInstant(Instant instant) {
//		this.instant = instant;
//	}

	public Level getLevel() {
		return level;
	}

//	public void setLevel(Level level) {
//		this.level = level;
//	}

	public String getLoggerName() {
		return loggerName;
	}

//	public void setLoggerName(String loggerName) {
//		this.loggerName = loggerName;
//	}

	public String getMessage() {
		return message;
	}

//	public void setMessage(String message) {
//		this.message = message;
//	}

	public String getSourceClassName() {
		return sourceClassName;
	}

//	public void setSourceClassName(String sourceClassName) {
//		this.sourceClassName = sourceClassName;
//	}

	public String getSourceMethodName() {
		return sourceMethodName;
	}

//	public void setSourceMethodName(String sourceMethodName) {
//		this.sourceMethodName = sourceMethodName;
//	}

	public String getThrown() {
		return thrown;
	}

//	public void setThrown(String thrown) {
//		this.thrown = thrown;
//	}

	public String getAuthenticatedPrincipal() {
		return authenticatedPrincipal;
	}

//	public void setAuthenticatedPrincipal(String authenticatedPrincipal) {
//		this.authenticatedPrincipal = authenticatedPrincipal;
//	}

	public String getCalledUrl() {
		return calledUrl;
	}

//	public void setCalledUrl(String calledUrl) {
//		this.calledUrl = calledUrl;
//	}

	public String getRemoteAddr() {
		return remoteAddr;
	}

//	public void setRemoteAddr(String remoteAddr) {
//		this.remoteAddr = remoteAddr;
//	}

	public String getReqNum() {
		return reqNum;
	}

//	public void setReqNum(String reqNum) {
//		this.reqNum = reqNum;
//	}

	public String getUserPrincipal() {
		return userPrincipal;
	}

//	public void setUserPrincipal(String userPrincipal) {
//		this.userPrincipal = userPrincipal;
//	}

	public String getApplication() {
		return application;
	}

//	public void setApplication(String application) {
//		this.application = application;
//	}

	public String getComponent() {
		return component;
	}

//	public void setComponent(String component) {
//		this.component = component;
//	}

	public String getEnvironment() {
		return environment;
	}

//	public void setEnvironment(String environment) {
//		this.environment = environment;
//	}

	public String getHostname() {
		return hostname;
	}

//	public void setHostname(String hostname) {
//		this.hostname = hostname;
//	}

	public RuntimeMode getMode() {
		return mode;
	}

//	public void setMode(RuntimeMode mode) {
//		this.mode = mode;
//	}

	public String getModule() {
		return module;
	}

//	public void setModule(String module) {
//		this.module = module;
//	}

	public String getNodeId() {
		return nodeId;
	}

//	public void setNodeId(String nodeId) {
//		this.nodeId = nodeId;
//	}

	public String getRuntime() {
		return runtime;
	}

//	public void setRuntime(String runtime) {
//		this.runtime = runtime;
//	}

	/**
	 * Return name of the current thread
	 * @return String representation of the current thread's name
	 */
	public String getThread() {
		return thread;
	}

//	public void setThread(String thread) {
//		this.thread = thread;
//	}
}
