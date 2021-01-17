package com.zhbitcxy.entity;

import com.zhbitcxy.enums.RegexStrTypeEnum;
import com.zhbitcxy.index.PrefixTreeNode;
import com.zhbitcxy.index.SuffixTreeNode;
import com.zhbitcxy.tableInfo.MyRegex;

import java.util.Iterator;
import java.util.List;

public class MyRegexListWrap {
    private List<MyRegex> sortedLikeStrList;
    private boolean hasAny;
    private boolean hasPrefixTree;
    private boolean hasSuffixTree;
    private int prefixCnt;
    private int suffixCnt;

    // 前缀和后缀树引用
    PrefixTreeNode prefixTreeNode;
    SuffixTreeNode suffixTreeNode;

    public MyRegexListWrap() {
        hasPrefixTree = false;
        hasSuffixTree = false;
        prefixCnt = 0;
        suffixCnt = 0;
    }

    public void init(List<MyRegex> sortedLikeStrList) {
        this.sortedLikeStrList = sortedLikeStrList;
        for (MyRegex myRegex : sortedLikeStrList){
            if (myRegex.getType() == RegexStrTypeEnum.PREFIX){
                prefixCnt++;
            }
            if (myRegex.getType() == RegexStrTypeEnum.SUFFIX){
                suffixCnt++;
            }
            if (myRegex.getType() == RegexStrTypeEnum.ANY){
                hasAny = true;
            }
        }

        if (prefixCnt >= 3){

            //生成前缀树
            prefixTreeNode = new PrefixTreeNode();
            PrefixTreeNode treeNode;
            Iterator<MyRegex> iterator = sortedLikeStrList.iterator();
            while (iterator.hasNext()) {
                MyRegex myRegex = iterator.next();
                if (myRegex.getType() == RegexStrTypeEnum.PREFIX) {
                    byte[] bytes = myRegex.getStr().getBytes();
                    treeNode = prefixTreeNode;
                    for (int i = 0, len = bytes.length; i < len; i++) {
                        treeNode.insertByte(bytes[i]);
                        treeNode = treeNode.getChild(bytes[i]);
                    }
                    treeNode.leafMark();
                    iterator.remove();
                }
            }

            hasPrefixTree = true;
        }

        if (suffixCnt >= 3){

            //生成前缀树
            suffixTreeNode = new SuffixTreeNode();
            SuffixTreeNode treeNode;
            Iterator<MyRegex> iterator = sortedLikeStrList.iterator();
            while (iterator.hasNext()) {
                MyRegex myRegex = iterator.next();
                if (myRegex.getType() == RegexStrTypeEnum.SUFFIX) {
                    byte[] bytes = myRegex.getStr().getBytes();
                    treeNode = suffixTreeNode;
                    for (int i = (bytes.length - 1); i >= 0; i--) {
                        treeNode.insertByte(bytes[i]);
                        treeNode = treeNode.getChild(bytes[i]);
                    }
                    treeNode.leafMark();
                    iterator.remove();
                }
            }

            hasSuffixTree = true;
        }
    }

    public List<MyRegex> getSortedLikeStrList() {
        return sortedLikeStrList;
    }

    public boolean hasAny() {
        return hasAny;
    }

    public boolean hasPrefixTree() {
        return hasPrefixTree;
    }

    public boolean hasSuffixTree() {
        return hasSuffixTree;
    }

    public PrefixTreeNode getPrefixTreeNode() {
        return prefixTreeNode;
    }

    public SuffixTreeNode getSuffixTreeNode() {
        return suffixTreeNode;
    }
}
