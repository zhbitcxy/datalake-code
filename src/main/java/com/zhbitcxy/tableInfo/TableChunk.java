package com.zhbitcxy.tableInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class TableChunk {
    private Header header;

    private int rowNum;

    private String tablePath;

    public TableChunk(String tablePath, int rowNum) {
        this.header = new Header();
        this.tablePath = tablePath;

        this.rowNum = rowNum;
    }

    public String getContent(int id, int columnIdx, FileChannel fc) throws IOException {
        String resStr = "";
//        int startPos = rows[id][columnIdx];
//        int contentLen = rows[id][columnIdx + 1] - rows[id][columnIdx] - 1;
//
//        ByteBuffer byteBuffer = ByteBuffer.allocate(contentLen);
//        byteBuffer.clear();
//
//        fc.position(startPos);
//        fc.read(byteBuffer);
//        byteBuffer.flip();
//
//        byte[] bytes = new byte[contentLen];
//        byteBuffer.get(bytes);
//        resStr = new String(bytes,"UTF-8");

        return resStr;
    }


    public String getTablePath() {
        return tablePath;
    }

    public void addHeaderCell(HeaderCell headerCell){
        header.addHeaderCell(headerCell);
    }

    public Header getHeader() {
        return header;
    }
}
