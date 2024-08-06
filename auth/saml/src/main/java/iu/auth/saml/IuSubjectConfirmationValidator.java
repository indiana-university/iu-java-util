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
package iu.auth.saml;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import org.opensaml.saml.common.assertion.AssertionValidationException;
import org.opensaml.saml.common.assertion.ValidationContext;
import org.opensaml.saml.common.assertion.ValidationResult;
import org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters;
import org.opensaml.saml.saml2.assertion.impl.BearerSubjectConfirmationValidator;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;

import edu.iu.IuWebUtils;
import net.shibboleth.shared.primitive.StringSupport;

/**
 * Validates a bearer subject confirmation. Support to validate subject details
 * return as SAML response as successful authentication
 */
public class IuSubjectConfirmationValidator extends BearerSubjectConfirmationValidator {

	private static final Logger LOG = Logger.getLogger(IuSubjectConfirmationValidator.class.getName());

	private final Iterable<String> allowedRanges;
	private final boolean failOnAddressMismatch;

	/**
	 * Constructor.
	 * 
	 * @param allowedRanges         allowed IP address list
	 * @param failOnAddressMismatch determine whether to fail on address mismatch or
	 *                              not, true if required, false if not
	 */
	public IuSubjectConfirmationValidator(Iterable<String> allowedRanges, boolean failOnAddressMismatch) {
		this.allowedRanges = allowedRanges;
		this.failOnAddressMismatch = failOnAddressMismatch;
	}

	@Override
	protected ValidationResult validateAddress(SubjectConfirmationData confirmationData, Assertion assertion,
			ValidationContext context, boolean required) throws AssertionValidationException {
		super.validateAddress(confirmationData, assertion, context, required);

		final Boolean checkAddress = (Boolean) context.getStaticParameters()
				.get(SAML2AssertionValidationParameters.SC_CHECK_ADDRESS);

		if (checkAddress != null && !checkAddress) {
			LOG.fine("SubjectConfirmationData/@Address check is disabled, skipping");
			return ValidationResult.VALID;
		}

		final String address = StringSupport.trimOrNull(confirmationData.getAddress());
		if (address == null) {
			if (required) {
				context.getValidationFailureMessages()
						.add("SubjectConfirmationData/@Address was missing and was required");
				return ValidationResult.INVALID;
			}
			return ValidationResult.VALID;
		}

		InetAddress[] confirmingAddresses;
		try {
			confirmingAddresses = InetAddress.getAllByName(address);
		} catch (UnknownHostException e) {
			context.getValidationFailureMessages().add(String
					.format("Subject confirmation address '%s' is not resolvable hostname or IP address", address));
			return ValidationResult.INDETERMINATE;
		}

		// Assume that subject confirmation with a private IP resides on the same //
		// network as the IDP. Since we don't have a way to confirm which NAT'd address
		// the subject is calling from, this assumption will have to be good enough.
		for (InetAddress confirmingAddress : confirmingAddresses) {
			if (confirmingAddress.isSiteLocalAddress()) {
				LOG.fine(() -> "Allowing private IP " + confirmingAddress);
				return ValidationResult.VALID;
			}
			if (allowedRanges != null)
				for (String range : allowedRanges)
					if (IuWebUtils.isInetAddressInRange(confirmingAddress, range.toString())) {
						LOG.fine(() -> "Allowing IP " + confirmingAddress + "; range = " + range);
						return ValidationResult.VALID;
					}
		}

		ValidationResult result = super.validateAddress(confirmationData, assertion, context, required);

		if (!ValidationResult.VALID.equals(result)) {
			LOG.info(() -> "IP address mismatch in SAML subject confirmation; remote address = " + address
					+ "; allowed ranges = " + allowedRanges);

			if (!failOnAddressMismatch)
				return ValidationResult.VALID;
		}

		return result;
	}

}
