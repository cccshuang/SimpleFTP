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

	private Stack<File> rootDirectoryStack = new Stack<File>(); // ʹ��stack����¼�ļ��еĵݹ�ṹ
	private Socket connection; // tcp����
	private DatagramSocket udpServer; // udp����

	public FileServerProcessor(File rootDirectory, Socket connection,
			DatagramSocket udpServer) {

		if (rootDirectory.isFile()) {
			throw new IllegalArgumentException("��Ŀ¼������һ���ļ���");
		}
		try {
			rootDirectory = rootDirectory.getCanonicalFile();
		} catch (IOException ex) {
		}
		rootDirectoryStack.push(rootDirectory); // ����Ŀ¼��ջ
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

			// ���ӳɹ�ʱ����"�ͻ���IP��ַ:�ͻ��˶˿ں�>���ӳɹ�"
			bw.write(connection.getRemoteSocketAddress() + ">���ӳɹ�\r\n");
			bw.flush();
			logger.info("���ӵ��ͻ��ˣ�" + connection.getRemoteSocketAddress());

			String command = null;
			while (null != (command = br.readLine())) {
				logger.info("���յ����ͻ������" + command);

				if (command.equals("bye")) {
					logger.info(connection.getRemoteSocketAddress() + "�ѶϿ�����");
					break;
				} else if (command.equals("ls")) {// �г���ǰ�ļ�������������
					getAllFileList(bw);
				} else if (command.equals("cd..")) {// ������һĿ¼
					if (rootDirectoryStack.size() <= 1) { // ��Ŀ¼ʱ
						bw.write("has been the root directory\r\n");
						bw.write(rootDirectoryStack.peek().getName()
								+ " > OK\r\n");
						bw.write("end\r\n"); // �����ı�ǣ��Ա�ͻ����ж�
						bw.flush();
					} else {
						rootDirectoryStack.pop(); // ���Ǹ�Ŀ¼ʱ������ջ��Ԫ��
						bw.write(rootDirectoryStack.peek().getName()
								+ " > OK\r\n"); // ��ʾ��ǰջ��Ԫ��
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
			logger.log(Level.WARNING, "��" + connection.getRemoteSocketAddress()
					+ "ͨ��ʱ��������", ex);
		} catch (InterruptedException e) {
			logger.log(Level.WARNING, "��" + connection.getRemoteSocketAddress()
					+ "�����ļ������з�������", e);
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
	 * ��õ�ǰ�ļ����������ļ��б�
	 * 
	 * @param bw
	 *            socket�������
	 * @throws IOException
	 *             bw����ͻ���дʱ�׳����쳣
	 */
	private void getAllFileList(BufferedWriter bw) throws IOException {
		File files[] = rootDirectoryStack.peek().listFiles();
		StringBuilder fileListsInfo = new StringBuilder();
		for (File file : files) {
			String tmpStr = "";
			if (file.isDirectory()) { // ������ļ���
				tmpStr = String.format("%-10s", "<dir>")
						+ String.format("%-40s", file.getName());
				fileListsInfo.append(tmpStr + " " + getDirSize(file) + "\r\n");
			} else { // ������ļ�
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
	 * �����ļ��еĴ�С
	 * 
	 * @param dir
	 *            �ļ���
	 * @return �ļ��д�С(b)
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
	 * �����û�Ҫ�������ļ���
	 * 
	 * @param command
	 *            �ͻ��˷���������
	 * @param bw
	 *            socket�������
	 * @throws IOException
	 *             bw����ͻ���дʱ�׳����쳣
	 */
	private void goToTargetDic(String command, BufferedWriter bw)
			throws IOException {

		String targetDicStr = command.substring(3, command.length());
		File files[] = rootDirectoryStack.peek().listFiles();
		boolean directoryExist = false;
		for (File file : files) {

			if (targetDicStr.equals(file.getName())) { // �ж��Ƿ��ڵ�ǰĿ¼
				if (file.isDirectory()) { // ������ļ���
					directoryExist = true;
					rootDirectoryStack.push(file); // ���ļ���ѹջ����Ϊ��ǰ�ļ���
					bw.write(targetDicStr + " > OK\r\n");
					bw.write("end\r\n");
					bw.flush();
				}
			}
		}
		if (!directoryExist) { // ������ڵ�ǰĿ¼��
			bw.write("unknown dir\r\n");
			bw.write("end\r\n");
			bw.flush();
		}
	}

	/**
	 * �����û�������ļ�
	 * 
	 * @param command
	 *            �ͻ��˷���������
	 * @param bw
	 *            socket�������
	 * @throws IOException
	 *             bw����ͻ���дʱ�׳����쳣
	 * @throws InterruptedException
	 *             sendFileByUDP�����׳����쳣
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
			if (targetFileStr.equals(file.getName().trim())) { // �ж��Ƿ��ڵ�ǰĿ¼
				targetFile = file;
				if (!file.isDirectory()) { // ����Ŀ¼
					fileExist = true;
					targetFileName = targetFile.getName();
					targetFileLength = targetFile.length();
					break;
				}
			}
		}

		sendHeader(bw, targetFileName, targetFileLength, fileExist);// ���ɹ������ļ������ֺʹ�С��Ϣ,��֮statueΪunknown
																	// file
		if (fileExist) { // ��������ļ��������ļ�����
			sendFileByUDP(targetFile.toPath());
		}
	}

	/**
	 * ʹ��UDP�������ļ�
	 * 
	 * @param path
	 *            Ҫ���д�����ļ���·��
	 * @throws IOException
	 *             bw����ͻ���дʱ�׳����쳣
	 * @throws InterruptedException
	 */
	private void sendFileByUDP(Path path) throws IOException,
			InterruptedException {
		byte[] data = Files.readAllBytes(path);
		int contentLength = data.length; // �ļ��ֽ���
		try {
			byte[] recvBuffer = new byte[1024];
			DatagramPacket recvPacket = new DatagramPacket(recvBuffer,
					recvBuffer.length);
			udpServer.receive(recvPacket); // �Ƚ��տͻ��˴�������Ϣ���Ի�ÿͻ��˵ĵ�ַ��udp�˿�
			byte[] buffer = new byte[1024];
			int offset = 0; // �ļ��ķָ���
			while (offset < contentLength) {
				int sendLength = (buffer.length > contentLength - offset) ? contentLength
						- offset
						: buffer.length;// �����һ������buffer��С������ΪcontentLength-
										// offset
				System.arraycopy(data, offset, buffer, 0, sendLength); // ��data�����д�offset��ʼ��sendLength��Ԫ�ؿ�����buffer����
				DatagramPacket packet = new DatagramPacket(buffer, sendLength,
						recvPacket.getAddress(), recvPacket.getPort());
				TimeUnit.MICROSECONDS.sleep(1);
				udpServer.send(packet);
				offset += sendLength;
			}

		} catch (SocketException ex) {
			logger.log(Level.WARNING, "��" + connection.getRemoteSocketAddress()
					+ "ʹ��UDP�����ļ������з�������", ex);
		}
	}

	/**
	 * ��udp�����ļ�����ǰ���Ȱ��ļ��Ļ�����Ϣ��JSON��ʽ�����ͻ���
	 * 
	 * @param bw
	 *            socket�������
	 * @param fileName
	 *            Ҫ������ļ���������������Ϊ""
	 * @param contentLength
	 *            Ҫ������ļ����ȣ�����������Ϊ-1
	 * @param fileExist
	 *            Ҫ������ļ��Ƿ����
	 * @throws IOException
	 *             bw����ͻ���дʱ�׳����쳣
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
			logger.log(Level.WARNING, "��" + connection.getRemoteSocketAddress()
					+ "ʹ��UDP�����ļ�ǰ����Ҫ��Ϣͨ��JSON��ʽ�����ͻ���ʱ��������", e);
		}

	}

}
