package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 *	没有{@link EventProcessor}的操作版本,只跟踪{@link Sequence}。
 * <p>
 *	这在测试中或从发布者预填充{@link RingBuffer}时非常有用。
 * </p>
 */
public final class NoOpEventProcessor implements EventProcessor {
	private final SequencerFollowingSequence sequence;
	private final AtomicBoolean running = new AtomicBoolean(false);

	/**
	 *	构建一个跟踪{@link Sequence}对象的{@link EventProcessor}。
	 *
	 * @param 根据的序列
	 */
	public NoOpEventProcessor(final RingBuffer<?> sequencer) {
		sequence = new SequencerFollowingSequence(sequencer);
	}

	@Override
	public Sequence getSequence() {
		return sequence;
	}

	@Override
	public void halt() {
		running.set(false);
	}

	@Override
	public boolean isRunning() {
		return running.get();
	}

	@Override
	public void run() {
		if (!running.compareAndSet(false, true)) {
			throw new IllegalStateException("Thread is already running");
		}
	}

	/**
	 *	跟随（通过包装）另一个序列
	 */
	private static final class SequencerFollowingSequence extends Sequence {
		private final RingBuffer<?> sequencer;

		private SequencerFollowingSequence(final RingBuffer<?> sequencer) {
			super(Sequencer.INITIAL_CURSOR_VALUE);
			this.sequencer = sequencer;
		}

		@Override
		public long get() {
			return sequencer.getCursor();
		}
	}
}
