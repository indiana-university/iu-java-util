/*
 * Copyright © 2024 Indiana University
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
/**
 * Provides unit testing support.
 * 
 * <p>
 * Supports the use of:
 * </p>
 * 
 * <ul>
 * <li>JUnit Juptier Engine</li>
 * <li>Mockito</li>
 * </ul>
 * 
 * @see edu.iu.test.IuTest
 * @provides org.junit.jupiter.api.extension.Extension Ties logging expectations in to test runs
 * @provides org.junit.platform.launcher.LauncherSessionListener Enables logging expectations
 */
module iu.util.test {
	exports edu.iu.test;

	requires iu.util;
	requires java.net.http;
	requires org.mockito;
	requires transitive org.junit.jupiter.api;
	requires transitive org.junit.platform.launcher;
	requires static jakarta.json;

	provides org.junit.platform.launcher.LauncherSessionListener with edu.iu.test.IuTestSessionListener;
	provides org.junit.jupiter.api.extension.Extension with edu.iu.test.IuTestExtension;
}