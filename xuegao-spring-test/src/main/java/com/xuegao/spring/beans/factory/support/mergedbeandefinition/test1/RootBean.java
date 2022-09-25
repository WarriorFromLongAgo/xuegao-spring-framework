package com.xuegao.spring.beans.factory.support.mergedbeandefinition.test1;

public class RootBean {
	private String country;

	private String city;

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	@Override
	public String toString() {
		return "RootBean {" +
				"country='" + country + '\'' +
				", city='" + city + '\'' +
				'}';
	}
}
