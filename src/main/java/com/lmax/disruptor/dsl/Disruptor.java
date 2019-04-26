package com.lmax.disruptor.dsl;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.EventTranslatorThreeArg;
import com.lmax.disruptor.EventTranslatorTwoArg;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.WorkerPool;
import com.lmax.disruptor.util.Util;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 
 * 两个消费者必须按顺序的时候，事件消费的简单示例：
 * 
 * <pre>
 * <code>Disruptor&lt;MyEvent&gt; disruptor = new Disruptor&lt;MyEvent&gt;(MyEvent.FACTORY, 32, Executors.newCachedThreadPool());
 * EventHandler&lt;MyEvent&gt; handler1 = new EventHandler&lt;MyEvent&gt;() { ... };
 * EventHandler&lt;MyEvent&gt; handler2 = new EventHandler&lt;MyEvent&gt;() { ... };
 * disruptor.handleEventsWith(handler1);
 * disruptor.after(handler1).handleEventsWith(handler2);
 *
 * RingBuffer ringBuffer = disruptor.start();</code>
 * </pre>
 *
 * @param <T> 所使用的数据类型
 */
public class Disruptor<T> {
	// 事件队列，绝大多数功能都委托给ringBuffer处理
	private final RingBuffer<T> ringBuffer;
	// 用于执行消费者的执行器
	private final Executor executor;
	// 消费者仓库，就是消费者的集合
	private final ConsumerRepository<T> consumerRepository = new ConsumerRepository<>();
	// 启动时检查，只能启动一次
	private final AtomicBoolean started = new AtomicBoolean(false);
	// 异常消费者
	private ExceptionHandler<? super T> exceptionHandler = new ExceptionHandlerWrapper<>();

	/**
	 * 通过构造方法，可以对内部的环和执行器进行初始化。
	 * 默认参数是{@link com.lmax.disruptor.BlockingWaitStrategy}和{@link ProducerType}
	 *
	 * @deprecated 使用{@link ThreadFactory}而不是{@link Executor}，作为ThreadFactory能够在无法构造线程来运行生产者时报告错误。
	 * @param eventFactory   创建事件events
	 * @param ringBufferSize 环的大小
	 * @param executor       用{@link Executor}来执行消费者
	 */
	@Deprecated
	public Disruptor(final EventFactory<T> eventFactory, final int ringBufferSize, final Executor executor) {
		this(RingBuffer.createMultiProducer(eventFactory, ringBufferSize), executor);
	}

	/**
	 * 创建一个新的Disruptor
	 * 
	 * @deprecated 使用{@link ThreadFactory}而不是{@link Executor}，作为ThreadFactory能够在无法构造线程来运行生产者时报告错误。
	 * @param eventFactory   创建事件events
	 * @param ringBufferSize 大小必须是2的幂
	 * @param executor       执行消费者
	 * @param producerType   声明生产者的类型
	 * @param waitStrategy   声明等待策略
	 */
	@Deprecated
	public Disruptor(final EventFactory<T> eventFactory, final int ringBufferSize, final Executor executor,
			final ProducerType producerType, final WaitStrategy waitStrategy) {
		this(RingBuffer.create(producerType, eventFactory, ringBufferSize, waitStrategy), executor);
	}

	/**
	 * 创建一个新的Disruptor
	 * 将默认为{@link com.lmax.disruptor.BlockingWaitStrategy}和{@link ProducerType}
	 *
	 * @param eventFactory   创建事件events
	 * @param ringBufferSize 环的大小
	 * @param threadFactory  {@link ThreadFactory}为处理事件创建线程
	 */
	public Disruptor(final EventFactory<T> eventFactory, final int ringBufferSize, final ThreadFactory threadFactory) {
		this(RingBuffer.createMultiProducer(eventFactory, ringBufferSize), new BasicExecutor(threadFactory));
	}

	/**
	 * 创建一个新的Disruptor
	 *
	 * @param eventFactory   创建事件events
	 * @param ringBufferSize RingBuffer的大小必须是2的幂
	 * @param threadFactory  {@link ThreadFactory}来执行消费者
	 * @param producerType   声明生产者的类型
	 * @param waitStrategy   声明等待策略
	 */
	public Disruptor(final EventFactory<T> eventFactory, final int ringBufferSize, final ThreadFactory threadFactory,
			final ProducerType producerType, final WaitStrategy waitStrategy) {
		this(RingBuffer.create(producerType, eventFactory, ringBufferSize, waitStrategy),
				new BasicExecutor(threadFactory));
	}

