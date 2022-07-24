package com.xuegao.config;

import com.xuegao.dao.ConfigDao1;
import com.xuegao.dao.ConfigDao2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 组件
 *
 * @author ：HUANG ZHI XUE
 * @date ：Create in 2020-12-04
 */
@Configuration
@ComponentScan("com.xuegao")
public class JavaConfig {

	@Bean
	public ConfigDao1 configDao1() {   //自己写一个configDao1类即可
		return new ConfigDao1();
	}

	@Bean
	public ConfigDao2 configDao2() {   //自己写一个configDao2类即可
		configDao1();
		return new ConfigDao2();
	}

}