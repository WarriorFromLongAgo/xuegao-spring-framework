package com.xuegao.test;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class DefaultListableBeanFactoryTestV1 {
	public static void main(String[] args) {
		//创建一个DefaultListableBeanFactory实例
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		//创建一个BeanDefinition
		RootBeanDefinition beanDefinition = new RootBeanDefinition(Wheel.class);
		//将BeanDefinition注册到容器中
		beanFactory.registerBeanDefinition("wheel", beanDefinition);
	}

	public static class Wheel {
	}
}