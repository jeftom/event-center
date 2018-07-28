package eventcenter.remote.dubbo.saf.test;

import eventcenter.remote.dubbo.test.SampleMonitorService;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import eventcenter.remote.dubbo.test.SampleMonitorService;

public class Subscriber {

	public static void main(String[] args) {
		org.apache.log4j.BasicConfigurator.configure();
		ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("spring-saf/spring-ec-subscriber.xml");
		ctx.getBean("sampleMonitorService", SampleMonitorService.class);
		System.out.println("启动监听器成功！");
		ctx.close();
	}

}
