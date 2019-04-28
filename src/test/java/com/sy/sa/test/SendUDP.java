package com.sy.sa.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @Author: zhengtianqi
 * @Date: 2019/4/27 10:16
 */
public class SendUDP {
	public static void main(String[] args) throws Exception {
		// Use this port to send broadcast packet
		@SuppressWarnings("resource")
		final DatagramSocket detectSocket = new DatagramSocket(8888);
		// Send packet thread
		new Thread(new Runnable() {
			@Override
			public void run() {
				System.out.println("Send thread started.");
				while (true) {
					try {
						byte[] buf = new byte[1024];
						int packetPort = 9999;
						// Broadcast address
						InetAddress hostAddress = InetAddress.getByName("192.168.184.255");
						BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
						String outMessage = stdin.readLine();
						if (outMessage.equals("bye"))
							break;
						buf = outMessage.getBytes();
						System.out.println("Send " + outMessage + " to " + hostAddress);
						// Send packet to hostAddress:9999, server that listen
						// 9999 would reply this packet
						DatagramPacket out = new DatagramPacket(buf, buf.length, hostAddress, packetPort);
						detectSocket.send(out);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
		// Receive packet thread.
		new Thread(new Runnable() {
			@Override
			public void run() {
				System.out.println("Receive thread started.");
				while (true) {
					byte[] buf = new byte[1024];
					DatagramPacket packet = new DatagramPacket(buf, buf.length);
					try {
						detectSocket.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					String rcvd = "Received from " + packet.getSocketAddress() + ", Data="
							+ new String(packet.getData(), 0, packet.getLength());
					System.out.println(rcvd);
				}
			}
		}).start();
	}
}
