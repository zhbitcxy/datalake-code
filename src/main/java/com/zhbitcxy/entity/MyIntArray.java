package com.zhbitcxy.entity;

public class MyIntArray {
    private int[] array;
    private int cursor;

    public MyIntArray() {
        array = new int[1 << 20];
        cursor = 0;
    }

    public void reset(){
        cursor = 0;
    }

    public void add(int value){
        array[cursor] = value;
        cursor++;
    }

    public int[] getArray() {
        return array;
    }

    public int getCursor() {
        return cursor;
    }

    public void setCursor(int cursor) {
        this.cursor = cursor;
    }
}
