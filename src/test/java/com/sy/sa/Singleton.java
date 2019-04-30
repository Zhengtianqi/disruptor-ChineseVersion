package com.sy.sa;
/**
*
* @data 2019年4月30日 上午9:33:49
* @author ztq
**/
public class Singleton{
    private static Singleton instance =new Singleton();
    //私有化构造方法
    private Singleton(){}
    public static Singleton getInstance(){
        return instance;
    }
    public static void main(String[] args) {
    	Singleton s= Singleton.getInstance();
    	Singleton t= Singleton.getInstance();
    	Singleton k = new Singleton();
    	System.out.println(k.getInstance());
    	System.out.println(s);
    	System.out.println(t);
	}
}