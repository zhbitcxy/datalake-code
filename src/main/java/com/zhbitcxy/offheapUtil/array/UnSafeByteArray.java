package com.zhbitcxy.offheapUtil.array;

import com.zhbitcxy.offheapUtil.UnsafeAllocator;

public class UnSafeByteArray {
    long base;
    int capacity;
    static final long SCALE = 1;

    public UnSafeByteArray(int capacity) {
        this.capacity = capacity;
        base = UnsafeAllocator.allocate(capacity * SCALE);
    }

    public byte get(int idx){
        return UnsafeAllocator.get(base, idx);
    }

    public void set(int idx, byte value){
        UnsafeAllocator.set(base, idx, value);
    }

    public void free(){
        UnsafeAllocator.free(base);
    }
}
