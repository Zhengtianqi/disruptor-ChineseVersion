package com.lmax.disruptor.util;

import java.util.concurrent.ThreadFactory;

/**
 * 访问ThreadFactory实例。 所有线程都是使用setDaemon(true)创建的守护线程
 */
public enum DaemonThreadFactory implements ThreadFactory {
	INSTANCE;

	@Override
	public Thread newThread(final Runnable r) {
		Thread t = new Thread(r);
		t.setDaemon(true);
		return t;
	}
}
