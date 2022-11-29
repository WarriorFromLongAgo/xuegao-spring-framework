// package test1;
//
// import org.apache.catalina.Context;
// import org.apache.catalina.LifecycleException;
// import org.apache.catalina.connector.Connector;
// import org.apache.catalina.startup.Tomcat;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
// import org.springframework.web.servlet.DispatcherServlet;
//
// import java.io.File;
//
// public class TomcatTestV2 {
//
// 	@GetMapping("/ping")
// 	public String ping() {
// 		return "pong";
// 	}
//
// 	public static void main(String[] args) {
// 		Tomcat tomcat = new Tomcat();
// 		Connector conn = new Connector();
// 		conn.setPort(80);
// 		tomcat.setConnector(conn);
//
// 		DispatcherServlet dispatcherServlet = new DispatcherServlet();
// 		AnnotationConfigWebApplicationContext annotationConfigWebApplicationContext = new AnnotationConfigWebApplicationContext();
// 		annotationConfigWebApplicationContext.register(TomcatTestV2.class);
// 		annotationConfigWebApplicationContext.refresh();
// 		dispatcherServlet.setApplicationContext(annotationConfigWebApplicationContext);
// 		Context ctx = tomcat.addContext("", new File(".").getAbsolutePath());
// 		Tomcat.addServlet(ctx, "mvc", dispatcherServlet).addMapping("/*");
// 		try {
// 			tomcat.start();
// 			tomcat.getServer().await();
// 		} catch (LifecycleException e) {
// 			e.printStackTrace();
// 		}
// 	}
//
// 	// 作者：用户347577237354
// 	// 链接：https://juejin.cn/post/7065230530123399176
// 	// 来源：稀土掘金
// 	// 著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
// }
