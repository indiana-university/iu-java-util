package edu.iu.transaction;

import javax.transaction.xa.Xid;

import edu.iu.IdGenerator;
import edu.iu.IuObject;

/**
 * Portable {@link Xid} implementation.
 */
public class IuXid implements Xid {

	private static final int IU_FORMAT_ID = 01_733_7_0;

	private final byte[] gtrid;
	private final byte[] bqual;

	/**
	 * Creates a new root transaction identifier.
	 */
	public IuXid() {
		this(null);
	}

	/**
	 * Creates a new branch transaction identifier.
	 * 
	 * @param parent parent id
	 */
	IuXid(IuXid parent) {
		if (parent == null)
			gtrid = IdGenerator.generateRawId();
		else
			gtrid = parent.gtrid;
		bqual = IdGenerator.generateRawId();
	}

	@Override
	public int getFormatId() {
		return IU_FORMAT_ID;
	}

	@Override
	public byte[] getGlobalTransactionId() {
		return gtrid;
	}

	@Override
	public byte[] getBranchQualifier() {
		return bqual;
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
		return "iuxid-" + IU_FORMAT_ID + "+" + IdGenerator.encodeId(gtrid) + "+" + IdGenerator.encodeId(bqual);
	}

}
