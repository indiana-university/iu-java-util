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
package edu.iu.transaction;

import javax.transaction.xa.Xid;

import edu.iu.IdGenerator;
import edu.iu.IuObject;
import edu.iu.IuText;

/**
 * Portable {@link Xid} implementation.
 */
class IuXid implements Xid {

	private static final int IU_FORMAT_ID = 63225;

	private final byte[] gtrid;
	private final byte[] bqual;

	/**
	 * Creates a new root transaction identifier.
	 */
	IuXid() {
		this(null);
	}

	/**
	 * Creates a new branch transaction identifier.
	 * 
	 * @param parent parent id
	 */
	IuXid(IuXid parent) {
		if (parent == null)
			gtrid = IuText.base64Url(IdGenerator.generateId());
		else
			gtrid = parent.gtrid.clone();
		bqual = IuText.base64Url(IdGenerator.generateId());
	}

	@Override
	public int getFormatId() {
		return IU_FORMAT_ID;
	}

	@Override
	public byte[] getGlobalTransactionId() {
		return gtrid.clone();
	}

	@Override
	public byte[] getBranchQualifier() {
		return bqual.clone();
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(bqual, gtrid);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		IuXid other = (IuXid) obj;
		return IuObject.equals(gtrid, other.gtrid) //
				&& IuObject.equals(bqual, other.bqual);
	}

	public String toString() {
		return "iuxid-" + IU_FORMAT_ID + "+" + IuText.base64Url(gtrid) + "+" + IuText.base64Url(bqual);
	}

}
