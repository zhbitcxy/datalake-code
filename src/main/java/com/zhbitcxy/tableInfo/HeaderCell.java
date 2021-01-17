package com.zhbitcxy.tableInfo;

import com.zhbitcxy.entity.MyIntArray;
import com.zhbitcxy.entity.MyString;
import com.zhbitcxy.offheapUtil.UnsafeAllocator;

import java.nio.ByteBuffer;

public class HeaderCell {
    int[] columnPosIndex;
    //meta
    public int columnType;
    //1整数 2.时间戳 3.短字符串 4.长字符串 5.ip地址 6.时间串 7.手机号码
    private boolean isFixed;
    private boolean isLongText;
    private int eleSize;

    //index
    private boolean hasRange;
    private String maxString;
    private String minString;

    private int maxLen;
    private int minLen;
    private int avgLen;

    private boolean isInMem;    //是否缓存在内存
    private long menAddress;

    public HeaderCell(long address, int[] columnPosIndex, int eleSize) {
        this.columnPosIndex = columnPosIndex;
        menAddress = address;
        this.eleSize = eleSize;

        isInMem = true;
        isFixed = false;
        isLongText = false;
        hasRange = false;
    }

    public String getContent(int idx){
        int strLen = columnPosIndex[idx + 1] - columnPosIndex[idx];
        byte[] bytes = new byte[strLen];
        for (int i = 0 ; i < strLen; i++){
            bytes[i] = UnsafeAllocator.getByte(menAddress + columnPosIndex[idx] + i);
        }
        return new String(bytes);
    }

    public void getAll(byte[] bytePool, MyIntArray posArray){
        posArray.reset();
        int offset = 0;
        for (int idx = 0; idx < eleSize; idx++){
            int strLen = columnPosIndex[idx + 1] - columnPosIndex[idx];
            for (int i = 0 ; i < strLen; i++){
                bytePool[offset + i] = UnsafeAllocator.getByte(menAddress + columnPosIndex[idx] + i);
            }
            offset += strLen;
            posArray.add(strLen);
        }
    }

    public void getContent(int idx, MyString myString){
        int strLen = columnPosIndex[idx + 1] - columnPosIndex[idx];
        byte[] bytes = myString.getBytes();
        for (int i = 0 ; i < strLen; i++){
            bytes[i] = UnsafeAllocator.getByte(menAddress + columnPosIndex[idx] + i);
        }
        myString.setLen(strLen);
    }

    public boolean hasRange() {
        return hasRange;
    }

    public void setInfo(int maxLen, int minLen, int avgLen, boolean isFixed){
        this.maxLen = maxLen;
        this.minLen = minLen;
        this.avgLen = avgLen;
        this.isFixed = isFixed;
    }

    public void setRange(String minString, String maxString){
        this.hasRange = true;
        this.minString = minString;
        this.maxString = maxString;
    }

    public void onMem(long menAddress){
        this.menAddress = menAddress;
        isInMem = true;
    }

    public boolean isFixed() {
        return isFixed;
    }

    public boolean isInMem() {
        return isInMem;
    }

    public long getMenAddress() {
        return menAddress;
    }

    public String getMaxString() {
        return maxString;
    }

    public void setMaxString(String maxString) {
        this.maxString = maxString;
    }

    public String getMinString() {
        return minString;
    }

    public void setMinString(String minString) {
        this.minString = minString;
    }

    public int getEleSize() {
        return eleSize;
    }

}
