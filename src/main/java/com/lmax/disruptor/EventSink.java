package com.lmax.disruptor;

/**
 * 这个类主要是提供发布事件(就是往队列上放数据)的功能，接口上定义了以各种姿势发布事件的方法。
 * 
 * 1：EventSink接口是用来发布Event的，在发布的同时，调用绑定的Translator来初始化并填充Event。
 * 2：填充Event是通过实现EventTranslator，EventTranslatorOneArg，EventTranslatorTwoArg，EventTranslatorThreeArg，EventTranslatorVararg这些EventTranslator来做的。
 * 3：发布流程：申请下一个序列->申请成功则获取对应槽的Event->利用translator初始化并填充对应槽的Event->发布Event。translator用户实现，用于初始化Event。
 * 
 * @param <E>
 */
public interface EventSink<E> {
	/**
	 * 将事件发布到RingBuffer 它处理声明下一个序列，从RingBuffer获取当前（未初始化）事件并在转换后发布声明的序列。
	 *
	 * @param translator 用户指定的事件的翻译
	 */
	void publishEvent(EventTranslator<E> translator);

	/**
	 * 尝试将事件发布到Ringbuffer 它处理声明下一个序列，从RingBuffer获取当前（未初始化）事件并在转换后发布声明的序列
	 * 如果指定的容量不可用，则返回false
	 *
	 * @param translator 用户指定的事件的翻译
	 * @return 如果值已发布，如果容量不足为false capacity.
	 */
	boolean tryPublishEvent(EventTranslator<E> translator);

	/**
	 * 允许一个用户提供的参数
	 *
	 * @param            <A> 用户提供的参数的类
	 * @param translator 用户指定了事件的翻译
	 * @param arg0       用户提供的参数
	 * @see #publishEvent(EventTranslator)
	 */
	<A> void publishEvent(EventTranslatorOneArg<E, A> translator, A arg0);

	/**
	 * 允许一个用户提供的参数。
	 *
	 * @param            <A> 用户提供的参数的类
	 * @param translator 用户指定的事件的翻译
	 * @param arg0       用户提供的参数。
	 * @return 如果值已发布，如果容量不足为false
	 * @see #tryPublishEvent(EventTranslator)
	 */
	<A> boolean tryPublishEvent(EventTranslatorOneArg<E, A> translator, A arg0);

	/**
	 * 允许两个用户提供的参数
	 *
	 * @param            <A> 用户提供参数的类
	 * @param            <B> 用户提供参数的类
	 * @param translator 用户指定事件的翻译
	 * @param arg0       用户提供的参数
	 * @param arg1       用户提供的参数
	 * @see #publishEvent(EventTranslator)
	 */
	<A, B> void publishEvent(EventTranslatorTwoArg<E, A, B> translator, A arg0, B arg1);

	/**
	 * 允许两个用户提供的参数
	 *
	 * @param            <A> 用户提供参数的类
	 * @param            <B> 用户提供参数的类
	 * @param translator 用户指定事件的翻译
	 * @param arg0       用户提供的参数
	 * @param arg1       用户提供的参数
	 * @return 如果值已发布，如果容量不足为false capacity.
	 * @see #tryPublishEvent(EventTranslator)
	 */
	<A, B> boolean tryPublishEvent(EventTranslatorTwoArg<E, A, B> translator, A arg0, B arg1);

	/**
	 * 允许三个用户提供的参数
	 *
	 * @param            <A> 用户提供参数的类
	 * @param            <B> 用户提供参数的类
	 * @param            <C> 用户提供参数的类
	 * @param translator 用户指定事件的翻译
	 * @param arg0       用户提供的参数
	 * @param arg1       用户提供的参数
	 * @param arg2       用户提供的参数
	 * @see #publishEvent(EventTranslator)
	 */
	<A, B, C> void publishEvent(EventTranslatorThreeArg<E, A, B, C> translator, A arg0, B arg1, C arg2);

	/**
	 * 允许三个用户提供的参数
	 *
	 * @param            <A> 用户提供参数的类
	 * @param            <B> 用户提供参数的类
	 * @param            <C> 用户提供参数的类
	 * @param translator 用户指定事件的翻译
	 * @param arg0       用户提供的参数
	 * @param arg1       用户提供的参数
	 * @param arg2       用户提供的参数
	 * @return 如果值已发布，如果容量不足为false capacity.
	 * @see #publishEvent(EventTranslator)
	 */
	<A, B, C> boolean tryPublishEvent(EventTranslatorThreeArg<E, A, B, C> translator, A arg0, B arg1, C arg2);

