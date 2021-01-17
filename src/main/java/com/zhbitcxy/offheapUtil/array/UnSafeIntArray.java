package com.zhbitcxy.offheapUtil.array;

import com.zhbitcxy.offheapUtil.UnsafeAllocator;

public class UnSafeIntArray {
    long base;
    int capacity;
    static final long SCALE = 4;

    public UnSafeIntArray(int capacity) {
        this.capacity = capacity;
        base = UnsafeAllocator.allocate(capacity * SCALE);
    }

    public int get(int idx){
        return UnsafeAllocator.get(base, idx, SCALE);
    }

    public void set(int idx, int value){
        UnsafeAllocator.set(base, idx, value, SCALE);
    }

    public void free(){
        UnsafeAllocator.free(base);
    }
}