	/**
	 * 私有构造函数
	 */
	private Disruptor(final RingBuffer<T> ringBuffer, final Executor executor) {
		this.ringBuffer = ringBuffer;
		this.executor = executor;
	}

	/**
	 * barrierSequences是eventHandlers的前置事件处理关卡，是用来保证事件消费的时序性的关键
	 * 这个方法可以看成为链(chain)的起点. 如果A必须在B之前处理事件，例如
	 * 
	 * <pre>
	 * <code>dw.handleEventsWith(A).then(B);</code>
	 * </pre>
	 * 
	 * 此调用是附加的，但通常只应在设置Disruptor实例时调用一次
	 *
	 * @param handlers 处理事件
	 * @return 可用于链的依赖，{@link EventHandlerGroup}
	 */
	@SuppressWarnings("varargs")
	@SafeVarargs
	public final EventHandlerGroup<T> handleEventsWith(final EventHandler<? super T>... handlers) {
		return createEventProcessors(new Sequence[0], handlers);
	}

	/**
	 * 设置自定义消费者以处理环事件。 当调用{@link #start()}时，Disruptor将自动启动这些消费者该方法可以用作链的起点 例如：
	 * 如果A必须在B之前处理事件，例如
	 * 
	 * <pre>
	 * <code>dw.handleEventsWith(A).then(B);</code>
	 * </pre>
	 *
	 * 这是链的开始， 消费者工厂会传递一个空的序列,所以在此案例中是不必须的.
	 * 提供这个方法，为了与已知{@link EventHandlerGroup#handleEventsWith(EventProcessorFactory...)}
	 * 和 {@link EventHandlerGroup#then(EventProcessorFactory...)} 有阻塞序列的提供
	 * 此调用是附加的，但通常只应在设置Disruptor实例时调用一次
	 *
	 * @param eventProcessorFactories 用于创建消费者
	 * @return {@link EventHandlerGroup} 可用于链的依赖
	 */
	@SafeVarargs
	public final EventHandlerGroup<T> handleEventsWith(final EventProcessorFactory<T>... eventProcessorFactories) {
		final Sequence[] barrierSequences = new Sequence[0];
		return createEventProcessors(barrierSequences, eventProcessorFactories);
	}

	/**
	 * 自定义消费者以处理来自环的事件 当{@link #start()}被调用的时候，自动启动processors 该方法可以用作链的起点。例如
	 * 如果A必须在B之前处理事件，例如
	 * 
	 * <pre>
	 * <code>dw.handleEventsWith(A).then(B);</code>
	 * </pre>
	 *
	 * @param processors 处理事件的消费者
	 * @return {@link EventHandlerGroup} 可用于链的依赖
	 */
	public EventHandlerGroup<T> handleEventsWith(final EventProcessor... processors) {
		for (final EventProcessor processor : processors) {
			consumerRepository.add(processor);
		}

		final Sequence[] sequences = new Sequence[processors.length];
		for (int i = 0; i < processors.length; i++) {
			sequences[i] = processors[i].getSequence();
		}

		ringBuffer.addGatingSequences(sequences);

		return new EventHandlerGroup<>(this, consumerRepository, Util.getSequencesFor(processors));
	}