	/**
	 * 允许可变数量的用户提供的参数
	 *
	 * @param translator 用户指定了事件的翻译
	 * @param args       用户提供的参数
	 * @see #publishEvent(EventTranslator)
	 */
	void publishEvent(EventTranslatorVararg<E> translator, Object... args);

	/**
	 * 允许可变数量的用户提供的参数
	 *
	 * @param translator 用户指定了事件的翻译
	 * @param args       用户提供的参数
	 * @return 如果值已发布，如果容量不足为false capacity.
	 * @see #publishEvent(EventTranslator)
	 */
	boolean tryPublishEvent(EventTranslatorVararg<E> translator, Object... args);

	/**
	 * <p>
	 * 将多个事件发布到RingBuffer 它处理声明下一个序列，从RingBuffer获取当前（未初始化）事件并在转换后发布声明的序列。
	 *
	 * <p>
	 * 通过此调用，要插入RingBuffer的数据将是一个字段（显式或匿名捕获） 因此，对于要插入RingBuffer的每个值，此调用将需要转换器的实例。
	 * </p>
	 * 
	 * @param translators 用户为每个事件指定的翻译
	 */
	void publishEvents(EventTranslator<E>[] translators);

	/**
	 * <p>
	 * 将多个事件发布到环形缓冲区。 它处理声明下一个序列， 从RingBuffer获取当前（未初始化）事件并在转换后发布声明的序列。
	 * </p>
	 * 
	 * <p>
	 * 通过此调用，要插入RingBuffer的数据将是一个字段（显式或匿名捕获）, 因此，此调用将需要为要插入RingBuffer的每个值的转换器实例。
	 * </p>
	 *
	 * @param translators   用户为每个事件指定了翻译
	 * @param batchStartsAt 批处理中数组的第一个元素
	 * @param batchSize     批次的实际大小
	 */
	void publishEvents(EventTranslator<E>[] translators, int batchStartsAt, int batchSize);

	/**
	 * 尝试将多个事件发布到RingBuffer。 它处理声明的下一个序列，从RingBuffer获取当前（未初始化）事件并在转换后发布声明的序列。
	 * 如果指定的容量不可用，则返回false。
	 *
	 * @param translators 用户指定了事件的翻译
	 * @return 如果值已发布，则为true; 如果容量不足，则为false
	 */
	boolean tryPublishEvents(EventTranslator<E>[] translators);

	/**
	 * 将多个事件发布到RingBuffer 它处理声明下一个序列，从RingBuffer获取当前（未初始化）事件并在转换后发布声明的序列。
	 * 如果指定的容量不可用，则返回false。
	 *
	 * @param translators   用户指定了事件的翻译
	 * @param batchStartsAt 批处理数组中的第一个元素。
	 * @param batchSize     批次的实际大小
	 * @return 如果值已发布，则为true; 如果容量不足，则为false
	 */
	boolean tryPublishEvents(EventTranslator<E>[] translators, int batchStartsAt, int batchSize);

	/**
	 * 每个事件允许一个用户提供参数
	 *
	 * @param            <A> 用户提供的参数的类
	 * @param translator 用户指定的事件的翻译
	 * @param arg0       用户提供的参数
	 * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
	 */
	<A> void publishEvents(EventTranslatorOneArg<E, A> translator, A[] arg0);

	/**
	 * 每个事件允许一个用户提供参数
	 *
	 * @param               <A> 用户提供的参数的类
	 * @param translator    用户为每个事件指定的翻译
	 * @param batchStartsAt 批处理中数组的第一个元素
	 * @param batchSize     批次的实际大小
	 * @param arg0          用户提供的参数数组，每个事件一个元素
	 * @see #publishEvents(EventTranslator[])
	 */
	<A> void publishEvents(EventTranslatorOneArg<E, A> translator, int batchStartsAt, int batchSize, A[] arg0);

