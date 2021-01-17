package com.zhbitcxy.tableInfo;

import com.zhbitcxy.index.PartitionIndex;

import java.util.ArrayList;
import java.util.List;

public class Table {
    private int tableTotalCount;    //所有表总记录数
    private List<TableChunk> tableChunkList;
    private PartitionIndex partitionIndex;

    public Table() {
        tableTotalCount = 0;
        tableChunkList = new ArrayList<TableChunk>();
        partitionIndex = null;
    }


    public void addTableChunk(TableChunk tableChunk){
        tableChunkList.add(tableChunk);
    }

    public List<TableChunk> getTableChunkList() {
        return tableChunkList;
    }

    public void setTableChunkList(List<TableChunk> tableChunkList) {
        this.tableChunkList = tableChunkList;
    }

    public PartitionIndex getPartitionIndex() {
        return partitionIndex;
    }

    public void setPartitionIndex(PartitionIndex partitionIndex) {
        this.partitionIndex = partitionIndex;
    }

    public int getTableTotalCount() {
        return tableTotalCount;
    }

    public void setTableTotalCount(int tableTotalCount) {
        this.tableTotalCount = tableTotalCount;
    }

}
