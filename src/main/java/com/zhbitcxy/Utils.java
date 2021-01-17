package com.zhbitcxy;

import com.zhbitcxy.entity.MyString;
import com.zhbitcxy.enums.LikeTypeEnum;
import com.zhbitcxy.enums.RegexStrTypeEnum;
import com.zhbitcxy.offheapUtil.UnsafeAllocator;
import com.zhbitcxy.tableInfo.HeaderCell;
import com.zhbitcxy.tableInfo.Item;
import com.zhbitcxy.tableInfo.MyRegex;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class Utils {

    @Deprecated
     public static String getRegexStr(String str){
        StringBuilder regexStr = new StringBuilder();
        int i = 0;
        if (str.charAt(0) != '%'){
            regexStr.append("^");
        }else{
            regexStr.append("([\\s\\S]*)");
            i++;
        }
        for (; i < str.length(); i++){
            if (str.charAt(i) == '%') {
                regexStr.append("([\\s\\S]*)");

            }else if(str.charAt(i) == '_'){
                regexStr.append("([\\s\\S]{1})");
            }else if(str.charAt(i) == '\\'){
                i++;
                if (isSpeChar(str.charAt(i))){
                    regexStr.append("\\");
                }
                regexStr.append(str.charAt(i));
            }else{
                if (isSpeChar(str.charAt(i))){
                    regexStr.append("\\");
                }
                regexStr.append(str.charAt(i));
            }
        }

        if (str.charAt(str.length()-1) != '%'){
            regexStr.append("$");
        }

        String pattern = "";

        return regexStr.toString();
    }

    private static boolean isSpeChar(char ch){
        switch (ch){
            case '$':
            case '(':
            case ')':
            case '*':
            case '+':
            case '.':
            case '[':
            case '?':
            case '^':
            case '{':
            case '|':
            case '\\':
                return true;
        }
        return false;
    }

    public static List<String> split(String str, char ch){

         byte[] bytes = str.getBytes();
         int len = bytes.length;

         int startPos = 0;
         List<String> stringList = new ArrayList<>();
         int i = 0;
         for (; i < len; i++){
             if (bytes[i] == ch){
                 stringList.add(new String(bytes, startPos, i - startPos));
                 startPos = i + 1;
             }
         }
         if (startPos < len){
             stringList.add(new String(bytes, startPos, len - startPos));
         }
         return stringList;
    }

    @Deprecated
    public static List<String> getRegexStrList(String likeArgs){
         List<String> regexStrList = new ArrayList<>();
         String cleanStr = likeArgs.substring(1, likeArgs.length() - 1);
         String[] tokens = cleanStr.split(",");

         for (String token : tokens){
            String tmp = token.substring(1, token.length() - 1);
            String regexStr = Utils.getRegexStr(tmp);
            regexStrList.add(regexStr);
         }
         return regexStrList;
    }

    public static List<MyRegex> sortRegexStrList(List<String> likeStrList){
        List<MyRegex> sortedLikeStrList = new ArrayList<>();
        RegexStrTypeEnum typeEnum;

        for (String regexStr : likeStrList){
            int percentSignCnt = 0;
            int underlineSignCnt = 0;
            char preCh = '|';
            for (int i = 0, len = regexStr.length(); i < len; i++){
                char ch = regexStr.charAt(i);
                if (ch == '%' && preCh != '\\'){
                    percentSignCnt++;
                }
                if (ch == '_' && preCh != '\\'){
                    underlineSignCnt++;
                }
                preCh = ch;
            }

            //形如  %%...%
            if (percentSignCnt == regexStr.length()){
                sortedLikeStrList.add(new MyRegex("%", RegexStrTypeEnum.ANY));
                continue;
            }

            //形如 ____
            if (underlineSignCnt == regexStr.length()){
                sortedLikeStrList.add(new MyRegex("", RegexStrTypeEnum.ANY_FIXED, underlineSignCnt));
                continue;
            }

            if (percentSignCnt == 0){
                 //形如 ab_c...e

                sortedLikeStrList.add(new MyRegex(cleanSpeStr(regexStr), RegexStrTypeEnum.CONST, underlineSignCnt > 0));
            }else{
                boolean isSuffix = (regexStr.charAt(0) == '%');
                boolean isPrefix = (regexStr.charAt(regexStr.length()-1) == '%');
                if (percentSignCnt == 2 && isPrefix && isSuffix){
                    //形如 %ab_c...e%

                    sortedLikeStrList.add(new MyRegex(cleanSpeStr(regexStr.substring(1, regexStr.length() - 1)),
                            RegexStrTypeEnum.FIXED, underlineSignCnt > 0));

                    continue;
                }

                if (percentSignCnt == 1 && isSuffix){
                    //形如 %ab_c...e
                    if (underlineSignCnt > 0){
                        sortedLikeStrList.add(new MyRegex(cleanSpeStr(regexStr.substring(1, regexStr.length())),
                                RegexStrTypeEnum.SUFFIX_, true));
                    }else{
                        sortedLikeStrList.add(new MyRegex(cleanSpeStr(regexStr.substring(1, regexStr.length())),
                                RegexStrTypeEnum.SUFFIX, false));
                    }
//                    sortedLikeStrList.add(new MyRegex(cleanSpeStr(regexStr.substring(1, regexStr.length())),
//                            RegexStrTypeEnum.SUFFIX, underlineSignCnt > 0));
                    continue;
                }

                if (percentSignCnt == 1 && isPrefix){
                    //形如 ab_c..e%
                    if (underlineSignCnt > 0){
                        sortedLikeStrList.add(new MyRegex(cleanSpeStr(regexStr.substring(0, regexStr.length() - 1)),
                                RegexStrTypeEnum.PREFIX_, true));
                    }else{
                        sortedLikeStrList.add(new MyRegex(cleanSpeStr(regexStr.substring(0, regexStr.length() - 1)),
                                RegexStrTypeEnum.PREFIX, false));
                    }

                    continue;
                }

                sortedLikeStrList.add(new MyRegex(RegexStrTypeEnum.FULL, parseTokens(regexStr)));
            }
        }

        sortedLikeStrList.sort(Comparator.comparing(MyRegex::getType));

        return sortedLikeStrList;
    }

    public static List<MyRegex> mergeRegexStrList(List<MyRegex> oldRegexStrList, LikeTypeEnum likeType){

        //todo 对能合并的模式串进行合并
        switch (likeType){
            case ALL_LIKE:

                break;

            case ANY_LIKE:

                break;

            case NONE_LIKE:

                break;
        }
        return oldRegexStrList;
    }

    //把_替换成|，因为|不可能是内容，方便后续处理
    public static String cleanSpeStr(String str){
         StringBuilder sb = new StringBuilder();
         char preCh = '|';
         for (int i = 0, len = str.length(); i < len; i++){
            char ch = str.charAt(i);
            if (ch == '\\'){
                if (preCh == '\\'){
                    sb.append(ch);
                }
            }else{
                if (ch == '_' && preCh != '\\'){
                    sb.append('|');
                }else{
                    sb.append(ch);
                }
            }
             preCh = ch;
         }

         return sb.toString();
    }

    public static List<String> parseTokens(String str){
        StringBuilder sb = new StringBuilder();
        char preCh = '|';
        int startPos = 0;
        int endPos = str.length() - 1;
        while (str.charAt(startPos) == '%'){
            startPos++;
        }

        while (str.charAt(endPos) == '%'){
            endPos--;
        }

        if (endPos == 1 && str.charAt(endPos - 1) == '\\'){
            endPos++;
        }else if (endPos >= 2 && str.charAt(endPos - 1) == '\\' && str.charAt(endPos - 2) != '\\'){
            endPos++;
        }

        for (int i = startPos; i <= endPos; i++){
            char ch = str.charAt(i);
            if (ch == '\\'){
                if (preCh == '\\'){
                    sb.append(ch);
                }
            }else{
                if (ch == '_' && preCh != '\\'){
                    sb.append('|');
                }else if (ch == '%'){
                    if(preCh == '\\'){
                        if ((i - 2 >= 0) && str.charAt(i - 2) != '\\'){
                            sb.append('%');
                        }else{
                            sb.append(',');
                        }
                    }else{
                        sb.append(',');
                    }
                }else{
                    sb.append(ch);
                }
            }
            preCh = ch;
        }

        String content = sb.toString();
        List<String> cleanStrList = Utils.split(content, ',');
        Iterator<String> iterator = cleanStrList.iterator();
        while (iterator.hasNext()){
            String item = iterator.next();
            if (item.isEmpty()){
                iterator.remove();
            }
        }
        return cleanStrList;
    }

    public static List<Pattern> getPatternList(String likeArgs){
        List<Pattern> patternList = new ArrayList<>();

        String cleanStr = likeArgs.substring(1, likeArgs.length()-1);
        String[] tokens = cleanStr.split(",");
        for (String token : tokens){
            String tmp = token.substring(1, token.length()-1);
            String regexStr = Utils.getRegexStr(tmp);
            Pattern p = Pattern.compile( regexStr );
            patternList.add(p);
        }

        return patternList;
    }


    public static List<Item> getColumnValList(HeaderCell headerCell) throws IOException {
        List<Item> itemList = new ArrayList<>(headerCell.getEleSize());

        int eleSize = headerCell.getEleSize();
        for (int id = 0; id < eleSize; id++){
            String str = headerCell.getContent(id);
            itemList.add(new Item(str, id));
        }

        return itemList;
    }

    private static boolean constMatch(byte[] bytes1, int len1, String findStr){
         byte[] bytes2 = findStr.getBytes();
         int len2 = bytes2.length;

         if (len1 != len2){
             return false;
         }else{

             for (int i = 0; i < len1; i++){
                 if (bytes1[i] != bytes2[i]){
                    if (bytes2[i] == '|'){
                        continue;
                    }
                     return false;
                 }
             }
             return true;
         }
    }

    private static boolean constMatch(byte[] bytes1, int startPos, int len1, String findStr){
        byte[] bytes2 = findStr.getBytes();
        int len2 = bytes2.length;

        if (len1 != len2){
            return false;
        }else{

            for (int i = 0; i < len1; i++){
                if (bytes1[i + startPos] != bytes2[i]){
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean constMatch_(byte[] bytes1, int startPos, int len1, String findStr){
        byte[] bytes2 = findStr.getBytes();
        int len2 = bytes2.length;

        if (len1 != len2){
            return false;
        }else{

            for (int i = 0; i < len1; i++){
                if (bytes1[i + startPos] != bytes2[i]){
                    if (bytes2[i] == '|'){
                        continue;
                    }
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean prefixMatch(byte[] bytes1, int len1, String findStr){
         byte[] bytes2 = findStr.getBytes();
        int len2 = bytes2.length;

         if (len1 < len2){
             return false;
         }

        for (int j = 0; j < len2; j++){
            if (bytes1[j] != bytes2[j]){
                if (bytes2[j] == '|'){  // _ -> |
                    continue;
                }
                return false;
            }
        }
        return true;

    }

    private static boolean prefixMatch(byte[] bytes1, int startPos, int len1, String findStr){
        byte[] bytes2 = findStr.getBytes();
        int len2 = bytes2.length;

        if (len1 < len2){
            return false;
        }

        for (int j = 0; j < len2; j++){
            if (bytes1[j + startPos] != bytes2[j]){
                return false;
            }
        }
        return true;

    }

    private static boolean prefixMatch_(byte[] bytes1, int startPos, int len1, String findStr){
        byte[] bytes2 = findStr.getBytes();
        int len2 = bytes2.length;

        if (len1 < len2){
            return false;
        }

        for (int j = 0; j < len2; j++){
            if (bytes1[j + startPos] != bytes2[j]){
                if (bytes2[j] == '|'){  // _ -> |
                    continue;
                }
                return false;
            }
        }
        return true;

    }

    private static boolean suffixMatch(byte[] bytes1, int len1, String findStr){
        byte[] bytes2 = findStr.getBytes();
        int len2 = bytes2.length;

        if (len1 < len2){
            return false;
        }

        for (int j = 0; j < len2; j++){
            if (bytes1[len1 - len2 + j] != bytes2[j]){
                if (bytes2[j] == '|'){  // _ -> |
                    continue;
                }
                return false;
            }
        }

        return true;
    }

    private static boolean suffixMatch(byte[] bytes1, int startPos, int len1, String findStr){
        byte[] bytes2 = findStr.getBytes();
        int len2 = bytes2.length;

        if (len1 < len2){
            return false;
        }

        int offset = startPos + len1 - len2;
        for (int j = 0; j < len2; j++){
            if (bytes1[offset + j] != bytes2[j]){
                return false;
            }
        }

        return true;
    }

    private static boolean suffixMatch_(byte[] bytes1, int startPos, int len1, String findStr){
        byte[] bytes2 = findStr.getBytes();
        int len2 = bytes2.length;

        if (len1 < len2){
            return false;
        }

        int offset = startPos + len1 - len2;
        for (int j = 0; j < len2; j++){
            if (bytes1[offset + j] != bytes2[j]){
                if (bytes2[j] == '|'){  // _ -> |
                    continue;
                }
                return false;
            }
        }

        return true;
    }

    public static boolean strMatch(byte[] bytes1, int len1, String findStr){

        byte[] bytes2 = findStr.getBytes();

        int len2 = bytes2.length;

        if (len1 < len2){
            return false;
        }

        for (int i = 0, len = len1 - len2 + 1; i < len; i++){
            for (int j = 0; j < len2; j++){
                if (bytes1[i + j] != bytes2[j]){
                    if (bytes2[j] != '|'){  // _ -> |
                        break;
                    }
                }
                if (j == len2-1){
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean strMatch(byte[] bytes1, int startPos, int len1, String findStr){

        byte[] bytes2 = findStr.getBytes();

        int len2 = bytes2.length;

        if (len1 < len2){
            return false;
        }

        for (int i = startPos, len = startPos + len1 - len2 + 1; i < len; i++){
            for (int j = 0; j < len2; j++){
                if (bytes1[i + j] != bytes2[j]){
                    break;
                }
                if (j == len2-1){
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean strMatch_(byte[] bytes1, int startPos, int len1, String findStr){

        byte[] bytes2 = findStr.getBytes();

        int len2 = bytes2.length;

        if (len1 < len2){
            return false;
        }

        for (int i = startPos, len = startPos + len1 - len2 + 1; i < len; i++){
            for (int j = 0; j < len2; j++){
                if (bytes1[i + j] != bytes2[j]){
                    if (bytes2[j] != '|'){  // _ -> |
                        break;
                    }
                }
                if (j == len2-1){
                    return true;
                }
            }
        }
        return false;
    }

    public static int strBytesMatch(byte[] bytes1, int off1, int len1, byte[] bytes2, int off2, int len2){
        for (int i = off1, len = len1 - len2 + 1; i < len; i++){
            for (int j = off2; j < len2; j++){
                if (bytes1[i + j] != bytes2[j]){
                    if (bytes2[j] != '|'){  // _ -> |
                        break;
                    }
                }
                if (j == len2 - 1){
                    return i + len2;
                }
            }
        }
        return -1;
    }

    public static int strBytesMatch(byte[] bytes1, int baseOff, int off1, int len1, byte[] bytes2, int off2, int len2){
        for (int i = baseOff + off1, len = baseOff + len1 - len2 + 1; i < len; i++){
            for (int j = off2; j < len2; j++){
                if (bytes1[i + j] != bytes2[j]){
                    if (bytes2[j] != '|'){  // _ -> |
                        break;
                    }
                }
                if (j == len2 - 1){
                    return i + len2 - baseOff;
                }
            }
        }
        return -1;
    }

    public static boolean patternMatch(byte[] bytes1, int len1, List<String> tokenList){

         int pos = 0;

         int matchCnt = 0;
         for (int i = 0, len = tokenList.size(); i < len; i++){
             byte[] bytes2 = tokenList.get(i).getBytes();
             int res = strBytesMatch(bytes1, pos, len1, bytes2, 0, bytes2.length);
             if (res != -1){
                 pos = res;
                 matchCnt++;
             }else{
                 break;
             }
         }
         if (matchCnt == tokenList.size()){
             return true;
         }
         return false;
    }

    public static boolean patternMatch(byte[] bytes1, int startPos, int len1, List<String> tokenList){
        int pos = 0;

        int matchCnt = 0;
        for (int i = 0, len = tokenList.size(); i < len; i++){
            byte[] bytes2 = tokenList.get(i).getBytes();
            int res = strBytesMatch(bytes1, startPos, pos, len1, bytes2, 0, bytes2.length);
            if (res != -1){
                pos = res;
                matchCnt++;
            }else{
                break;
            }
        }
        if (matchCnt == tokenList.size()){
            return true;
        }
        return false;
    }

    public static boolean allLikeHandle(List<MyRegex> regexStrList, LikeTypeEnum likeType, byte[] bytes, int startPos, int len){
        boolean isAllLike = true;
        for (MyRegex regexObj : regexStrList){
            boolean isDone = false;
            switch (regexObj.getType()){
                case PREFIX:
                case PREFIX_:
                    if (regexObj.hasUnderline()){
                        if (!prefixMatch_(bytes, startPos, len, regexObj.getStr())){
                            isAllLike = false;
                            isDone = true;
                        }
                    }else{
                        if (!prefixMatch(bytes, startPos, len, regexObj.getStr())){
                            isAllLike = false;
                            isDone = true;
                        }
                    }

                    break;
                case SUFFIX:
                case SUFFIX_:
                    if (regexObj.hasUnderline()){
                        if (!suffixMatch_(bytes, startPos, len, regexObj.getStr())){
                            isAllLike = false;
                            isDone = true;
                        }
                    }else{
                        if (!suffixMatch(bytes, startPos, len, regexObj.getStr())){
                            isAllLike = false;
                            isDone = true;
                        }
                    }
                    break;
                case FIXED:
                    if (regexObj.hasUnderline()){
                        if (!strMatch_(bytes, startPos, len, regexObj.getStr())){

                            isAllLike = false;
                            isDone = true;
                        }
                    }else{
                        if (!strMatch(bytes, startPos, len, regexObj.getStr())){

                            isAllLike = false;
                            isDone = true;
                        }
                    }
                    break;

                case CONST:
                    if (regexObj.hasUnderline()){
                        if (!constMatch_(bytes, startPos, len, regexObj.getStr())){
                            isAllLike = false;
                            isDone = true;
                        }
                    }else{
                        if (!constMatch(bytes, startPos, len, regexObj.getStr())){
                            isAllLike = false;
                            isDone = true;
                        }
                    }

                    break;
                case FULL:
                    if (!patternMatch(bytes, startPos, len, regexObj.getTokenList())){
                        isAllLike = false;
                        isDone = true;
                    }
                    break;
                case ANY_FIXED:
                    if (len != regexObj.getStrLen()){
                        isAllLike = false;
                        isDone = true;
                    }
                    break;
                case ANY:
                    break;

            }
            if (isDone){
                break;
            }
        }
        return isAllLike;
    }

    public static boolean anyLikeHandle(List<MyRegex> regexStrList, LikeTypeEnum likeType, byte[] bytes, int startPos, int len){
        boolean isAnyLike = false;

        for (MyRegex regexObj : regexStrList){
            boolean isDone = false;
            switch (regexObj.getType()){
                case PREFIX:
                case PREFIX_:
                    if (regexObj.hasUnderline()){
                        if (prefixMatch_(bytes, startPos, len, regexObj.getStr())){
                            isAnyLike = true;
                            isDone = true;
                        }
                    }else{
                        if (prefixMatch(bytes, startPos, len, regexObj.getStr())){
                            isAnyLike = true;
                            isDone = true;
                        }
                    }

                    break;
                case SUFFIX:
                case SUFFIX_:
                    if (regexObj.hasUnderline()){
                        if (suffixMatch_(bytes, startPos, len, regexObj.getStr())){
                            isAnyLike = true;
                            isDone = true;
                        }
                    }else{
                        if (suffixMatch(bytes, startPos, len, regexObj.getStr())){
                            isAnyLike = true;
                            isDone = true;
                        }
                    }

                    break;
                case FIXED:
                    if (regexObj.hasUnderline()){
                        if (strMatch_(bytes, startPos, len, regexObj.getStr())){
                            isAnyLike = true;
                            isDone = true;
                        }
                    }else{
                        if (strMatch(bytes, startPos, len, regexObj.getStr())){
                            isAnyLike = true;
                            isDone = true;
                        }
                    }

                    break;
                case CONST:
                    if (regexObj.hasUnderline()){
                        if (constMatch_(bytes, startPos, len, regexObj.getStr())){
                            isAnyLike = true;
                            isDone = true;
                        }
                    }else{
                        if (constMatch(bytes, startPos, len, regexObj.getStr())){
                            isAnyLike = true;
                            isDone = true;
                        }
                    }

                    break;
                case FULL:
                    if (patternMatch(bytes, startPos, len, regexObj.getTokenList())){
                        isAnyLike = true;
                        isDone = true;
                    }
                    break;
                case ANY_FIXED:
                    if (len == regexObj.getStrLen()){
                        isAnyLike = true;
                        isDone = true;
                    }
                    break;
                case ANY:
                    isAnyLike = true;
                    isDone = true;
                    break;

            }
            if (isDone){
                break;
            }

        }
        return isAnyLike;
    }

    public static boolean noneLikeHandle(List<MyRegex> regexStrList, LikeTypeEnum likeType, byte[] bytes, int startPos, int len){
        boolean isNoneLike = true;

        for (MyRegex regexObj : regexStrList){
            boolean isDone = false;
            switch (regexObj.getType()){

                case PREFIX:
                case PREFIX_:
                    if (regexObj.hasUnderline()){
                        if (prefixMatch_(bytes, startPos, len, regexObj.getStr())){
                            isNoneLike = false;
                            isDone = true;
                        }
                    }else{
                        if (prefixMatch(bytes, startPos, len, regexObj.getStr())){
                            isNoneLike = false;
                            isDone = true;
                        }
                    }
                    break;
                case SUFFIX:
                case SUFFIX_:
                    if (regexObj.hasUnderline()){
                        if (suffixMatch_(bytes, startPos, len, regexObj.getStr())){
                            isNoneLike = false;
                            isDone = true;
                        }
                    }else{
                        if (suffixMatch(bytes, startPos, len, regexObj.getStr())){
                            isNoneLike = false;
                            isDone = true;
                        }
                    }

                    break;
                case FIXED:
                    if (regexObj.hasUnderline()){
                        if (strMatch_(bytes, startPos, len, regexObj.getStr())){
                            isNoneLike = false;
                            isDone = true;
                        }
                    }else{
                        if (strMatch(bytes, startPos, len, regexObj.getStr())){
                            isNoneLike = false;
                            isDone = true;
                        }
                    }

                    break;
                case CONST:
                    if (regexObj.hasUnderline()){
                        if (constMatch_(bytes, startPos, len, regexObj.getStr())){
                            isNoneLike = false;
                            isDone = true;
                        }
                    }else{
                        if (constMatch(bytes, startPos, len, regexObj.getStr())){
                            isNoneLike = false;
                            isDone = true;
                        }
                    }

                    break;
                case FULL:
                    if (patternMatch(bytes, startPos, len, regexObj.getTokenList())){
                        isNoneLike = false;
                        isDone = true;
                    }
                    break;
                case ANY_FIXED:
                    if (len == regexObj.getStrLen()){
                        isNoneLike = false;
                        isDone = true;
                    }
                    break;
                case ANY:
                    isNoneLike = false;
                    isDone = true;
                    break;
            }
            if (isDone){
                break;
            }
        }
        return isNoneLike;
    }

    public static LikeTypeEnum getLikeTypeEnum(String likeStr){
        if (likeStr.equals("ALL_LIKE")){
            return LikeTypeEnum.ALL_LIKE;
        }else if (likeStr.equals("ANY_LIKE")){
            return LikeTypeEnum.ANY_LIKE;
        }else{
            return LikeTypeEnum.NONE_LIKE;
        }
    }

    public static int upperBound(List<Item> strList, String aim) {
        int low = 0, high = strList.size();
        while (low < high) {
            int mid = low + (high - low) / 2;
            if (strList.get(mid).compareTo(aim) <= 0) low = mid + 1;
            else high = mid;
        }
        return high;

    }

    public static int lowerBound(List<Item> strList, String aim) {
        int low = 0, high = strList.size() ;
        while(low < high)
        {
            int mid = low + (high - low)/2;
            int val = strList.get(mid).compareTo(aim);
            if(val >= 0)  high = mid;
            else low = mid + 1;
        }

        return low;
    }

    public static int byteCompare(byte[] bytes0, byte[] bytes1){
        int len1 = bytes0.length;
        int len2 = bytes1.length;
        int lim = Math.min(len1, len2);

        int k = 0;
        while (k < lim) {
            byte c1 = bytes0[k];
            byte c2 = bytes1[k];
            if (c1 != c2) {
                return c1 - c2;
            }
            k++;
        }
        return len1 - len2;
    }

    public static int byteCompare(byte[] bytes0, int off1, int len1, byte[] bytes1, int off2, int len2){
        int lim = Math.min(len1, len2);

        int k = 0;
        while (k < lim) {
            byte c1 = bytes0[k + off1];
            byte c2 = bytes1[k + off2];
            if (c1 != c2) {
                return c1 - c2;
            }
            k++;
        }
        return len1 - len2;
    }


}
