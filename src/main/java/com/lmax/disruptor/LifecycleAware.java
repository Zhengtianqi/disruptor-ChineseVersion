package com.lmax.disruptor;

/**
 * 在{@link EventHandler}中实现此接口，以便在{@link BatchEventProcessor}的线程启动和关闭时得到通知。
 */
public interface LifecycleAware {
	/**
	 * 在第一个事件可用之前，在线程启动时调用一次。
	 */
	void onStart();

	/**
	 * <p>
	 * 在线程关闭之前调用一次
	 * </p>
	 * <p>
	 * 在调用此方法之前，序列事件处理已经停止。此消息后不会处理任何事件。
	 * </p>
	 */
	void onShutdown();
}
