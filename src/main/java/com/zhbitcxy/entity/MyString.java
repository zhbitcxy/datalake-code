package com.zhbitcxy.entity;

public class MyString {
    private byte[] bytes;
    private int len;

    public MyString() {
        bytes = new byte[4 << 20];
        len = 0;
    }

    public void setLen(int len){
        this.len = len;
    }

    public byte[] getBytes(){
        return bytes;
    }

    public int getLen(){
        return len;
    }
}
