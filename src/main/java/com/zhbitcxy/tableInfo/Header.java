package com.zhbitcxy.tableInfo;

import java.util.ArrayList;
import java.util.List;

public class Header {
    private List<HeaderCell> headerCellList;

    public Header() {
        this.headerCellList = new ArrayList<>();
    }

    public void addHeaderCell(HeaderCell headerCell){
        headerCellList.add(headerCell);
    }

    public HeaderCell getCell(int idx){
        return headerCellList.get(idx);
    }
}