	/**
	 * handleEventsWithWorkerPool内部会创建WorkerPool 设置{@link WorkerPool}以将事件分发到工作处理线程池
	 * 每个事件仅由其中一个消费者处理，当调用{@link #start()}时，Disruptor将自动启动此消费者。
	 *
	 * @param workHandlers 要处理的事件
	 * @return {@link EventHandlerGroup} 可用于链的依赖
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public final EventHandlerGroup<T> handleEventsWithWorkerPool(final WorkHandler<T>... workHandlers) {
		return createWorkerPool(new Sequence[0], workHandlers);
	}

	/**
	 * 事件消费异常处理。请注意，只有在调用此方法后，涉及的消费者才会使用异常消费者
	 *
	 * @param exceptionHandler 用于未来{@link EventProcessor}的异常消费者。
	 * @deprecated 此方法仅适用于将来的消费者。 使用setDefaultExceptionHandler，它适用于现有和新的消费者。
	 */
	public void handleExceptionsWith(final ExceptionHandler<? super T> exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	/**
	 * 指定用于此Disruptor创建的消费者和工作池的异常处理 异常处理将由此Disruptor实例创建的，现有和未来事件处理，和工作池使用
	 * 
	 * @param exceptionHandler 要使用的异常处理
	 */
	@SuppressWarnings("unchecked")
	public void setDefaultExceptionHandler(final ExceptionHandler<? super T> exceptionHandler) {
		checkNotStarted();
		if (!(this.exceptionHandler instanceof ExceptionHandlerWrapper)) {
			throw new IllegalStateException("setDefaultExceptionHandler can not be used after handleExceptionsWith");
		}
		((ExceptionHandlerWrapper<T>) this.exceptionHandler).switchTo(exceptionHandler);
	}

	/**
	 * 覆盖特定处理的默认异常处理
	 * 
	 * <pre>
	 * disruptorWizard.handleExceptionsIn(eventHandler).with(exceptionHandler);
	 * </pre>
	 *
	 * @param eventHandler 为其设置不同异常的事件处理
	 * @return an ExceptionHandlerSetting dsl object - 打算通过链接方法调用来使用
	 */
	public ExceptionHandlerSetting<T> handleExceptionsFor(final EventHandler<T> eventHandler) {
		return new ExceptionHandlerSetting<>(eventHandler, consumerRepository);
	}

	/**
	 * <p>
	 * 创建一组时间处理作为依赖 如果A必须在B之前处理事件，例如
	 * 
	 * <pre>
	 * <code>dw.after(A).handleEventsWith(B);</code>
	 * </pre>
	 * 
	 * @param handlers 事件处理,之前使用{@link #handleEventsWith(com.lmax.disruptor.EventHandler[])}进行设置,这将构成后续消费者或消费者的栏栅
	 * 
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public final EventHandlerGroup<T> after(final EventHandler<T>... handlers) {
		final Sequence[] sequences = new Sequence[handlers.length];
		for (int i = 0, handlersLength = handlers.length; i < handlersLength; i++) {
			sequences[i] = consumerRepository.getSequenceFor(handlers[i]);
		}

		return new EventHandlerGroup<>(this, consumerRepository, sequences);
	}

	/**
	 * 创建一组消费者以用作依赖项。
	 * 
	 * @param processors 消费者,
	 *                   以前用{@link #handleEventsWith(com.lmax.disruptor.EventProcessor...)}设置的消费者，这将成为后续消费者或消费者的栏栅。
	 * 
	 * @return 一个{@link EventHandlerGroup}，可用于在指定的消费者上设置{@link SequenceBarrier}
	 * @see #after(com.lmax.disruptor.EventHandler[])
	 */
	public EventHandlerGroup<T> after(final EventProcessor... processors) {
		for (final EventProcessor processor : processors) {
			consumerRepository.add(processor);
		}

		return new EventHandlerGroup<>(this, consumerRepository, Util.getSequencesFor(processors));
	}

	/**
	 * 将事件发布到环
	 *
	 * @param eventTranslator 将数据加载到事件（翻译器）
	 */
	public void publishEvent(final EventTranslator<T> eventTranslator) {
		ringBuffer.publishEvent(eventTranslator);
	}

	/**
	 * 将事件发布到环
	 *
	 * @param                 <A> 用户提供的参数的类
	 * @param eventTranslator 将数据加载到事件中（翻译器）
	 * @param arg             加载到事件中的单个参数
	 */
	public <A> void publishEvent(final EventTranslatorOneArg<T, A> eventTranslator, final A arg) {
		ringBuffer.publishEvent(eventTranslator, arg);
	}

	/**
	 * 将一批事件发布到环
	 *
	 * @param                 <A> 用户提供的参数的类
	 * @param eventTranslator 将数据加载到事件中（翻译器）
	 * @param arg             要加载到事件中的数组（每一个事件）
	 */
	public <A> void publishEvents(final EventTranslatorOneArg<T, A> eventTranslator, final A[] arg) {
		ringBuffer.publishEvents(eventTranslator, arg);
	}

	/**
	 * 将事件发布到环
	 *
	 * @param                 <A> 用户提供的参数的类
	 * @param                 <B> 用户提供的参数的类
	 * @param eventTranslator 将数据加载到事件（翻译器）
	 * @param arg0            加载到事件中的第一个参数
	 * @param arg1            加载到事件中的第二个参数
	 */
	public <A, B> void publishEvent(final EventTranslatorTwoArg<T, A, B> eventTranslator, final A arg0, final B arg1) {
		ringBuffer.publishEvent(eventTranslator, arg0, arg1);
	}

	/**
	 * 将事件发布到环
	 * 
	 * @param eventTranslator 将数据加载到事件中（翻译器）
	 * @param                 <A> 用户提供的参数的类
	 * @param                 <B> 用户提供的参数的类
	 * @param                 <C> 用户提供的参数的类
	 * @param arg0            加载到事件中的第一个参数
	 * @param arg1            加载到事件中的第二个参数
	 * @param arg2            加载到事件中的第三个参数
	 */
	public <A, B, C> void publishEvent(final EventTranslatorThreeArg<T, A, B, C> eventTranslator, final A arg0,
			final B arg1, final C arg2) {
		ringBuffer.publishEvent(eventTranslator, arg0, arg1, arg2);
	}

	/**
	 * 启动过程中会将事件处理者的序列设置为RingBuffer的追踪序列 设置RingBuffer以防止覆盖最慢的消费者尚未处理的任何entry。
	 * 添加所有消费者后，只能调用一次此方法。
	 *
	 * @return 配置完成的RingBuffer.
	 */
	public RingBuffer<T> start() {
		checkOnlyStartedOnce();
		for (final ConsumerInfo consumerInfo : consumerRepository) {
			consumerInfo.start(executor);
		}

		return ringBuffer;
	}

	/**
	 * 在通过当前disruptor创建的所有消费者上调用{@link com.lmax.disruptor.EventProcessor#halt()}
	 */
	public void halt() {
		for (final ConsumerInfo consumerInfo : consumerRepository) {
			consumerInfo.halt();
		}
	}

	/**
	 * 等待所有能处理的事件都处理完了，再定制事件处理者，有超时选项。 此方法不会关闭executor，也不会等待消费者线程的最终终止。
	 */
	public void shutdown() {
		try {
			shutdown(-1, TimeUnit.MILLISECONDS);
		} catch (final TimeoutException e) {
			exceptionHandler.handleOnShutdownException(e);
		}
	}

	/**
	 * 等待所有消费者当前处理所有事件，然后停止消费者。 此方法不会关闭executor，也不会等待消费者线程的最终终止。
	 *
	 * @param timeout  等待处理所有事件的时间量。-1时将给出无限超时
	 * @param timeUnit 指定timeOut的单位
	 * @throws TimeoutException 如果在关闭完成之前发生超时
	 */
	public void shutdown(final long timeout, final TimeUnit timeUnit) throws TimeoutException {
		final long timeOutAt = System.currentTimeMillis() + timeUnit.toMillis(timeout);
		while (hasBacklog()) {
			if (timeout >= 0 && System.currentTimeMillis() > timeOutAt) {
				throw TimeoutException.INSTANCE;
			}
			// Busy spin
		}
		halt();
	}

	/**
	 * 如果{@link BatchEventProcessor}的行为不合适，这对于创建自定义消费者很有用
	 * 
	 * @return 获取当前使用的RingBuffer
	 */
	public RingBuffer<T> getRingBuffer() {
		return ringBuffer;
	}

	/**
	 * 获取指示已发布序列的游标值
	 *
	 * @return 已发布事件的游标值。
	 */
	public long getCursor() {
		return ringBuffer.getCursor();
	}

	/**
	 * entries的容量
	 *
	 * @return RingBuffer的大小.
	 * @see com.lmax.disruptor.Sequencer#getBufferSize()
	 */
	public long getBufferSize() {
		return ringBuffer.getBufferSize();
	}

	/**
	 * 获取RingBuffer中给定序列的事件
	 *
	 * @param sequence 要获取事件所给定的序列
	 * @return RingBuffer中给定序列的事件
	 * @see RingBuffer#get(long)
	 */
	public T get(final long sequence) {
		return ringBuffer.get(sequence);
	}

	/**
	 * 获取特定消费者使用的{@link SequenceBarrier} 请注意，{@link SequenceBarrier}可能由多个消费者共享。
	 *
	 * @param handler 获得栏栅的消费者。
	 * @return 消费者使用的SequenceBarrier。
	 */
	public SequenceBarrier getBarrierFor(final EventHandler<T> handler) {
		return consumerRepository.getBarrierFor(handler);
	}

	/**
	 * 获取指定消费者的序列值
	 *
	 * @param b1 通过eventHandler来获取序列
	 * @return 事件的序列
	 */
	public long getSequenceValueFor(final EventHandler<T> b1) {
		return consumerRepository.getSequenceFor(b1).get();
	}

	/**
	 * 确认所有消费者是否已使用所有消息
	 */
	private boolean hasBacklog() {
		final long cursor = ringBuffer.getCursor();
		for (final Sequence consumer : consumerRepository.getLastSequenceInChain(false)) {
			if (cursor > consumer.get()) {
				return true;
			}
		}
		return false;
	}

	EventHandlerGroup<T> createEventProcessors(final Sequence[] barrierSequences,
			final EventHandler<? super T>[] eventHandlers) {
		checkNotStarted();

		final Sequence[] processorSequences = new Sequence[eventHandlers.length];
		final SequenceBarrier barrier = ringBuffer.newBarrier(barrierSequences);

		for (int i = 0, eventHandlersLength = eventHandlers.length; i < eventHandlersLength; i++) {
			final EventHandler<? super T> eventHandler = eventHandlers[i];

			final BatchEventProcessor<T> batchEventProcessor = new BatchEventProcessor<>(ringBuffer, barrier,
					eventHandler);

			if (exceptionHandler != null) {
				batchEventProcessor.setExceptionHandler(exceptionHandler);
			}

			consumerRepository.add(batchEventProcessor, eventHandler, barrier);
			processorSequences[i] = batchEventProcessor.getSequence();
		}

		updateGatingSequencesForNextInChain(barrierSequences, processorSequences);

		return new EventHandlerGroup<>(this, consumerRepository, processorSequences);
	}

	private void updateGatingSequencesForNextInChain(final Sequence[] barrierSequences,
			final Sequence[] processorSequences) {
		if (processorSequences.length > 0) {
			ringBuffer.addGatingSequences(processorSequences);
			for (final Sequence barrierSequence : barrierSequences) {
				ringBuffer.removeGatingSequence(barrierSequence);
			}
			consumerRepository.unMarkEventProcessorsAsEndOfChain(barrierSequences);
		}
	}

	EventHandlerGroup<T> createEventProcessors(final Sequence[] barrierSequences,
			final EventProcessorFactory<T>[] processorFactories) {
		final EventProcessor[] eventProcessors = new EventProcessor[processorFactories.length];
		for (int i = 0; i < processorFactories.length; i++) {
			eventProcessors[i] = processorFactories[i].createEventProcessor(ringBuffer, barrierSequences);
		}

		return handleEventsWith(eventProcessors);
	}

	EventHandlerGroup<T> createWorkerPool(final Sequence[] barrierSequences,
			final WorkHandler<? super T>[] workHandlers) {
		final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier(barrierSequences);
		final WorkerPool<T> workerPool = new WorkerPool<>(ringBuffer, sequenceBarrier, exceptionHandler, workHandlers);

		consumerRepository.add(workerPool, sequenceBarrier);

		final Sequence[] workerSequences = workerPool.getWorkerSequences();

		updateGatingSequencesForNextInChain(barrierSequences, workerSequences);

		return new EventHandlerGroup<>(this, consumerRepository, workerSequences);
	}

	private void checkNotStarted() {
		if (started.get()) {
			throw new IllegalStateException("All event handlers must be added before calling starts.");
		}
	}

	private void checkOnlyStartedOnce() {
		if (!started.compareAndSet(false, true)) {
			throw new IllegalStateException("Disruptor.start() must only be called once.");
		}
	}

	@Override
	public String toString() {
		return "Disruptor{" + "ringBuffer=" + ringBuffer + ", started=" + started + ", executor=" + executor + '}';
	}
}
