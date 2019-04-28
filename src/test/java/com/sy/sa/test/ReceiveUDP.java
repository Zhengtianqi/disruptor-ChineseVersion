package com.sy.sa.test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * @Author: zhengtianqi
 * @Date: 2019/4/27 10:23
 */
public class ReceiveUDP {
	public static void main(String[] args) throws Exception {
		int listenPort = 9999;
		byte[] buf = new byte[1024];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		@SuppressWarnings("resource")
		DatagramSocket responseSocket = new DatagramSocket(listenPort);
		System.out.println("Server started, Listen port: " + listenPort);
		while (true) {
			responseSocket.receive(packet);
			String rcvd = "Received " + new String(packet.getData(), 0, packet.getLength()) + " from address: "
					+ packet.getSocketAddress();
			System.out.println(rcvd);
			// Send a response packet to sender
			String backData = "DCBA";
			byte[] data = backData.getBytes();
			System.out.println("Send " + backData + " to " + packet.getSocketAddress());
			DatagramPacket backPacket = new DatagramPacket(data, 0, data.length, packet.getSocketAddress());
			responseSocket.send(backPacket);
		}
	}
}
