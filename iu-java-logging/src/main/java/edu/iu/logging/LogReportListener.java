package edu.iu.logging;

import java.util.logging.Level;

public interface LogReportListener {

	void publishLogReport(Level level, String summary, String report);

	void startMonitoring(String hint, boolean soft);

	void endMonitoring();

	void endMonitoringInError(Throwable cause);

}
