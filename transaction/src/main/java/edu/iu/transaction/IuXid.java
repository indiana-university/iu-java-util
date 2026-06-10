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
