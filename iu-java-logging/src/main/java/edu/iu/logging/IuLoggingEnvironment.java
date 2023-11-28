/*
 * Copyright © 2023 Indiana University
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
package edu.iu.logging;

/**
 * Logging Environment Interface
 */
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
