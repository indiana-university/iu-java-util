package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.opensaml.saml.common.assertion.AssertionValidationException;
import org.opensaml.saml.common.assertion.ValidationContext;
import org.opensaml.saml.common.assertion.ValidationResult;
import org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;

import edu.iu.IuException;

@SuppressWarnings("javadoc")
public class IuSubjectConfirmationValidationTest {

	public static final String SUBJECT_CONFIRMATION_LOCAL_ADDRESS = "10.1.2.3";
	public static final String SUBJECT_CONFIRMATION_ADDRESS = "128.0.0.0";

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

		result = validator.validateAddress(subjectConfirmationDataValid, assertion, validationContext, false);
		assertEquals(ValidationResult.VALID, result);

		// valid non local Address
		final var nonLocalAddress = mock(SubjectConfirmationData.class);
		when(nonLocalAddress.getAddress()).thenReturn("127.0.0.0");

		result = validator.validateAddress(nonLocalAddress, assertion, validationContext, false);
		assertEquals(ValidationResult.VALID, result);

		// valid non local Address - not in allowed list
		final var nonAllowedValidIpAddress = mock(SubjectConfirmationData.class);
		when(nonAllowedValidIpAddress.getAddress()).thenReturn("157.0.0.9");

		result = validator.validateAddress(nonAllowedValidIpAddress, assertion, validationContext, false);
		assertEquals(ValidationResult.VALID, result);

		// allowed list is null and failOnAddressMismatch is false
		validator = new IuSubjectConfirmationValidator(null, false);
		result = validator.validateAddress(nonAllowedValidIpAddress, assertion, validationContext, false);
		assertEquals(ValidationResult.VALID, result);

		// allowed list is null and failOnAddressMismatch is true
		validator = new IuSubjectConfirmationValidator(null, true);
		result = validator.validateAddress(nonAllowedValidIpAddress, assertion, validationContext, false);
		assertEquals(ValidationResult.INDETERMINATE, result);

	}

}
