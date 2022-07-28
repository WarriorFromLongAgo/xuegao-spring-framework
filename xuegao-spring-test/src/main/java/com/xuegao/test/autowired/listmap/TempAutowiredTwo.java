package com.xuegao.test.autowired.listmap;

import org.springframework.stereotype.Service;

@Service
public class TempAutowiredTwo implements ITempAutowired{
	@Override
	public void say() {
		System.out.println(" TempAutowiredTwo ");
	}
}
