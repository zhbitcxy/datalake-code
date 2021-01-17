package com.zhbitcxy.index;

import java.util.HashMap;
import java.util.Map;

public class SuffixTreeNode {
    public SuffixTreeNode() {
        byteMap = new HashMap<>();
        isLeaf = false;
    }

    public void insertByte(byte b){
        if (!byteMap.containsKey(b)){
            byteMap.put(b, new SuffixTreeNode());
        }
    }

    public SuffixTreeNode getChild(byte b){
        SuffixTreeNode treeNode = byteMap.get(b);
        return treeNode;
    }


    public boolean findAny(byte[] bytes, int pos, int len){
        Map<Byte, SuffixTreeNode> iteratorMap = byteMap;
        SuffixTreeNode node = null;
        int limit = pos + len - 1;
        for (int i = limit; i >= pos; i--){

            byte b0 = bytes[i];
            node = iteratorMap.get(b0);

            if (node == null){
                return false;
            }

            if (node.isLeaf()) {
                return true;
            }

            if (node.getByteMap().isEmpty()){
                if (i == 0){
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

    public Map<Byte, SuffixTreeNode> getByteMap() {
        return byteMap;
    }

    public void leafMark(){
        isLeaf = true;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    Map<Byte, SuffixTreeNode> byteMap;
    private boolean isLeaf;
}
