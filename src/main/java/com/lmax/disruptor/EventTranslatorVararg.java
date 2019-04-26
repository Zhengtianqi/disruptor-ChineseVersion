package com.lmax.disruptor;

/**
 * 实现将另一个数据表示转换为{@link RingBuffer}声明的事件
 * 
 * @param <T> 事件实现存储数据以便在事件的交换或并行协调期间进行共享
 * 
 * @see EventTranslator
 */
public interface EventTranslatorVararg<T>
{
    /**
     * 将数据表示转换为在给定事件中设置的字段
     *
     * @param event    应该将数据翻译成什么事件
     * @param sequence 事件指定的序列
     * @param args     用户参数数组
     */
    void translateTo(T event, long sequence, Object... args);
}
