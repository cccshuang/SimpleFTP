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
			.getCanonicalName()); //记录日志
	
	private final static int TCP_PORT = 2021; // TCP连接负责与用户交互
	private final static int UDP_PORT = 2020; // UDP连接负责与传输文件
	private final static int POOL_SIZE = 4; // 单个处理器线程池工作线程数目

	private final File rootDirectory; // 文件夹根目录

	public FileServer(File rootDirectory) throws IOException {

		if (!rootDirectory.isDirectory()) {
			throw new IOException(rootDirectory
					+ "必须为一个目录");
		}
		this.rootDirectory = rootDirectory;
	}

	public void start() throws IOException {
		
		ExecutorService pool = Executors.newFixedThreadPool(Runtime
				.getRuntime().availableProcessors() * POOL_SIZE);
		
		try (ServerSocket tcpServer = new ServerSocket(TCP_PORT);DatagramSocket udpServer = new DatagramSocket(UDP_PORT);) {
			logger.info("接受连接的端口： " + tcpServer.getLocalPort());
			logger.info("根目录 : " + rootDirectory);

			while (true) {
				try {
					Socket request = tcpServer.accept();
					Runnable r = new FileServerProcessor(rootDirectory, request,udpServer);
					pool.submit(r);
				} catch (IOException ex) {
					logger.log(Level.WARNING, "接受连接出错", ex);
				}
			}
		}
	}

	public static void main(String[] args) {

		File docroot; //根目录
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
			logger.log(Level.SEVERE, "服务器启动失败", ex);
		}
	}

}
