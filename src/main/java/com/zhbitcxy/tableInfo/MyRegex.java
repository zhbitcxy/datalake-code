package com.zhbitcxy.tableInfo;

import com.zhbitcxy.enums.RegexStrTypeEnum;

import java.util.Collections;
import java.util.List;

public class MyRegex {
    private String str;
    private RegexStrTypeEnum type;
    private boolean hasUnderline;

    private int strLen; //for ANY_FIXED type
    private List<String> tokenList; //for FULL type

    public MyRegex(String str, RegexStrTypeEnum type) {
        this.str = str;
        this.type = type;
        this.strLen = strLen;
        tokenList = Collections.emptyList();
        hasUnderline = false;
    }

    public MyRegex(String str, RegexStrTypeEnum type, boolean hasUnderline) {
        this.str = str;
        this.type = type;
        tokenList = Collections.emptyList();
        this.hasUnderline = hasUnderline;
    }

    public MyRegex(String str, RegexStrTypeEnum type, int strLen) {
        this.str = str;
        this.type = type;
        this.strLen = strLen;
        tokenList = Collections.emptyList();
    }

    public MyRegex(RegexStrTypeEnum type, List<String> tokenList) {
        this.type = type;
        this.tokenList = tokenList;
    }

    public boolean hasUnderline() {
        return hasUnderline;
    }

    public int getStrLen() {
        return strLen;
    }

    public void setStrLen(int strLen) {
        this.strLen = strLen;
    }

    public List<String> getTokenList() {
        return tokenList;
    }

    public void setTokenList(List<String> tokenList) {
        this.tokenList = tokenList;
    }

    public String toString(){
        return str + " -> " + type.toString();
    }

    public String getStr() {
        return str;
    }

    public RegexStrTypeEnum getType() {
        return type;
    }

    public void setStr(String str) {
        this.str = str;
    }
}
