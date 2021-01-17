package com.zhbitcxy.offheapUtil;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeAllocator {
    static final Unsafe unsafe;

    static
    {
        try
        {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (sun.misc.Unsafe) field.get(null);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    public static long allocate(long size)
    {
        try
        {
            return unsafe.allocateMemory(size);
        }
        catch (OutOfMemoryError oom)
        {
            return 0L;
        }
    }

    public static void set(long address, long i, int value, long scale){
        unsafe.putInt(address + i * scale, value);
    }

    public static int get(long address, long i, long scale){
        int b0 = unsafe.getInt(address + i * scale);
        return b0;
    }

    public static void setInt(long address, int value){
        unsafe.putInt(address, value);
    }

    public static int getInt(long address){
        int b0 = unsafe.getInt(address);
        return b0;
    }

    public static void setByte(long address, byte value){
        unsafe.putByte(address, value);
    }

    public static byte getByte(long address){
        byte b0 = unsafe.getByte(address);
        return b0;
    }

    public static void set(long address, long i, byte value){
        unsafe.putByte(address + i, value);
    }

    public static byte get(long address, long i){
        return unsafe.getByte(address + i);
    }

    public static void free(long peer)
    {
        unsafe.freeMemory(peer);
    }
}
