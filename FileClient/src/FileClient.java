import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

public class FileClient {

	private static final int PORT = 2021; // 连接端口
	private static final String HOST = "127.0.0.1"; // 连接地址	
	private static final String SAVE_DIR = "D:\\client_save_dir\\";
	private Socket socket = new Socket();

	public FileClient() throws UnknownHostException, IOException {
		socket = new Socket();
		socket.connect(new InetSocketAddress(HOST, PORT));
	}

	public static void main(String[] args) throws UnknownHostException,
			IOException {
		new FileClient().send();
	}

	/**
	 * send implements
	 */
	public void send() {
		try {
			// 客户端输出流，向服务器发消息
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
					socket.getOutputStream()));
			// 客户端输入流，接收服务器消息
			BufferedReader br = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			System.out.println(br.readLine()); // 输出服务器返回的连接消息

			PrintWriter pw = new PrintWriter(bw, true); // 装饰输出流，及时刷新
			Scanner in = new Scanner(System.in); // 接受用户信息
			String command = null;
			while ((command = in.nextLine()) != null) {
				pw.println(command); // 发送给服务器端
				if (command.equals("bye")) {// 退出
					break; 
				}
				String msg = ""; 
				while ((msg = br.readLine()) != null) { // 输出服务器返回的消息
					if (!msg.equals("end")) { //规定的结束标志
						if (!command.startsWith("get ")) {
							System.out.println(msg); 
						} else {
							toDownloadFile(msg);
						}

					} else {
						break;
					}
				}

			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			System.err.println("使用get 命令时信息传输出错!");
			e.printStackTrace();
		} finally {
			if (null != socket) {
				try {
					socket.close(); // 断开连接
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * 处理服务器以json形式传来的文件的信息，来接收文件
	 * 
	 * @param msg 服务器以json形式传来的文件的信息
	 * @throws JSONException
	 */
	private void toDownloadFile(String msg) throws JSONException {
		JSONObject jsonObj = new JSONObject(msg);
		String statue = (String) jsonObj.get("statue");

		if (statue.equals("OK")) { //可以下载
			String fileName = (String) jsonObj.get("file-name");
			long contentLength = (int) jsonObj.get("content-length");
			System.out.println("开始接收文件: " + fileName);
			boolean isSuccess = downloadFileByUDP(SAVE_DIR + fileName, contentLength); //使用udp接收文件
			if (isSuccess) {
				System.out.println("文件接收完毕");
			} else {
				System.out.println("文件接收失败");
			}
		} else {
			System.out.println(statue); //unknown file
		}

	}

	/**
	 * 通过udp来接收服务器传来的文件内容并保存到当前源程序所在目录
	 * 
	 * @param fileName 要接收和保存的文件名
	 * @param contentLength 要接收和保存的文件大小
	 * @return
	 */
	private boolean downloadFileByUDP(String fileName, long contentLength) {

		try (DatagramSocket datagramSocket = new DatagramSocket();
				FileOutputStream fout = new FileOutputStream(fileName, true);) {
			byte[] sendBuff = "start send".getBytes(); 
			DatagramPacket sendPacket = new DatagramPacket(sendBuff,
					sendBuff.length, InetAddress.getByName(HOST), 2020);
			datagramSocket.send(sendPacket); //首先向服务器发送信息，实际是为了向服务器发送自己的地址和udp端口

			int offset = 0; 
			byte[] recvBuffer = new byte[1024];
			DatagramPacket recvPacket = new DatagramPacket(recvBuffer,
					recvBuffer.length);
			File file = new File(fileName);
			if (file.exists()) { //如果当前目录存在同名文件，则删掉同名文件
				file.delete();
			}

			while (offset < contentLength) { //接收文件并保存
				datagramSocket.receive(recvPacket);
				int bytesRead = recvPacket.getLength();
				offset += bytesRead;
				fout.write(recvPacket.getData(), 0, bytesRead);
				fout.flush();
				// System.out.println("已接收"+offset/contentLength*100.0+"%");
			}
		} catch (IOException e) {
			return false; 
		}
		return true;
	}

}
