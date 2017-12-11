package com.moglix.reports.model;

import java.util.Date;

public class Agreement {
	private String agreementNo;
	private String agreementType;
	private String docType;
	private String purchaseGroup;
	private Date startDate;
	private Date endDate;
	private String currency;
	private int item_count;
	private Company company;
	private Plant plant;
	
	public String getAgreementNo() {
		return agreementNo;
	}

	public void setAgreementNo(String agreementNo) {
		this.agreementNo = agreementNo;
	}

	public String getAgreementType() {
		return agreementType;
	}

	public void setAgreementType(String agreementType) {
		this.agreementType = agreementType;
	}

	public String getDocType() {
		return docType;
	}

	public void setDocType(String docType) {
		this.docType = docType;
	}

	public String getPurchaseGroup() {
		return purchaseGroup;
	}

	public void setPurchaseGroup(String purchaseGroup) {
		this.purchaseGroup = purchaseGroup;
	}

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public int getItem_count() {
		return item_count;
	}

	public void setItem_count(int item_count) {
		this.item_count = item_count;
	}

	public Company getCompany() {
		return company;
	}

	public void setCompany(Company company) {
		this.company = company;
	}

	public Plant getPlant() {
		return plant;
	}

	public void setPlant(Plant plant) {
		this.plant = plant;
	}

	public static class Company {
		private String name;
		private String code;
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getCode() {
			return code;
		}
		public void setCode(String code) {
			this.code = code;
		}
		@Override
		public String toString() {
			return "Company [name=" + name + ", code=" + code + "]";
		}
	}
	
	public static class Plant {
		private String name;
		private String code;
		
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getCode() {
			return code;
		}
		public void setCode(String code) {
			this.code = code;
		}
		@Override
		public String toString() {
			return "Plant [name=" + name + ", code=" + code + "]";
		}
	}

	@Override
	public String toString() {
		return "Agreement [agreementNo=" + agreementNo + ", agreementType=" + agreementType + ", docType=" + docType
				+ ", purchaseGroup=" + purchaseGroup + ", startDate=" + startDate + ", endDate=" + endDate
				+ ", currency=" + currency + ", item_count=" + item_count + ", company=" + company + ", plant=" + plant
				+ "]";
	}
}