	/**
	 * 允许一个用户提供的参数。
	 *
	 * @param            <A> 用户提供的参数的类
	 * @param translator 用户为每个事件指定的翻译
	 * @param arg0       用户提供的参数数组，每个事件一个元素
	 * @return 如果值已发布，则为true;如果容量不足，则为false
	 * @see #tryPublishEvents(com.lmax.disruptor.EventTranslator[])
	 */
	<A> boolean tryPublishEvents(EventTranslatorOneArg<E, A> translator, A[] arg0);

	/**
	 * 允许一个用户提供的参数
	 *
	 * @param               <A> 用户提供的参数的类
	 * @param translator    用户为每个事件指定的翻译
	 * @param batchStartsAt 批处理数组中的第一个元素
	 * @param batchSize     批次的实际大小
	 * @param arg0          用户提供的参数数组，每个事件一个元素
	 * @return 如果值已发布，则为true;如果容量不足，则为false capacity.
	 * @see #tryPublishEvents(EventTranslator[])
	 */
	<A> boolean tryPublishEvents(EventTranslatorOneArg<E, A> translator, int batchStartsAt, int batchSize, A[] arg0);

	/**
	 * 每个事件允许两个用户提供的参数。
	 *
	 * @param            <A> 用户提供的参数的类
	 * @param            <B> 用户提供的参数的类
	 * @param translator 用户指定的事件的翻译
	 * @param arg0       用户提供的参数数组，每个事件一个元素
	 * @param arg1       用户提供的参数数组，每个事件一个元素
	 * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
	 */
	<A, B> void publishEvents(EventTranslatorTwoArg<E, A, B> translator, A[] arg0, B[] arg1);

	/**
	 * 每个事件允许两个用户提供的参数
	 *
	 * @param               <A> 用户提供的参数的类
	 * @param               <B> 用户提供的参数的类
	 * @param translator    用户指定的事件的翻译
	 * @param batchStartsAt 批处理数组中的第一个元素
	 * @param batchSize     批次的实际大小
	 * @param arg0          用户提供的参数数组，每个事件一个元素
	 * @param arg1          用户提供的参数数组，每个事件一个元素
	 * @see #publishEvents(EventTranslator[])
	 */
	<A, B> void publishEvents(EventTranslatorTwoArg<E, A, B> translator, int batchStartsAt, int batchSize, A[] arg0,
			B[] arg1);

	/**
	 * 每个事件允许两个用户提供的参数
	 *
	 * @param            <A> 用户提供的参数的类
	 * @param            <B> 用户提供的参数的类
	 * @param translator 用户指定的事件的翻译
	 * @param arg0       用户提供的参数数组，每个事件一个元素
	 * @param arg1       用户提供的参数数组，每个事件一个元素
	 * @return 如果值已发布，则为true;如果容量不足，则为false capacity.
	 * @see #tryPublishEvents(com.lmax.disruptor.EventTranslator[])
	 */
	<A, B> boolean tryPublishEvents(EventTranslatorTwoArg<E, A, B> translator, A[] arg0, B[] arg1);

	/**
	 * 每个事件允许两个用户提供的参数
	 *
	 * @param               <A> 用户提供的参数的类
	 * @param               <B> 用户提供的参数的类
	 * @param translator    用户指定的每个事件的翻译
	 * @param batchStartsAt 批处理中数组的第一个元素
	 * @param batchSize     批次的实际大小。
	 * @param arg0          用户提供的参数数组，每个事件一个元素
	 * @param arg1          用户提供的参数数组，每个事件一个元素
	 * @return 如果值已发布，则为true;如果容量不足，则为false capacity.
	 * @see #tryPublishEvents(EventTranslator[])
	 */
	<A, B> boolean tryPublishEvents(EventTranslatorTwoArg<E, A, B> translator, int batchStartsAt, int batchSize,
			A[] arg0, B[] arg1);

	/**
	 * 每个事件允许三个用户提供参数
	 *
	 * @param            <A> 用户提供的参数的类
	 * @param            <B> 用户提供的参数的类
	 * @param            <C> 用户提供的参数的类
	 * @param translator 用户指定了事件的翻译
	 * @param arg0       用户提供的参数数组，每个事件一个元素
	 * @param arg1       用户提供的参数数组，每个事件一个元素
	 * @param arg2       用户提供的参数数组，每个事件一个元素
	 * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
	 */
	<A, B, C> void publishEvents(EventTranslatorThreeArg<E, A, B, C> translator, A[] arg0, B[] arg1, C[] arg2);

