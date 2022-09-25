package com.xuegao.spring.beans.factory.support.mergedbeandefinition.test1;

import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.xuegao.spring.beans.factory.support.mergedbeandefinition.test1")
public class MergeAppConfig {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext();
		ac.register(MergeAppConfig.class);

		RootBeanDefinition rootBeanDefinition = new RootBeanDefinition();
		rootBeanDefinition.setBeanClass(RootBean.class);
		rootBeanDefinition.getPropertyValues().add("country", "hubei");
		rootBeanDefinition.getPropertyValues().add("city", "wuhan");
		ac.registerBeanDefinition("root", rootBeanDefinition);

		GenericBeanDefinition genericBeanDefinition = new GenericBeanDefinition();
		genericBeanDefinition.setBeanClass(ChildBean.class);
		genericBeanDefinition.setParentName("root");
		genericBeanDefinition.getPropertyValues().add("city", "shenzhen");
		ac.registerBeanDefinition("child", genericBeanDefinition);

		ac.refresh();
		System.out.println(ac.getBean("child"));
	}
}
