package eventcenter.leveldb;

import eventcenter.api.*;
import eventcenter.api.EventListener;
import eventcenter.api.support.DefaultEventCenter;
import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.junit.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class TestLevelDBContainerUnderLoadWithMaxPage {

	private LevelDBContainer container;

	private EventCenterConfig config;

	private LevelDBQueue queue;

	LevelDBPersistenceAdapter adapter;

	private QueueMiddleComponent queueMiddle;

	private Set<String> uuids;

	DefaultEventCenter eventCenter;

	File dir = new File(System.getProperty("user.dir") + File.separator + "target" + File.separator + "ecleveldb");

	JniDBFactory factory = new JniDBFactory();

	public TestLevelDBContainerUnderLoadWithMaxPage(){
		//org.apache.log4j.BasicConfigurator.configure();
	}
	
	@Before
	public void setUp() throws Exception{
		eventCenter = new DefaultEventCenter();
		uuids = Collections.synchronizedSet(new TreeSet<String>());
		adapter = new LevelDBPersistenceAdapter();
		queueMiddle = new QueueMiddleComponent(adapter);
		queueMiddle.setPageSize(1);

		adapter.setDirPath(dir);
		queue = new LevelDBQueue(queueMiddle);

		config = new EventCenterConfig();
		List<EventListener> listeners = new ArrayList<EventListener>();
		listeners.add(new TestListener());
		CommonEventListenerConfig listenerConfig = new CommonEventListenerConfig();
		listenerConfig.getListeners().put("test", listeners );
		listeners = new ArrayList<EventListener>();
		listeners.add(new TestListener2(uuids));
		listenerConfig.getListeners().put("test2", listeners);
		config.loadCommonEventListenerConfig(listenerConfig);
		eventCenter.setEcConfig(config);
		eventCenter.startup();
		container = new LevelDBContainer(config, queue);
		container.setCorePoolSize(8);
		container.setMaximumPoolSize(8);
		container.startup();
		queueMiddle.clear();

	}
	
	@After
	public void tearDown() throws Exception{
		queueMiddle.clear();
		container.shutdown();
		eventCenter.shutdown();
		factory.destroy(dir, new Options());
	}

	@Ignore
	@Test
	public void testUnderLoad() throws InterruptedException, PersistenceException {
		queueMiddle.readCursor = new LevelDBCursor();
		queueMiddle.readCursor.setIndex(queueMiddle.getPageSize() - 1);
		queueMiddle.readCursor.setPageNo(Long.MAX_VALUE);
		queueMiddle.writeCursor = new LevelDBCursor();
		queueMiddle.writeCursor.setIndex(queueMiddle.getPageSize());
		queueMiddle.writeCursor.setPageNo(Long.MAX_VALUE);
		queueMiddle.currentPage = new LevelDBPage();
		queueMiddle.currentPage.setNo(Long.MAX_VALUE);
		queueMiddle.currentPage.setIndexes(Arrays.asList("1"));
		WriteBatch wb = queueMiddle.adapter.getDb().createWriteBatch();
		queueMiddle.savePage(queueMiddle.currentPage, wb);
		queueMiddle.adapter.getDb().write(wb);
		long total = 1000;
		long start = System.currentTimeMillis();
		int produced = 0;
		final AtomicLong counter = new AtomicLong(0);
		System.out.println("current info ,wc:" + printCursor(queueMiddle.writeCursor) + ",rc:" + printCursor(queueMiddle.readCursor) + ",dp:" + printCursor(queueMiddle.deleteCursor) + ",cp:" + printPage(queueMiddle.currentPage));
		while(total >= (System.currentTimeMillis() - start)){
			queue.offer(new CommonEventSource(this, String.valueOf(counter.getAndIncrement()), "test2", null, null, null));
			System.out.println("total consumed:" + uuids.size() + ",wc:" + printCursor(queueMiddle.writeCursor) + ",rc:" + printCursor(queueMiddle.readCursor) + ",dp:" + printCursor(queueMiddle.deleteCursor) + ",cp:" + printPage(queueMiddle.currentPage));
			produced++;
		}
		System.out.println("total produced:" + produced);
		System.out.println("total consumed:" + uuids.size() + ",wc:" + printCursor(queueMiddle.writeCursor) + ",rc:" + printCursor(queueMiddle.readCursor) + ",dp:" + printCursor(queueMiddle.deleteCursor) + ",cp:" + printPage(queueMiddle.currentPage));
		System.out.println("wait for consumed end");
		long totalTime = 5000;
		long startConsumed = System.currentTimeMillis();
		while(uuids.size() < produced){
			System.out.println("current consumed size:" + uuids.size() + ",wc:" + printCursor(queueMiddle.writeCursor) + ",rc:" + printCursor(queueMiddle.readCursor) + ",dp:" + printCursor(queueMiddle.deleteCursor) + ",cp:" + printPage(queueMiddle.currentPage));
            Thread.sleep(1000);
			if(totalTime <= (System.currentTimeMillis() - startConsumed))
				break;
		}
		Assert.assertEquals(produced, uuids.size());
		System.out.println("current consumed size:" + uuids.size() + ",wc:" + printCursor(queueMiddle.writeCursor) + ",rc:" + printCursor(queueMiddle.readCursor) + ",dp:" + printCursor(queueMiddle.deleteCursor) + ",cp:" + printPage(queueMiddle.currentPage));
		System.out.println("consumed complete, total:" + (System.currentTimeMillis() - start) + " ms.");
		System.out.println("ratio:" + ((float) total / (System.currentTimeMillis() - start)));
		for(int i = 0;i < produced;i++){
			if(!uuids.contains(String.valueOf(i))){
				System.err.println("not consumed event:" + i);
			}
		}
	}

	private String printCursor(LevelDBCursor c){
		return new StringBuilder().append(c.getPageNo()).append("-").append(c.getIndex()).toString();
	}

	private String printPage(LevelDBPage c){
		return new StringBuilder().append(c.getNo()).append("-").append(c.getIndexes().size()).toString();
	}

	class TestListener implements EventListener {

		@Override
		public void onObserved(CommonEventSource source) {
			try {
				System.out.println("begin consuming:" + source.getEventId());
				Thread.sleep(1000);
				System.out.println("consumed:" + source.getEventId());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}

	class TestListener2 implements EventListener {

		private final Set<String> uuids;
		
		public TestListener2(Set<String> uuids){
			this.uuids = uuids;
		}
		
		@Override
		public void onObserved(CommonEventSource source) {
			if(this.uuids.contains(source.getEventId())){
				System.err.println("读取了重复事件：" + source.getEventId());
				return ;
			}
			this.uuids.add(source.getEventId());
		}
		
	}
}
