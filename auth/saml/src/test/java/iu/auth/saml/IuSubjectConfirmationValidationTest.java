/*
 * Copyright © 2026 Indiana University
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
package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensaml.saml.common.assertion.AssertionValidationException;
import org.opensaml.saml.common.assertion.ValidationContext;
import org.opensaml.saml.common.assertion.ValidationResult;
import org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;

import edu.iu.IuException;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class IuSubjectConfirmationValidationTest {

	public static final String SUBJECT_CONFIRMATION_LOCAL_ADDRESS = "10.1.2.3";
	public static final String SUBJECT_CONFIRMATION_ADDRESS = "128.0.0.0";

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("net.shibboleth", Level.FINE);
		IuTestLogger.allow("org.apache.xml", Level.FINE);
		IuTestLogger.allow("org.opensaml", Level.FINE);
	}

	@Test
	public void testLocalAddressValidation() throws AssertionValidationException {

		InetAddress address = IuException.unchecked(() -> InetAddress.getByName(SUBJECT_CONFIRMATION_LOCAL_ADDRESS));
		Map<String, Object> staticParams = new HashMap<>();
		staticParams.put(SAML2AssertionValidationParameters.SC_VALID_ADDRESSES, address);
		IuSubjectConfirmationValidator validator = new IuSubjectConfirmationValidator(
				Arrays.asList(SUBJECT_CONFIRMATION_LOCAL_ADDRESS), false);

		final var subjectConfirmationData = mock(SubjectConfirmationData.class);
		when(subjectConfirmationData.getAddress()).thenReturn(SUBJECT_CONFIRMATION_LOCAL_ADDRESS);

		final var assertion = mock(Assertion.class);

		final var validationContext = mock(ValidationContext.class);
		when(validationContext.getStaticParameters()).thenReturn(staticParams);
		IuTestLogger.expect(IuSubjectConfirmationValidator.class.getName(), Level.FINE,
				"Allowing private IP /10.1.2.3");
		ValidationResult result = validator.validateAddress(subjectConfirmationData, assertion, validationContext,
				false);
		assertEquals(ValidationResult.VALID, result);
	}

	@Test
	public void testDisableAddressCheck() throws AssertionValidationException {

		InetAddress address = IuException.unchecked(() -> InetAddress.getByName(SUBJECT_CONFIRMATION_LOCAL_ADDRESS));
		Map<String, Object> staticParams = new HashMap<>();
		staticParams.put(SAML2AssertionValidationParameters.SC_VALID_ADDRESSES, address);
		staticParams.put(SAML2AssertionValidationParameters.SC_CHECK_ADDRESS, Boolean.FALSE);
		IuSubjectConfirmationValidator validator = new IuSubjectConfirmationValidator(
				Arrays.asList(SUBJECT_CONFIRMATION_LOCAL_ADDRESS), false);

		final var subjectConfirmationData = mock(SubjectConfirmationData.class);
		when(subjectConfirmationData.getAddress()).thenReturn(SUBJECT_CONFIRMATION_LOCAL_ADDRESS);

		final var assertion = mock(Assertion.class);

		final var validationContext = mock(ValidationContext.class);
		when(validationContext.getStaticParameters()).thenReturn(staticParams);
		IuTestLogger.expect(IuSubjectConfirmationValidator.class.getName(), Level.FINE,
				"SubjectConfirmationData/@Address check is disabled, skipping");
		ValidationResult result = validator.validateAddress(subjectConfirmationData, assertion, validationContext,
				false);
		assertEquals(ValidationResult.VALID, result);

	}

	@Test
	public void testSubjectConfirmationDataAddressValidation() throws AssertionValidationException {
		String range = "150.50.0.0/16,127.0.0.0/8";

		List<String> allowedRange = Arrays.asList(range.split(","));

		IuSubjectConfirmationValidator validator = new IuSubjectConfirmationValidator(allowedRange, false);

		InetAddress address = IuException.unchecked(() -> InetAddress.getByName(SUBJECT_CONFIRMATION_LOCAL_ADDRESS));
		Map<String, Object> staticParams = new HashMap<>();
		staticParams.put(SAML2AssertionValidationParameters.SC_VALID_ADDRESSES, address);

		// empty address
		final var subjectConfirmationData = mock(SubjectConfirmationData.class);
		when(subjectConfirmationData.getAddress()).thenReturn("");

		final var assertion = mock(Assertion.class);

		final var validationContext = mock(ValidationContext.class);
		when(validationContext.getStaticParameters()).thenReturn(staticParams);

		ValidationResult result = validator.validateAddress(subjectConfirmationData, assertion, validationContext,
				false);
		assertEquals(ValidationResult.VALID, result);

		// address require
		result = validator.validateAddress(subjectConfirmationData, assertion, validationContext, true);
		assertEquals(ValidationResult.INVALID, result);

		// non null invalid address
		final var subjectConfirmationDataInvalidAddress = mock(SubjectConfirmationData.class);
		when(subjectConfirmationDataInvalidAddress.getAddress()).thenReturn("invalid");

		result = validator.validateAddress(subjectConfirmationDataInvalidAddress, assertion, validationContext, false);
		assertEquals(ValidationResult.INDETERMINATE, result);

		// valid Local Address
		final var subjectConfirmationDataValid = mock(SubjectConfirmationData.class);
		when(subjectConfirmationDataValid.getAddress()).thenReturn(SUBJECT_CONFIRMATION_LOCAL_ADDRESS);

		IuTestLogger.expect(IuSubjectConfirmationValidator.class.getName(), Level.FINE,
				"Allowing private IP /10.1.2.3");
		result = validator.validateAddress(subjectConfirmationDataValid, assertion, validationContext, false);
		assertEquals(ValidationResult.VALID, result);

		// valid non local Address
		final var nonLocalAddress = mock(SubjectConfirmationData.class);
		when(nonLocalAddress.getAddress()).thenReturn("127.0.0.0");

		IuTestLogger.expect(IuSubjectConfirmationValidator.class.getName(), Level.FINE,
				"Allowing IP /127.0.0.0; range = 127.0.0.0/8");
		result = validator.validateAddress(nonLocalAddress, assertion, validationContext, false);
		assertEquals(ValidationResult.VALID, result);

		// valid non local Address - not in allowed list
		final var nonAllowedValidIpAddress = mock(SubjectConfirmationData.class);
		when(nonAllowedValidIpAddress.getAddress()).thenReturn("157.0.0.9");

		IuTestLogger.expect(IuSubjectConfirmationValidator.class.getName(), Level.INFO,
				"IP address mismatch in SAML subject confirmation; remote address = 157.0.0.9; allowed ranges = [150.50.0.0/16, 127.0.0.0/8]");
		result = validator.validateAddress(nonAllowedValidIpAddress, assertion, validationContext, false);
		assertEquals(ValidationResult.VALID, result);

		// allowed list is null and failOnAddressMismatch is false
		validator = new IuSubjectConfirmationValidator(null, false);
		IuTestLogger.expect(IuSubjectConfirmationValidator.class.getName(), Level.INFO,
				"IP address mismatch in SAML subject confirmation; remote address = 157.0.0.9; allowed ranges = null");
		result = validator.validateAddress(nonAllowedValidIpAddress, assertion, validationContext, false);
		assertEquals(ValidationResult.VALID, result);

		// allowed list is null and failOnAddressMismatch is true
		validator = new IuSubjectConfirmationValidator(null, true);
		IuTestLogger.expect(IuSubjectConfirmationValidator.class.getName(), Level.INFO,
				"IP address mismatch in SAML subject confirmation; remote address = 157.0.0.9; allowed ranges = null");
		result = validator.validateAddress(nonAllowedValidIpAddress, assertion, validationContext, false);
		assertEquals(ValidationResult.INDETERMINATE, result);

	}

}
