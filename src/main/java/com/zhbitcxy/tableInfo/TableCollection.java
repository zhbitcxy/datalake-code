package com.zhbitcxy.tableInfo;

import java.util.HashMap;
import java.util.Map;

public class TableCollection {
    Map<String, Table> tableMap;

    public TableCollection() {
        tableMap = new HashMap<String, Table>();
    }

    public Map<String, Table> getTableMap() {
        return tableMap;
    }

    public void setTableMap(Map<String, Table> tableMap) {
        this.tableMap = tableMap;
    }

    public void addTable(String tableName, Table table){
        tableMap.put(tableName, table);
    }

    public Table getTable(String tableName){
        return tableMap.get(tableName);
    }
}
