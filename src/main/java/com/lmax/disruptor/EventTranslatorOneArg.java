package com.lmax.disruptor;

/**
 * 实现将另一个数据表示转换为{@link RingBuffer}声明的事件
 * 
 * @param <T> 事件实现存储数据以便在事件的交换或并行协调期间进行共享
 * @see EventTranslator
 */
public interface EventTranslatorOneArg<T, A>
{
    /**
     * 将数据表示转换为在给定事件中设置的字段
     *
     * @param event    应该将数据翻译成哪个
     * @param sequence 分配给事件的
     * @param arg0     第一个用户为翻译者指定了参数
     */
    void translateTo(T event, long sequence, A arg0);
}
