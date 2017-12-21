import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author S
 *
 */
public class FileServer {

	private static final Logger logger = Logger.getLogger(FileServer.class
			.getCanonicalName()); //��¼��־
	
	private final static int TCP_PORT = 2021; // TCP���Ӹ������û�����
	private final static int UDP_PORT = 2020; // UDP���Ӹ����봫���ļ�
	private final static int POOL_SIZE = 4; // �����������̳߳ع����߳���Ŀ

	private final File rootDirectory; // �ļ��и�Ŀ¼

	public FileServer(File rootDirectory) throws IOException {

		if (!rootDirectory.isDirectory()) {
			throw new IOException(rootDirectory
					+ "����Ϊһ��Ŀ¼");
		}
		this.rootDirectory = rootDirectory;
	}

	public void start() throws IOException {
		
		ExecutorService pool = Executors.newFixedThreadPool(Runtime
				.getRuntime().availableProcessors() * POOL_SIZE);
		
		try (ServerSocket tcpServer = new ServerSocket(TCP_PORT);DatagramSocket udpServer = new DatagramSocket(UDP_PORT);) {
			logger.info("�������ӵĶ˿ڣ� " + tcpServer.getLocalPort());
			logger.info("��Ŀ¼ : " + rootDirectory);

			while (true) {
				try {
					Socket request = tcpServer.accept();
					Runnable r = new FileServerProcessor(rootDirectory, request,udpServer);
					pool.submit(r);
				} catch (IOException ex) {
					logger.log(Level.WARNING, "�������ӳ���", ex);
				}
			}
		}
	}

	public static void main(String[] args) {

		File docroot; //��Ŀ¼
		try {
			docroot = new File(args[0]);
		} catch (ArrayIndexOutOfBoundsException ex) {
			System.out.println("Usage: java FileServer docroot");
			return;
		}

		try {
			FileServer fileServer = new FileServer(docroot);
			fileServer.start();
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "����������ʧ��", ex);
		}
	}

}
