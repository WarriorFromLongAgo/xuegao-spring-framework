package test1;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.File;

public class AnnotatedBeanDefinitionReaderContext {

	public static void main(String[] args) throws LifecycleException {
		File base = new File(System.getProperty("java.io.tmpdir"));
		Tomcat tomcat = new Tomcat();
		tomcat.setPort(8080);
		tomcat.addContext("/", base.getAbsolutePath());
		tomcat.start();
		Wrapper wrapper = tomcat.addServlet("/", "app", getDispatcherServlet());
		wrapper.setLoadOnStartup(1);
		wrapper.addMapping("/");
		tomcat.getServer().await();

	}


	public static DispatcherServlet getDispatcherServlet() {
		//容器添加需要启动的ApplicationContext
		AnnotationConfigWebApplicationContext annotationConfigWebApplicationContext
				= new AnnotationConfigWebApplicationContext();
		annotationConfigWebApplicationContext.register(AnnotatedBeanDefinitionReaderContext.class);
		annotationConfigWebApplicationContext.refresh();
		return new DispatcherServlet(annotationConfigWebApplicationContext);
	}

}


