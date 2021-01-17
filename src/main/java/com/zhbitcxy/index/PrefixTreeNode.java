package com.zhbitcxy.index;

import java.util.HashMap;
import java.util.Map;

public class PrefixTreeNode {
    public PrefixTreeNode() {
        byteMap = new HashMap<>();
        isLeaf = false;
    }

    public void insertByte(byte b){
        if (!byteMap.containsKey(b)){
            byteMap.put(b, new PrefixTreeNode());
        }
    }

    public PrefixTreeNode getChild(byte b){
        PrefixTreeNode treeNode = byteMap.get(b);
        return treeNode;
    }

    public boolean find(byte[] bytes, int pos, int limit){
        Map<Byte, PrefixTreeNode> iteratorMap = byteMap;
        PrefixTreeNode node = null;
        for (int i = pos; i < limit; i++){

            byte b0 = bytes[i];
            node = iteratorMap.get(b0);

            if (node == null){
                return false;
            }

            if (node.getByteMap().isEmpty()){
                if (i == limit - 1){
                    return true;
                }else{
                    return false;
                }
            }

            iteratorMap = node.getByteMap();
        }

        if (node != null){
            return node.isLeaf();
        }else{
            return false;
        }
    }

    public boolean findAny(byte[] bytes, int pos, int len){
        Map<Byte, PrefixTreeNode> iteratorMap = byteMap;
        PrefixTreeNode node = null;
        int limit = pos + len;
        for (int i = pos; i < limit; i++){

            byte b0 = bytes[i];
            node = iteratorMap.get(b0);

            if (node == null){
                return false;
            }

            if (node.isLeaf()) {
                return true;
            }

            if (node.getByteMap().isEmpty()){
                if (i == limit - 1){
                    return true;
                }else{
                    return false;
                }
            }

            iteratorMap = node.getByteMap();
        }

        if (node != null){
            return node.isLeaf();
        }else{
            return false;
        }
    }

    public Map<Byte, PrefixTreeNode> getByteMap() {
        return byteMap;
    }

    public void leafMark(){
        isLeaf = true;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    Map<Byte, PrefixTreeNode> byteMap;
    private boolean isLeaf;
}
