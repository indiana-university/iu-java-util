package iu.auth.saml;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Logger;

import org.opensaml.saml.common.assertion.AssertionValidationException;
import org.opensaml.saml.common.assertion.ValidationContext;
import org.opensaml.saml.common.assertion.ValidationResult;
import org.opensaml.saml.saml2.assertion.impl.BearerSubjectConfirmationValidator;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.SubjectConfirmation;



public class IuSubjectConfirmationValidator extends BearerSubjectConfirmationValidator {

	private static final Logger LOG = Logger.getLogger(IuSubjectConfirmationValidator.class.getName());

	private final List<InetAddress>  allowedRanges;
	private final boolean failOnAddressMismatch;

	public IuSubjectConfirmationValidator(List<InetAddress> allowedRanges, boolean failOnAddressMismatch) {
		this.allowedRanges = allowedRanges;
		this.failOnAddressMismatch = failOnAddressMismatch;
	}

//	@Override
	/*protected ValidationResult validateAddress(SubjectConfirmation confirmation, Assertion assertion,
			ValidationContext context) throws AssertionValidationException {
		String address = StringSupport.trimOrNull(confirmation.getSubjectConfirmationData().getAddress());
		if (address == null)
			return ValidationResult.VALID;

		InetAddress[] confirmingAddresses;
		try {
			confirmingAddresses = InetAddress.getAllByName(address);
		} catch (UnknownHostException e) {
			context.setValidationFailureMessage(String
					.format("Subject confirmation address '%s' is not resolvable hostname or IP address", address));
			return ValidationResult.INDETERMINATE;
		}

		// Assume that subject confirmation with a private IP resides on the same
		// network as the IDP. Since we don't have a way to confirm which NAT'd address
		// the subject is calling from, this assumption will have to be good enough.
		for (InetAddress confirmingAddress : confirmingAddresses) {
			if (confirmingAddress.isSiteLocalAddress()) {
				LOG.fine("Allowing private IP " + confirmingAddress);
				return ValidationResult.VALID;
			}
			if (allowedRanges != null)
				for (String range : allowedRanges)
					if (WebUtil.isInetAddressInRange(confirmingAddress, range)) {
						LOG.fine("Allowing whitelisted IP " + confirmingAddress + "; range = " + range);
						return ValidationResult.VALID;
					}
		}

		ValidationResult result = super.validateAddress(confirmation, assertion, context);
		if (!ValidationResult.VALID.equals(result)) {
			SecurityLogger.LOG.info(() -> "IP address mismatch in SAML subject confirmation; remote address = "
					+ address + "; allowed ranges = " + allowedRanges);
			if (!failOnAddressMismatch)
				return ValidationResult.VALID;
		}
		return result;
	}*/

}
