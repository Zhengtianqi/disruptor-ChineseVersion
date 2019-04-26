package com.lmax.disruptor;

/**
 * 
 * EventSequencer扩展了Sequenced，提供了一些序列功能；同时扩展了DataProvider，提供了按序列值来获取数据的功能。
 * 
 * @param <T>
 */
public interface EventSequencer<T> extends DataProvider<T>, Sequenced {

}
