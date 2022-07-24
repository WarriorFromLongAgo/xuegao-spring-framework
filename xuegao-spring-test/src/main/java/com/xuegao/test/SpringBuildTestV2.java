package com.xuegao.test;

import com.xuegao.config.JavaConfig;
import com.xuegao.dao.IndexDao;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 测试类
 *
 * @author ：xuegao
 * @date ：Create in 2022-07-24
 */
public class SpringBuildTestV2 {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext();
		ac.register(JavaConfig.class);
		ac.refresh();

		IndexDao indexDao = ac.getBean(IndexDao.class);
		System.out.println(indexDao);
		indexDao.query();

	}
}