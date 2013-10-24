package cz.metacentrum.perun.cabinet.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Class represents Thanks = expression of acknowledgment
 * from authors to facility owners.
 * 
 * @author Jiri Harazim <harazim@mail.muni.cz>
 * @author Pavel Zlamal <256627@mail.muni.cz>
 * @version $Id$
 */
public class Thanks implements Serializable {
    
	private static final long serialVersionUID = 1L;

	/**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column THANKS.id
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    private Integer id;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column THANKS.publicationId
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    private Integer publicationId;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column THANKS.ownerId
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    private Integer ownerId;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column THANKS.createdBy
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    private String createdBy;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column THANKS.createdDate
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    private Date createdDate;

    public Thanks() {
    	
    }
    
    public Thanks(Integer id, Integer publicationId, Integer ownerId,
			String createdBy, Date createdDate) {
		super();
		this.id = id;
		this.publicationId = publicationId;
		this.ownerId = ownerId;
		this.createdBy = createdBy;
		this.createdDate = createdDate;
	}

	/**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column THANKS.id
     *
     * @return the value of THANKS.id
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    public Integer getId() {
        return id;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column THANKS.id
     *
     * @param id the value for THANKS.id
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column THANKS.reportId
     *
     * @return the value of THANKS.reportId
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    public Integer getPublicationId() {
        return publicationId;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column THANKS.reportId
     *
     * @param reportId the value for THANKS.reportId
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    public void setPublicationId(Integer publicationId) {
        this.publicationId = publicationId;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column THANKS.ownerId
     *
     * @return the value of THANKS.ownerId
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    public Integer getOwnerId() {
        return ownerId;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column THANKS.ownerId
     *
     * @param ownerId the value for THANKS.ownerId
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    public void setOwnerId(Integer ownerId) {
        this.ownerId = ownerId;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column THANKS.createdBy
     *
     * @return the value of THANKS.createdBy
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column THANKS.createdBy
     *
     * @param createdBy the value for THANKS.createdBy
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column THANKS.createdDate
     *
     * @return the value of THANKS.createdDate
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    public Date getCreatedDate() {
        return createdDate;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column THANKS.createdDate
     *
     * @param createdDate the value for THANKS.createdDate
     *
     * @mbggenerated Fri Nov 04 19:38:27 CET 2011
     */
    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public String getBeanName() {
        return this.getClass().getSimpleName();
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	/**
	 * Two instances of Thanks are equal if their id property is equal and not null.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Thanks other = (Thanks) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
    
	@Override
	public String toString() {
		return getClass().getSimpleName()+":[id="+ id + ", pubId="+ publicationId +", ownerId="+ ownerId +", createdBy="+ createdBy +", createdDate="+ createdDate +"]";
	}
	
}