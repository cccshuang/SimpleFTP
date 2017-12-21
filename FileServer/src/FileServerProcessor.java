import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author S
 *
 */
public class FileServerProcessor implements Runnable {

	private final static Logger logger = Logger
			.getLogger(FileServerProcessor.class.getCanonicalName());

	private Stack<File> rootDirectoryStack = new Stack<File>(); // 使用stack来记录文件夹的递归结构
	private Socket connection; // tcp连接
	private DatagramSocket udpServer; // udp连接

	public FileServerProcessor(File rootDirectory, Socket connection,
			DatagramSocket udpServer) {

		if (rootDirectory.isFile()) {
			throw new IllegalArgumentException("根目录必须是一个文件夹");
		}
		try {
			rootDirectory = rootDirectory.getCanonicalFile();
		} catch (IOException ex) {
		}
		rootDirectoryStack.push(rootDirectory); // 将根目录入栈
		this.connection = connection;
		this.udpServer = udpServer;
	}

	@Override
	public void run() {
		try {
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
					connection.getOutputStream()));
			BufferedReader br = new BufferedReader(new InputStreamReader(
					connection.getInputStream()));

			// 连接成功时发送"客户端IP地址:客户端端口号>连接成功"
			bw.write(connection.getRemoteSocketAddress() + ">连接成功\r\n");
			bw.flush();
			logger.info("连接到客户端：" + connection.getRemoteSocketAddress());

