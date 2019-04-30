package com.lmax.disruptor;

/**
 * {@link BatchEventProcessor}事件处理周期中未捕获异常的回调处理程序
 */
public interface ExceptionHandler<T> {
	/**
	 * <p>
	 * 处理事件时处理未捕获异常的策略
	 * </p>
	 * <p>
	 * 如果策略希望终止{@link BatchEventProcessor}的进一步处理，那么它应该抛出{@link TimeoutException}。
	 * </p>
	 *
	 * @param ex       从{@link EventHandler}传播的异常。
	 * @param sequence 导致异常的事件
	 * @param event    异常发生时进行处理，这可以为null。
	 */
	void handleEventException(Throwable ex, long sequence, T event);

	/**
	 * 回调以在{@link LifecycleAware#onStart()}期间通知异常
	 *
	 * @param ex 在启动过程中抛出
	 */
	void handleOnStartException(Throwable ex);

	/**
	 * 回调以在{@link LifecycleAware#onShutdown()}期间通知异常
	 *
	 * @param ex 在关闭过程中抛出
	 */
	void handleOnShutdownException(Throwable ex);
}
