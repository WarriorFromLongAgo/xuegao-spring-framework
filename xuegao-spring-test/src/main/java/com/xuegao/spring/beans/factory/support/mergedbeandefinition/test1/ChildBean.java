package com.xuegao.spring.beans.factory.support.mergedbeandefinition.test1;

import org.springframework.beans.factory.annotation.Autowired;

public class ChildBean {

	@Autowired
	A a;

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
		return "ChildBean {" +
				"country='" + country + '\'' +
				", city='" + city + '\'' +
				'}';
	}
}
