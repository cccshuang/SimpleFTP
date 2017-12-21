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

	private static final int PORT = 2021; // ���Ӷ˿�
	private static final String HOST = "127.0.0.1"; // ���ӵ�ַ	
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
			// �ͻ���������������������Ϣ
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
					socket.getOutputStream()));
			// �ͻ��������������շ�������Ϣ
			BufferedReader br = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			System.out.println(br.readLine()); // ������������ص�������Ϣ

			PrintWriter pw = new PrintWriter(bw, true); // װ�����������ʱˢ��
			Scanner in = new Scanner(System.in); // �����û���Ϣ
			String command = null;
			while ((command = in.nextLine()) != null) {
				pw.println(command); // ���͸���������
				if (command.equals("bye")) {// �˳�
					break; 
				}
				String msg = ""; 
				while ((msg = br.readLine()) != null) { // ������������ص���Ϣ
					if (!msg.equals("end")) { //�涨�Ľ�����־
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
			System.err.println("ʹ��get ����ʱ��Ϣ�������!");
			e.printStackTrace();
		} finally {
			if (null != socket) {
				try {
					socket.close(); // �Ͽ�����
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * �����������json��ʽ�������ļ�����Ϣ���������ļ�
	 * 
	 * @param msg ��������json��ʽ�������ļ�����Ϣ
	 * @throws JSONException
	 */
	private void toDownloadFile(String msg) throws JSONException {
		JSONObject jsonObj = new JSONObject(msg);
		String statue = (String) jsonObj.get("statue");

		if (statue.equals("OK")) { //��������
			String fileName = (String) jsonObj.get("file-name");
			long contentLength = (int) jsonObj.get("content-length");
			System.out.println("��ʼ�����ļ�: " + fileName);
			boolean isSuccess = downloadFileByUDP(SAVE_DIR + fileName, contentLength); //ʹ��udp�����ļ�
			if (isSuccess) {
				System.out.println("�ļ��������");
			} else {
				System.out.println("�ļ�����ʧ��");
			}
		} else {
			System.out.println(statue); //unknown file
		}

	}

	/**
	 * ͨ��udp�����շ������������ļ����ݲ����浽��ǰԴ��������Ŀ¼
	 * 
	 * @param fileName Ҫ���պͱ�����ļ���
	 * @param contentLength Ҫ���պͱ�����ļ���С
	 * @return
	 */
	private boolean downloadFileByUDP(String fileName, long contentLength) {

		try (DatagramSocket datagramSocket = new DatagramSocket();
				FileOutputStream fout = new FileOutputStream(fileName, true);) {
			byte[] sendBuff = "start send".getBytes(); 
			DatagramPacket sendPacket = new DatagramPacket(sendBuff,
					sendBuff.length, InetAddress.getByName(HOST), 2020);
			datagramSocket.send(sendPacket); //�����������������Ϣ��ʵ����Ϊ��������������Լ��ĵ�ַ��udp�˿�

			int offset = 0; 
			byte[] recvBuffer = new byte[1024];
			DatagramPacket recvPacket = new DatagramPacket(recvBuffer,
					recvBuffer.length);
			File file = new File(fileName);
			if (file.exists()) { //�����ǰĿ¼����ͬ���ļ�����ɾ��ͬ���ļ�
				file.delete();
			}

			while (offset < contentLength) { //�����ļ�������
				datagramSocket.receive(recvPacket);
				int bytesRead = recvPacket.getLength();
				offset += bytesRead;
				fout.write(recvPacket.getData(), 0, bytesRead);
				fout.flush();
				// System.out.println("�ѽ���"+offset/contentLength*100.0+"%");
			}
		} catch (IOException e) {
			return false; 
		}
		return true;
	}

}
