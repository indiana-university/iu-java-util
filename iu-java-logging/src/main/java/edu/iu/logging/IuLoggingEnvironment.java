package edu.iu.logging;

// IuLoggingEnvironment
public interface IuLoggingEnvironment {

	public static enum RuntimeMode { DEVELOPMENT, TEST, PRODUCTION }

	default String getApplication() {
		return null;
	}

	default String getComponent() {
		return null;
	}

	default String getEndpoint() {
		return null;
	}

	default String getEnvironment() {
		return null;
	}

	default String getHostname() {
		return null;
	}

	default RuntimeMode getMode() {
		return null;
	}

	default String getModule() {
		return null;
	}

	default String getNodeId() {
		return null;
	}
//
//	default String getVersion() {
//		return null;
//	}
//
//	default String getAlertFrom() {
//		return null;
//	}
//
//	default String getDeveloperEmail() {
//		return null;
//	}
//
//	default String getOpsEmail() {
//		return null;
//	}
//
//	default String getAlertSmtp() {
//		return null;
//	}
//
//	default String getContactDeveloper() {
//		return null;
//	}
//
//	default Level getLogLevel() {
//		return Level.INFO;
//	}
//
//	default Level getLogLevel(String loggerName) {
//		return Level.INFO;
//	}
//
//	default Level getConsoleLogLevel() {
//		return Level.WARNING;
//	}
//
//	default long getSevereInterval() {
//		return TimeUnit.MINUTES.toMillis(15L);
//	}
//
//	default long getWarningInterval() {
//		return TimeUnit.MINUTES.toMillis(30L);
//	}
//
//	default long getInfoInterval() {
//		return TimeUnit.HOURS.toMillis(8L);
//	}
//	
//	default int getLogEventBufferSize() {
//		return 0x100000;
//	}

}