			String command = null;
			while (null != (command = br.readLine())) {
				logger.info("接收到到客户端命令：" + command);

				if (command.equals("bye")) {
					logger.info(connection.getRemoteSocketAddress() + "已断开连接");
					break;
				} else if (command.equals("ls")) {// 列出当前文件夹内所有内容
					getAllFileList(bw);
				} else if (command.equals("cd..")) {// 返回上一目录
					if (rootDirectoryStack.size() <= 1) { // 根目录时
						bw.write("has been the root directory\r\n");
						bw.write(rootDirectoryStack.peek().getName()
								+ " > OK\r\n");
						bw.write("end\r\n"); // 结束的标记，以便客户端判断
						bw.flush();
					} else {
						rootDirectoryStack.pop(); // 不是根目录时，弹出栈顶元素
						bw.write(rootDirectoryStack.peek().getName()
								+ " > OK\r\n"); // 显示当前栈顶元素
						bw.write("end\r\n");
						bw.flush();
					}

				} else if (command.startsWith("cd ")) {
					goToTargetDic(command, bw);
				} else if (command.startsWith("get ")) {
					getTargetFile(command, bw);
				} else {
					bw.write("unknown cmd\r\n");
					bw.write("end\r\n");
					bw.flush();
				}

			}

		} catch (IOException ex) {
			logger.log(Level.WARNING, "与" + connection.getRemoteSocketAddress()
					+ "通信时发生错误", ex);
		} catch (InterruptedException e) {
			logger.log(Level.WARNING, "与" + connection.getRemoteSocketAddress()
					+ "传输文件过程中发生错误", e);
		} finally {
			try {
				if (null != connection) {
					connection.close();
				}
			} catch (IOException ex) {
			}
		}
	}

	/**
	 * 获得当前文件夹下所有文件列表
	 * 
	 * @param bw
	 *            socket的输出流
	 * @throws IOException
	 *             bw在向客户端写时抛出的异常
	 */
	private void getAllFileList(BufferedWriter bw) throws IOException {
		File files[] = rootDirectoryStack.peek().listFiles();
		StringBuilder fileListsInfo = new StringBuilder();
		for (File file : files) {
			String tmpStr = "";
			if (file.isDirectory()) { // 如果是文件夹
				tmpStr = String.format("%-10s", "<dir>")
						+ String.format("%-40s", file.getName());
				fileListsInfo.append(tmpStr + " " + getDirSize(file) + "\r\n");
			} else { // 如果是文件
				tmpStr = String.format("%-10s", "<file>")
						+ String.format("%-40s", file.getName());
				fileListsInfo.append(tmpStr + " " + file.length() + "\r\n");
			}
		}
		bw.write(fileListsInfo.toString());
		bw.write("end\r\n");
		bw.flush();
	}

	/**
	 * 返回文件夹的大小
	 * 
	 * @param dir
	 *            文件夹
	 * @return 文件夹大小(b)
	 */
	private long getDirSize(File dir) {
		if (dir.isFile())
			return dir.length();
		File[] children = dir.listFiles();
		long total = 0;
		if (children != null)
			for (File child : children)
				total += getDirSize(child);
		return total;
	}

	/**
	 * 进入用户要求进入的文件夹
	 * 
	 * @param command
	 *            客户端发来的命令
	 * @param bw
	 *            socket的输出流
	 * @throws IOException
	 *             bw在向客户端写时抛出的异常
	 */
	private void goToTargetDic(String command, BufferedWriter bw)
			throws IOException {

		String targetDicStr = command.substring(3, command.length());
		File files[] = rootDirectoryStack.peek().listFiles();
		boolean directoryExist = false;
		for (File file : files) {

			if (targetDicStr.equals(file.getName())) { // 判断是否在当前目录
				if (file.isDirectory()) { // 如果是文件夹
					directoryExist = true;
					rootDirectoryStack.push(file); // 将文件夹压栈，设为当前文件夹
					bw.write(targetDicStr + " > OK\r\n");
					bw.write("end\r\n");
					bw.flush();
				}
			}
		}
		if (!directoryExist) { // 如果不在当前目录里
			bw.write("unknown dir\r\n");
			bw.write("end\r\n");
			bw.flush();
		}
	}

	/**
	 * 传输用户请求的文件
	 * 
	 * @param command
	 *            客户端发来的命令
	 * @param bw
	 *            socket的输出流
	 * @throws IOException
	 *             bw在向客户端写时抛出的异常
	 * @throws InterruptedException
	 *             sendFileByUDP方法抛出的异常
	 */
	private void getTargetFile(String command, BufferedWriter bw)
			throws IOException, InterruptedException {
		String targetFileStr = command.substring(4, command.length()).trim();
		File files[] = rootDirectoryStack.peek().listFiles();
		boolean fileExist = false;
		File targetFile = null;
		String targetFileName = "";
		long targetFileLength = -1;
		for (File file : files) {
			if (targetFileStr.equals(file.getName().trim())) { // 判断是否在当前目录
				targetFile = file;
				if (!file.isDirectory()) { // 不是目录
					fileExist = true;
					targetFileName = targetFile.getName();
					targetFileLength = targetFile.length();
					break;
				}
			}
		}

		sendHeader(bw, targetFileName, targetFileLength, fileExist);// 若成功则发送文件的名字和大小信息,反之statue为unknown
																	// file
		if (fileExist) { // 如果存在文件，则发送文件内容
			sendFileByUDP(targetFile.toPath());
		}
	}

	/**
	 * 使用UDP来传输文件
	 * 
	 * @param path
	 *            要进行传输的文件的路径
	 * @throws IOException
	 *             bw在向客户端写时抛出的异常
	 * @throws InterruptedException
	 */
	private void sendFileByUDP(Path path) throws IOException,
			InterruptedException {
		byte[] data = Files.readAllBytes(path);
		int contentLength = data.length; // 文件字节数
		try {
			byte[] recvBuffer = new byte[1024];
			DatagramPacket recvPacket = new DatagramPacket(recvBuffer,
					recvBuffer.length);
			udpServer.receive(recvPacket); // 先接收客户端传来的信息，以获得客户端的地址和udp端口
			byte[] buffer = new byte[1024];
			int offset = 0; // 文件的分割标记
			while (offset < contentLength) {
				int sendLength = (buffer.length > contentLength - offset) ? contentLength
						- offset
						: buffer.length;// 若最后一次少于buffer大小，则设为contentLength-
										// offset
				System.arraycopy(data, offset, buffer, 0, sendLength); // 将data数组中从offset开始的sendLength个元素拷贝进buffer数组
				DatagramPacket packet = new DatagramPacket(buffer, sendLength,
						recvPacket.getAddress(), recvPacket.getPort());
				TimeUnit.MICROSECONDS.sleep(1);
				udpServer.send(packet);
				offset += sendLength;
			}

		} catch (SocketException ex) {
			logger.log(Level.WARNING, "与" + connection.getRemoteSocketAddress()
					+ "使用UDP传输文件过程中发生错误", ex);
		}
	}

	/**
	 * 在udp进行文件传输前，先把文件的基本信息以JSON格式传给客户端
	 * 
	 * @param bw
	 *            socket的输出流
	 * @param fileName
	 *            要传输的文件名，若不存在则为""
	 * @param contentLength
	 *            要传输的文件长度，若不存在则为-1
	 * @param fileExist
	 *            要传输的文件是否存在
	 * @throws IOException
	 *             bw在向客户端写时抛出的异常
	 */
	private void sendHeader(BufferedWriter bw, String fileName,
			long contentLength, boolean fileExist) throws IOException {
		JSONObject jsonObj = new JSONObject();
		try {
			if (fileExist) {
				jsonObj.put("statue", "OK");
			} else {
				jsonObj.put("statue", "unknown file");
			}

			jsonObj.put("file-name", fileName);
			jsonObj.put("content-length", contentLength);
			bw.write(jsonObj.toString() + "\r\n");
			bw.write("end\n");
			bw.flush();
		} catch (JSONException e) {
			logger.log(Level.WARNING, "与" + connection.getRemoteSocketAddress()
					+ "使用UDP传输文件前将必要信息通过JSON格式传给客户端时发生错误", e);
		}

	}

}