	/**
	 * 每个事件允许三个用户提供参数
	 *
	 * @param               <A> 用户提供的参数的类
	 * @param               <B> 用户提供的参数的类
	 * @param               <C> 用户提供的参数的类
	 * @param translator    用户指定的事件的翻译
	 * @param batchStartsAt 批处理数组中的第一个元素
	 * @param batchSize     批次中的元素数量
	 * @param arg0          用户提供的参数数组，每个事件一个元素
	 * @param arg1          用户提供的参数数组，每个事件一个元素
	 * @param arg2          用户提供的参数数组，每个事件一个元素
	 * @see #publishEvents(EventTranslator[])
	 */
	<A, B, C> void publishEvents(EventTranslatorThreeArg<E, A, B, C> translator, int batchStartsAt, int batchSize,
			A[] arg0, B[] arg1, C[] arg2);

	/**
	 * 每个事件允许三个用户提供的参数
	 *
	 * @param            <A> 用户提供的参数的类
	 * @param            <B> 用户提供的参数的类
	 * @param            <C> 用户提供的参数的类
	 * @param translator 用户指定的事件的翻译
	 * @param arg0       用户提供的参数数组，每个事件一个元素
	 * @param arg1       用户提供的参数数组，每个事件一个元素
	 * @param arg2       用户提供的参数数组，每个事件一个元素
	 * @return 如果值已发布，则为true;如果容量不足，则为false capacity.
	 * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
	 */
	<A, B, C> boolean tryPublishEvents(EventTranslatorThreeArg<E, A, B, C> translator, A[] arg0, B[] arg1, C[] arg2);

	/**
	 * 每个事件允许三个用户提供的参数
	 *
	 * @param               <A> 用户提供的参数的类
	 * @param               <B> 用户提供的参数的类
	 * @param               <C> 用户提供的参数的类
	 * @param translator    用户指定的事件的翻译
	 * @param batchStartsAt 批处理数组中的第一个元素
	 * @param batchSize     批次中的元素数量
	 * @param arg0          用户提供的参数数组，每个事件一个元素
	 * @param arg1          用户提供的参数数组，每个事件一个元素
	 * @param arg2          用户提供的参数数组，每个事件一个元素
	 * @return 如果值已发布，则为true;如果容量不足，则为false capacity.
	 * @see #publishEvents(EventTranslator[])
	 */
	<A, B, C> boolean tryPublishEvents(EventTranslatorThreeArg<E, A, B, C> translator, int batchStartsAt, int batchSize,
			A[] arg0, B[] arg1, C[] arg2);

	/**
	 * 允许每个事件使用可变数量的用户提供的参数
	 *
	 * @param translator 用户指定的事件的翻译
	 * @param args       用户提供的参数，每个事件一个Object []。
	 * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
	 */
	void publishEvents(EventTranslatorVararg<E> translator, Object[]... args);

	/**
	 * 允许每个事件使用可变数量的用户提供的参数
	 *
	 * @param translator    用户指定的事件的翻译
	 * @param batchStartsAt 批处理数组中的第一个元素
	 * @param batchSize     批次的实际大小
	 * @param args          用户提供的参数，每个事件一个Object []。
	 * @see #publishEvents(EventTranslator[])
	 */
	void publishEvents(EventTranslatorVararg<E> translator, int batchStartsAt, int batchSize, Object[]... args);

	/**
	 * 允许每个事件使用可变数量的用户提供的参数
	 *
	 * @param translator 用户指定了事件的翻译
	 * @param args       用户提供的参数，每个事件一个Object []
	 * @return 如果值已发布，则为true; 如果容量不足，则为false capacity.
	 * @see #publishEvents(com.lmax.disruptor.EventTranslator[])
	 */
	boolean tryPublishEvents(EventTranslatorVararg<E> translator, Object[]... args);

	/**
	 * 允许每个事件使用可变数量的用户提供的参数
	 *
	 * @param translator    用户指定了事件的翻译
	 * @param batchStartsAt 批处理数组中的第一个元素
	 * @param batchSize     批次中的元素数量
	 * @param args          用户提供的参数，每个事件一个Object []
	 * @return 如果值已发布，则为true;如果容量不足，则为false
	 * @see #publishEvents(EventTranslator[])
	 */
	boolean tryPublishEvents(EventTranslatorVararg<E> translator, int batchStartsAt, int batchSize, Object[]... args);

}