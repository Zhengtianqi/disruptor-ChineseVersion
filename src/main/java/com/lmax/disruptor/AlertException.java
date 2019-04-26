package com.lmax.disruptor;

/**
 * 一个单例模式的类，用于警告{@link EventProcessor}等待状态更改的{@link SequenceBarrier}。
 * 出于性能原因，它不会填充堆栈跟踪。
 */
@SuppressWarnings("serial")
public final class AlertException extends Exception {
	/**
	 * 预先分配的异常以避免垃圾生成
	 */
	public static final AlertException INSTANCE = new AlertException();

	/**
	 * 私有构造函数，因此只存在一个实例。
	 */
	private AlertException() {
	}

	/**
	 * 由于性能原因，已覆盖，堆栈跟踪未填充导致异常。
	 * 
	 * @return this instance.
	 */
	@Override
	public Throwable fillInStackTrace() {
		return this;
	}
}
