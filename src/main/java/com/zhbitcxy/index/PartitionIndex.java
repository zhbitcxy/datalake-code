package com.zhbitcxy.index;

import com.zhbitcxy.enums.LikeTypeEnum;
import com.zhbitcxy.tableInfo.MyRegex;
import com.zhbitcxy.tableInfo.TableChunk;
import com.zhbitcxy.Utils;

import java.util.*;

final public class PartitionIndex {
    Map<String, TreeMap<String, List<TableChunk>>> levelIndex;

    public PartitionIndex(Map<String, TreeMap<String, List<TableChunk>>> levelIndex) {
        this.levelIndex = levelIndex;
    }

    public void setLevelIndex(Map<String, TreeMap<String, List<TableChunk>>> levelIndex) {
        this.levelIndex = levelIndex;
    }

    public List<TableChunk> getTableChunk(String partitionKey, String partitionValue, String operator){
        List<TableChunk> tableChunkList = null;
        TreeMap<String, List<TableChunk>> partitionValMap = levelIndex.get(partitionKey);
        SortedMap filterMap = null;
        ArrayList<ArrayList<TableChunk>> list;
        ArrayList<ArrayList<TableChunk>> list2;
        switch (operator){
            case "=":
                tableChunkList = partitionValMap.get(partitionValue);
                break;
            case "!=":

                filterMap = partitionValMap.headMap(partitionValue, false);
                list = new ArrayList<>(filterMap.values());
                int size1 = filterMap.values().size();

                filterMap = partitionValMap.tailMap(partitionValue, false);
                list2 = new ArrayList<>(filterMap.values());
                int size2 = filterMap.values().size();

                tableChunkList = new ArrayList<>(size1 + size2);

                for (List<TableChunk> item : list){
                    tableChunkList.addAll(item);
                }
                for (List<TableChunk> item : list2){
                    tableChunkList.addAll(item);
                }
                break;
            case ">":

                filterMap = partitionValMap.tailMap(partitionValue, false);
                tableChunkList = new ArrayList<>(filterMap.values().size());
                list = new ArrayList<>(filterMap.values());
                for (List<TableChunk> item : list){
                    tableChunkList.addAll(item);
                }

                break;
            case "<":
                filterMap = partitionValMap.headMap(partitionValue, false);
                tableChunkList = new ArrayList<>(filterMap.values().size());
                list = new ArrayList<>(filterMap.values());
                for (List<TableChunk> item : list){
                    tableChunkList.addAll(item);
                }
                break;
        }

        return tableChunkList;
    }

    public List<TableChunk> getTableChunkForLike(String likeColumn, String likeArgs, LikeTypeEnum likeType){
        List<TableChunk> tableChunkList = new ArrayList<>();

        List<String> likeStrList = new ArrayList<>();
        String cleanStr = likeArgs.substring(1, likeArgs.length() - 1);
        String[] tokens = cleanStr.split(",");

        for (String token : tokens){
            String tmp = token.substring(1, token.length() - 1);
            likeStrList.add(tmp);
        }

        List<MyRegex> myRegexList = Utils.sortRegexStrList(likeStrList);

        TreeMap<String, List<TableChunk>> partitionValMap = levelIndex.get(likeColumn);

        byte[] keyBytes;
        switch (likeType){
            case ALL_LIKE:
                for (Map.Entry<String, List<TableChunk>> entry : partitionValMap.entrySet()){
                    keyBytes = entry.getKey().getBytes();
                    if (Utils.allLikeHandle(myRegexList, likeType, keyBytes, 0, keyBytes.length)){
                        tableChunkList.addAll(entry.getValue());
                    }
                }
                break;
            case ANY_LIKE:
                for (Map.Entry<String, List<TableChunk>> entry : partitionValMap.entrySet()){
                    keyBytes = entry.getKey().getBytes();
                    if (Utils.anyLikeHandle(myRegexList, likeType, keyBytes, 0, keyBytes.length)){
                        tableChunkList.addAll(entry.getValue());
                    }
                }
                break;
            case NONE_LIKE:
                for (Map.Entry<String, List<TableChunk>> entry : partitionValMap.entrySet()){
                    keyBytes = entry.getKey().getBytes();
                    if (Utils.noneLikeHandle(myRegexList, likeType, keyBytes, 0, keyBytes.length)){
                        tableChunkList.addAll(entry.getValue());
                    }
                }
                break;
        }

        return tableChunkList;
    }
}


