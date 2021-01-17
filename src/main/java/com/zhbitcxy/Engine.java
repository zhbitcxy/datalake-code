package com.zhbitcxy;

import com.zhbitcxy.entity.MyIntArray;
import com.zhbitcxy.entity.MyRegexListWrap;
import com.zhbitcxy.entity.MyString;
import com.zhbitcxy.enums.LikeTypeEnum;
import com.zhbitcxy.enums.RegexStrTypeEnum;
import com.zhbitcxy.index.PartitionIndex;
import com.zhbitcxy.index.PrefixTreeNode;
import com.zhbitcxy.index.SuffixTreeNode;
import com.zhbitcxy.offheapUtil.UnsafeAllocator;
import com.zhbitcxy.tableInfo.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class Engine {
    static final String pattern = "\\d+";
    static final Pattern digitRegex = Pattern.compile(pattern);

    static final int SHORT_TEXT_MAX_LEN = 48;

    static final int COLUMN_LEN = 6;

    int rowCounter = 0;
    String dirPath;

    Map<String, TreeMap<String, List<TableChunk>>> levelIndex;
    int tableChunkCount;
    TableCollection tableCollection;

    static ThreadLocal<byte[]> bytePoolThreadLocal = new ThreadLocal<byte[]>(){
        @Override
        protected byte[] initialValue() {
            return new byte[32 << 20];
        }
    };

    static ThreadLocal<MyString> myStringThreadLocal = ThreadLocal.withInitial(MyString::new);

    static ThreadLocal<MyIntArray> intArrayThreadLocal = new ThreadLocal<MyIntArray>(){
        @Override
        protected MyIntArray initialValue() {
            return new MyIntArray();
        }
    };

    static ThreadLocal<MyIntArray> idArrayThreadLocal = new ThreadLocal<MyIntArray>(){
        @Override
        protected MyIntArray initialValue() {
            return new MyIntArray();
        }
    };

    static final int CHUNK_SIZE = 64 << 10;

    public Engine(String dirPath) {
        this.dirPath = dirPath;
    }

    public void init(){
        tableChunkCount = 0;
        tableCollection = new TableCollection();

        final String dataBasePath = dirPath + "/" + Config.DATA_BASE ;
        File dirFile = new File(dataBasePath);

        if( !dirFile.exists() ){
            System.err.println(String.format("dataBasePath:%s is not exists.", dataBasePath));
            return;
        }

        File[] files = dirFile.listFiles();

        try{
            if (files != null){
                for (File file : files){
                    if(file.getName().indexOf(".") == 0){
                        continue;
                    }
                    if(file.isDirectory()){
                        handleTable(file);
                    }
                }
            }
        }catch (Exception e){
            System.err.println(e.getMessage());
        }

        System.out.println(String.format("tableChunkCount has %d. rowCounter: %d", tableChunkCount, rowCounter));
    }

    public int find(String commandLine){
        int result;
        String[] items = commandLine.split(" ");

        String columnOrPartition = items[1];
//
        String likeColumn = items[4];

        if (columnOrPartition.indexOf("column") == 0 && likeColumn.indexOf("column") == 0){
            //两个column都是数据列
            result = action(items);
        }else{
            if (likeColumn.indexOf("column") == 0){
                //第一个列是分区列，第二个是数据列
                result = actionForPartition(items);
            }else{
                //第一个列是数据列,第二个是分区列
                result = actionForPartitionExtern(items);
            }
            // todo 第一个列是分区列，第二个也是分区列
        }

        return result;

    }

    private int action(String[] items){
        int result = 0;
        final String tableName = items[0];

        final String columnKey = items[1];
        final String operator = items[2];
        final String columnValue =  items[3].substring(1, items[3].length()-1);

        final String likeColumn = items[4];
        final String likeType = items[5];
        final String likeArgs = items[6];

        final LikeTypeEnum likeTypeEnum = Utils.getLikeTypeEnum(likeType);

        //解析like数组
        List<String> likeStrList = new ArrayList<>();
        String cleanStr = likeArgs.substring(1, likeArgs.length() - 1);
        List<String> tokens = Utils.split(cleanStr,',');

        for (String token : tokens){
            String tmp = token.substring(1, token.length() - 1);
            likeStrList.add(tmp);
        }
        final List<MyRegex> myRegexList = Utils.sortRegexStrList(likeStrList);

        final MyRegexListWrap myRegexListWrap = new MyRegexListWrap();
        if (likeTypeEnum == LikeTypeEnum.ANY_LIKE || likeTypeEnum == LikeTypeEnum.NONE_LIKE){
            myRegexListWrap.init(myRegexList);
        }

        //todo 特殊情况处理

        Table table = tableCollection.getTable(tableName);
        List<TableChunk> tableChunkList = table.getTableChunkList();

        final int firstColumnNo = Integer.parseInt( columnKey.substring(COLUMN_LEN) );
        final int secondColumnIdx = Integer.parseInt( likeColumn.substring(COLUMN_LEN) );

        if (tableChunkList != null){
            try{
                List<Future<Integer>> futureList = new ArrayList<>(tableChunkList.size());

                for (int i = 0; i < tableChunkList.size(); i++){
                    final TableChunk tableChunk = tableChunkList.get(i);
                    Future<Integer> future = actionThreadPool.submit(new Callable<Integer>() {
                        @Override
                        public Integer call() throws Exception {
                            Header header = tableChunk.getHeader();
                            HeaderCell headerCell = header.getCell(firstColumnNo);

                            int resCode = -1;
                            if (headerCell.hasRange()){
                                String maxString = headerCell.getMaxString();
                                String minString = headerCell.getMinString();
                                resCode = checkRange(minString, maxString, operator, columnValue);
                            }

                            int cnt = 0;
                            if (resCode != 0){
                                if (resCode == -1){
                                    MyString myString = myStringThreadLocal.get();
                                    byte[] bytePool = bytePoolThreadLocal.get();

                                    MyIntArray posArray = intArrayThreadLocal.get();
                                    MyIntArray idArray = idArrayThreadLocal.get();

                                    //第一级过滤
                                    headerCell.getAll(bytePool, posArray);
                                    getFilterItemList(bytePool, posArray, idArray, columnValue, operator);

                                    //第二级过滤
                                    HeaderCell headerCell2 = header.getCell(secondColumnIdx);
                                    int [] idArr = idArray.getArray();

                                    switch (likeTypeEnum){
                                        case ALL_LIKE:
                                            for (int i = 0, len = idArray.getCursor(); i < len; i++){
                                                int id = idArr[i];
                                                headerCell2.getContent(id, myString);
                                                if (Utils.allLikeHandle(myRegexList, likeTypeEnum, myString.getBytes(), 0, myString.getLen())){
                                                    cnt++;
                                                }
                                            }
                                            break;
                                        case ANY_LIKE:

                                            for (int i = 0, len = idArray.getCursor(); i < len; i++){
                                                int id = idArr[i];
                                                headerCell2.getContent(id, myString);
                                                if (myRegexListWrap.hasPrefixTree()){
                                                    PrefixTreeNode prefixTreeNode = myRegexListWrap.getPrefixTreeNode();
                                                    if ( prefixTreeNode.findAny(myString.getBytes(), 0, myString.getLen()) ){
                                                        cnt++;
                                                    }
                                                }

                                                if (myRegexListWrap.hasSuffixTree()){
                                                    SuffixTreeNode suffixTreeNode = myRegexListWrap.getSuffixTreeNode();
                                                    if ( suffixTreeNode.findAny(myString.getBytes(), 0, myString.getLen()) ){
                                                        cnt++;
                                                    }
                                                }

                                                if (Utils.anyLikeHandle(myRegexListWrap.getSortedLikeStrList(), likeTypeEnum, myString.getBytes(), 0, myString.getLen())){
                                                    cnt++;
                                                }

                                            }

                                            break;
                                        case NONE_LIKE:
                                            for (int i = 0, len = idArray.getCursor(); i < len; i++){
                                                int id = idArr[i];
                                                headerCell2.getContent(id, myString);
                                                if (myRegexListWrap.hasPrefixTree()){
                                                    PrefixTreeNode prefixTreeNode = myRegexListWrap.getPrefixTreeNode();
                                                    if ( prefixTreeNode.findAny(myString.getBytes(), 0, myString.getLen()) ){
                                                        continue;
                                                    }
                                                }
                                                if (myRegexListWrap.hasSuffixTree()){
                                                    SuffixTreeNode suffixTreeNode = myRegexListWrap.getSuffixTreeNode();
                                                    if ( suffixTreeNode.findAny(myString.getBytes(), 0, myString.getLen()) ){
                                                        continue;
                                                    }
                                                }
                                                if (Utils.noneLikeHandle(myRegexListWrap.getSortedLikeStrList(), likeTypeEnum, myString.getBytes(), 0, myString.getLen())){
                                                    cnt++;
                                                }

                                            }
                                            break;
                                    }
                                }else{
                                    byte[] bytePool = bytePoolThreadLocal.get();
                                    MyIntArray posArray = intArrayThreadLocal.get();

                                    //全部满足从索引文件读取
                                    HeaderCell headerCell2 = header.getCell(secondColumnIdx);
                                    headerCell2.getAll(bytePool, posArray);
                                    int[] posArr = posArray.getArray();
                                    int startPos = 0;

                                    switch (likeTypeEnum){
                                        case ALL_LIKE:
                                            for (int id = 0, len = posArray.getCursor(); id < len; id++){
                                                if (Utils.allLikeHandle(myRegexList, likeTypeEnum, bytePool, startPos, posArr[id])){
                                                    cnt++;
                                                }
                                                startPos += posArr[id];
                                            }
                                            break;
                                        case ANY_LIKE:
                                            for (int id = 0, len = posArray.getCursor(); id < len; id++){

                                                if (myRegexListWrap.hasPrefixTree()){
                                                    PrefixTreeNode prefixTreeNode = myRegexListWrap.getPrefixTreeNode();
                                                    if ( prefixTreeNode.findAny(bytePool, startPos, posArr[id]) ){
                                                        cnt++;
                                                    }
                                                }

                                                if (myRegexListWrap.hasSuffixTree()){
                                                    SuffixTreeNode suffixTreeNode = myRegexListWrap.getSuffixTreeNode();
                                                    if ( suffixTreeNode.findAny(bytePool, startPos, posArr[id]) ){
                                                        cnt++;
                                                    }
                                                }

                                                if (Utils.anyLikeHandle(myRegexListWrap.getSortedLikeStrList(), likeTypeEnum, bytePool, startPos, posArr[id])){
                                                    cnt++;
                                                }

                                                startPos += posArr[id];
                                            }
                                            break;
                                        case NONE_LIKE:

                                            for (int id = 0, len = posArray.getCursor(); id < len; id++){
                                                if (myRegexListWrap.hasPrefixTree()){
                                                    PrefixTreeNode prefixTreeNode = myRegexListWrap.getPrefixTreeNode();
                                                    if ( prefixTreeNode.findAny(bytePool, startPos, posArr[id]) ){
                                                        continue;
                                                    }
                                                }
                                                if (myRegexListWrap.hasSuffixTree()){
                                                    SuffixTreeNode suffixTreeNode = myRegexListWrap.getSuffixTreeNode();
                                                    if ( suffixTreeNode.findAny(bytePool, startPos, posArr[id]) ){
                                                        continue;
                                                    }
                                                }
                                                if (Utils.noneLikeHandle(myRegexListWrap.getSortedLikeStrList(), likeTypeEnum, bytePool, startPos, posArr[id])){
                                                    cnt++;
                                                }

                                                startPos += posArr[id];
                                            }
                                            break;
                                    }
                                }

                            }
                            return cnt;
                        }
                    });
                    futureList.add(future);
                }

                try {
                    for (Future<Integer> future: futureList){
                        result += future.get();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println(e.getMessage());
                }

            }catch (Exception e){
                e.printStackTrace();
                System.err.println("action -> " + e.getMessage());
            }

        }

        return result;
    }

    private int checkRange(String minString, String maxString, String operator, String columnValue){
        int res = -1;   //0表示全部不符合 1表示全部符合 -1表其他

        switch (operator){
            case "=":
                if (columnValue.compareTo(minString) < 0 || columnValue.compareTo(maxString) > 0){
                    res = 0;
                }

                break;
            case "!=":

                break;
            case ">":
                if (columnValue.compareTo(minString) < 0){
                    res = 1;
                }

//
                if (columnValue.compareTo(maxString) > 0){
                    res = 0;
                }

                break;

            case "<":
                if (columnValue.compareTo(maxString) > 0){
                    res = 1;
                }

                if (columnValue.compareTo(minString) < 0){
                    res = 0;
                }
                break;
        }

        return res;
    }

    private void getFilterItemList(byte[] bytePool, MyIntArray posArray, MyIntArray idArray,
                                            String columnValue, String operator){
        idArray.reset();
        int startPos = 0;
        byte[] bytes = columnValue.getBytes();
        int[] posArr = posArray.getArray();

        //todo 如果列有特殊索引调用特殊算法处理
        switch (operator){
            case "=":

                for (int id = 0, len = posArray.getCursor(); id < len; id++){
                    if (Utils.byteCompare(bytePool, startPos, posArr[id], bytes, 0, bytes.length) == 0){
                        idArray.add(id);
                    }
                    startPos += posArr[id];
                }
                break;
            case "!=":
                for (int id = 0, len = posArray.getCursor(); id < len; id++){
                    if (Utils.byteCompare(bytePool, startPos, posArr[id], bytes, 0, bytes.length) != 0){
                        idArray.add(id);
                    }
                    startPos += posArr[id];
                }
                break;

            case ">":
                for (int id = 0, len = posArray.getCursor(); id < len; id++){
                    if (Utils.byteCompare(bytePool, startPos, posArr[id], bytes, 0, bytes.length) > 0){
                        idArray.add(id);
                    }
                    startPos += posArr[id];
                }
                break;

            case "<":
                for (int id = 0, len = posArray.getCursor(); id < len; id++){
                    if (Utils.byteCompare(bytePool, startPos, posArr[id], bytes, 0, bytes.length) < 0){
                        idArray.add(id);
                    }
                    startPos += posArr[id];
                }
                break;
        }
    }

    private List<Integer> getFilterItemList(List<Item> itemList, String columnValue, String operator){
        List<Integer> resultList = new ArrayList<>();
        int lowerPos = 0;
        int upperPos = 0;
        switch (operator){
            case "=":
//                lowerPos = Utils.lowerBound(itemList, columnValue);
//
//                upperPos = Utils.upperBound(itemList, columnValue);
//
//                if (lowerPos < upperPos){
//                    return itemList.subList(lowerPos, upperPos);
//                }else{
//                    return Collections.emptyList();
//                }
//                break;
                for (Item item : itemList){
                    if (item.getContent().equals(columnValue)){
                        resultList.add(item.getId());
                    }
                }
                break;
            case "!=":
//                lowerPos = Utils.lowerBound(itemList, columnValue);
//
//                upperPos = Utils.upperBound(itemList, columnValue);
//
//                if (-1 < lowerPos) {
//                    resultList.addAll(itemList.subList(0, lowerPos + 1));
//                }
//
//                if (upperPos-1 < itemList.size()) {
//                    resultList.addAll(itemList.subList(upperPos-1, itemList.size()));
//                }
//                break;
                for (Item item : itemList){
                    if (!item.getContent().equals(columnValue)){
                        resultList.add(item.getId());
                    }
                }
                break;

            case ">":
//                upperPos = Utils.upperBound(itemList, columnValue);
//                if (upperPos < itemList.size()){
//                    return itemList.subList(upperPos, itemList.size());
//                }
//                break;
                for (Item item : itemList){
                    if (item.getContent().compareTo(columnValue) > 0){
                        resultList.add(item.getId());
                    }
                }
                break;

            case "<":
//                lowerPos = Utils.lowerBound(itemList, columnValue);
//                if (0 < lowerPos){
//                    return itemList.subList(0, lowerPos);
//                }
//                break;
                for (Item item : itemList){
                    if (item.getContent().compareTo(columnValue) < 0){
                        resultList.add(item.getId());
                    }
                }
                break;
        }

        return resultList;
    }

    static ThreadPoolExecutor actionThreadPool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            0,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(1024*64));

    private int actionForPartition(String[] items){
        final String tableName = items[0];

        final String partitionKey = items[1];
        final String operator = items[2];
        final String partitionValue =  items[3].substring(1, items[3].length()-1);

        final String likeColumn = items[4];
        final String likeType = items[5];
        final String likeArgs = items[6];

        final LikeTypeEnum likeTypeEnum = Utils.getLikeTypeEnum(likeType);

        Table table = tableCollection.getTableMap().get(tableName);

        //分区裁剪
        PartitionIndex partitionIndex = table.getPartitionIndex();
        List<TableChunk> tableChunkList = partitionIndex.getTableChunk(partitionKey, partitionValue, operator);

        int result = 0;

        final int columnIndex = Integer.parseInt( likeColumn.substring(COLUMN_LEN) );

        List<String> likeStrList = new ArrayList<>();
        String cleanStr = likeArgs.substring(1, likeArgs.length() - 1);
        List<String> tokens = Utils.split(cleanStr,',');

        for (String token : tokens){
            String tmp = token.substring(1, token.length() - 1);
            likeStrList.add(tmp);
        }

        final List<MyRegex> myRegexList = Utils.sortRegexStrList(likeStrList);
        final MyRegexListWrap myRegexListWrap = new MyRegexListWrap();
        if (likeTypeEnum == LikeTypeEnum.ANY_LIKE || likeTypeEnum == LikeTypeEnum.NONE_LIKE){
            myRegexListWrap.init(myRegexList);
        }
        //todo 特殊情况处理

        if (tableChunkList != null){
            try{
                List<Future<Integer>> futureList = new ArrayList<>(tableChunkList.size());

                for (int i = 0; i < tableChunkList.size(); i++){
                    final TableChunk tableChunk = tableChunkList.get(i);
                    Future<Integer> future = actionThreadPool.submit(new Callable<Integer>() {
                        @Override
                        public Integer call() throws Exception {
                            Header header = tableChunk.getHeader();
                            HeaderCell headerCell = header.getCell(columnIndex);

                            byte[] bytePool = bytePoolThreadLocal.get();
                            MyIntArray posArray = intArrayThreadLocal.get();

                            headerCell.getAll(bytePool, posArray);
                            int[] posArr = posArray.getArray();
                            int startPos = 0;

                            int cnt = 0;

                            switch (likeTypeEnum){
                                case ALL_LIKE:
                                    for (int id = 0, len = posArray.getCursor(); id < len; id++){
                                        if (Utils.allLikeHandle(myRegexList, likeTypeEnum, bytePool, startPos, posArr[id])){
                                            cnt++;
                                        }
                                        startPos += posArr[id];
                                    }
                                    break;
                                case ANY_LIKE:
                                    for (int id = 0, len = posArray.getCursor(); id < len; id++){
                                        if (myRegexListWrap.hasPrefixTree()){
                                            PrefixTreeNode prefixTreeNode = myRegexListWrap.getPrefixTreeNode();
                                            if ( prefixTreeNode.findAny(bytePool, startPos, posArr[id]) ){
                                                cnt++;
                                            }
                                        }
                                        if (myRegexListWrap.hasSuffixTree()){
                                            SuffixTreeNode suffixTreeNode = myRegexListWrap.getSuffixTreeNode();
                                            if ( suffixTreeNode.findAny(bytePool, startPos, posArr[id]) ){
                                                cnt++;
                                            }
                                        }
                                        if (Utils.anyLikeHandle(myRegexListWrap.getSortedLikeStrList(), likeTypeEnum, bytePool, startPos, posArr[id])){
                                            cnt++;
                                        }

                                        startPos += posArr[id];
                                    }
                                    break;

                                case NONE_LIKE:
                                    for (int id = 0, len = posArray.getCursor(); id < len; id++){
                                        if (myRegexListWrap.hasPrefixTree()){
                                            PrefixTreeNode prefixTreeNode = myRegexListWrap.getPrefixTreeNode();
                                            if ( prefixTreeNode.findAny(bytePool, startPos, posArr[id]) ){
                                                continue;
                                            }
                                        }
                                        if (myRegexListWrap.hasSuffixTree()){
                                            SuffixTreeNode suffixTreeNode = myRegexListWrap.getSuffixTreeNode();
                                            if ( suffixTreeNode.findAny(bytePool, startPos, posArr[id]) ){
                                                continue;
                                            }
                                        }
                                        if (Utils.noneLikeHandle(myRegexListWrap.getSortedLikeStrList(), likeTypeEnum, bytePool, startPos, posArr[id])){
                                            cnt++;
                                        }

                                        startPos += posArr[id];
                                    }
                                    break;
                            }

                            //过滤
                            return cnt;
                        }
                    });
                    futureList.add(future);
                }

                try {
                    for (Future<Integer> future: futureList){
                        result += future.get();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println(e.getMessage());
                }
            }catch (Exception e){
                e.printStackTrace();
                System.err.println("actionForPartition -> " + e.getMessage());
            }
        }

        return result;
    }

    private int actionForPartitionExtern(String[] items){
        final String tableName = items[0];

        final String columnKey = items[1];
        final String operator = items[2];
        final String columnValue =  items[3].substring(1, items[3].length()-1);

        final String likeColumn = items[4];
        final String likeType = items[5];
        final String likeArgs = items[6];

        final LikeTypeEnum likeTypeEnum = Utils.getLikeTypeEnum(likeType);

        Table table = tableCollection.getTableMap().get(tableName);
        //分区裁剪
        PartitionIndex partitionIndex = table.getPartitionIndex();
        List<TableChunk> tableChunkList = partitionIndex.getTableChunkForLike(likeColumn, likeArgs, likeTypeEnum);

        //第二级过滤
        int result = 0;

        final int columnIndex = Integer.parseInt( columnKey.substring(COLUMN_LEN) );

        if (tableChunkList != null){
            try{
                List<Future<Integer>> futureList = new ArrayList<>(tableChunkList.size());

                for (int i = 0; i < tableChunkList.size(); i++){
                    final TableChunk tableChunk = tableChunkList.get(i);
                    Future<Integer> future = actionThreadPool.submit(new Callable<Integer>() {
                        @Override
                        public Integer call() throws Exception {
                            Header header = tableChunk.getHeader();
                            HeaderCell headerCell = header.getCell(columnIndex);
                            int resCode = -1;
                            if (headerCell.hasRange()){
                                String maxString = headerCell.getMaxString();
                                String minString = headerCell.getMinString();
                                resCode = checkRange(minString, maxString, operator, columnValue);
                            }

                            if (resCode != 0){
                                if (resCode == -1){
                                    byte[] bytePool = bytePoolThreadLocal.get();

                                    MyIntArray posArray = intArrayThreadLocal.get();
                                    MyIntArray idArray = idArrayThreadLocal.get();

                                    //第一级过滤
                                    headerCell.getAll(bytePool, posArray);
                                    getFilterItemList(bytePool, posArray, idArray, columnValue, operator);

                                    return idArray.getCursor();
                                }else{
                                    return headerCell.getEleSize();
                                }

                            }else {
                                return 0;
                            }
                        }
                    });
                    futureList.add(future);
                }

                try {
                    for (Future<Integer> future: futureList){
                        result += future.get();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println(e.getMessage());
                }

            }catch (Exception e){
                e.printStackTrace();
                System.err.println("actionForPartitionExtern -> " + e.getMessage());
            }

        }

        return result;
    }


    private void handleTable(File tableFile) throws UnsupportedEncodingException {
        System.out.println(String.format("handleTable table name: %s.", tableFile.getName()));

        levelIndex = new HashMap<String, TreeMap<String, List<TableChunk>>>();
        tableChunkCount = 0;

        File[] files = tableFile.listFiles();

        File firstFile = null;

        if(files != null){
            for (File file : files){
                if(file.getName().indexOf(".") == 0){
                    continue;
                }
                firstFile = file;
                break;
            }
        }

        Table table = new Table();
        List<TableChunk> tableChunkList = new ArrayList<TableChunk>();
        if(firstFile != null){
            if(hasPartition(firstFile)){
                tableChunkList = handlePartition(tableFile);
                table.setPartitionIndex(new PartitionIndex(levelIndex));
            }else{
                tableChunkList = handleFile(tableFile);
                table.setPartitionIndex(null);
            }
        }

        table.setTableChunkList(tableChunkList);
        tableCollection.addTable(tableFile.getName(), table);
    }

    private boolean hasPartition(File file){
        return file.isDirectory();
    }

    private List<TableChunk> handlePartition(File partitionTable) throws UnsupportedEncodingException {

        String[] arr = partitionTable.getName().split("=");
        String partitionKey = null;
        String partitionVal = null;
        if(arr.length == 2){
            partitionKey = arr[0];
            partitionVal = arr[1];
        }

        List<TableChunk> tableChunkList = new ArrayList<TableChunk>();

        File[] files = partitionTable.listFiles();

        File firstFile = null;

        //判断是否最后一层分区
        if(files != null){
            for (File file : files){
                if(file.getName().indexOf(".") == 0){
                    continue;
                }
                firstFile = file;
                break;
            }
        }

        if(firstFile != null){
            if(hasPartition(firstFile)){
                for(File file : files){
                    if(file.getName().indexOf(".") == 0){
                        continue;
                    }
                    List<TableChunk> partitionTableChunkList = handlePartition(file);

                    //合并数据
                    tableChunkList.addAll(partitionTableChunkList);
                }
            }else{
                tableChunkList = handleFile(partitionTable);
            }

            //构建分区索引
            if (partitionKey != null){
                TreeMap<String, List<TableChunk>> partitionMap = levelIndex.get(partitionKey);
                if(partitionMap == null){
                    partitionMap = new TreeMap<String, List<TableChunk>>();
                    levelIndex.put(partitionKey, partitionMap);
                }

                List<TableChunk> chunkList = partitionMap.get(partitionVal);
                if (chunkList == null){
                    chunkList = new ArrayList<TableChunk>();
                    partitionMap.put(partitionVal, chunkList);
                }
                chunkList.addAll(tableChunkList);
            }
        }

        return tableChunkList;
    }

    private List<TableChunk> handleFile(File tableFile) throws UnsupportedEncodingException {
        List<TableChunk> tableChunkList = new ArrayList<TableChunk>();

        String[] fileNames = tableFile.list((dir, name) -> {
            if(name.indexOf(".") == 0){
                return false;
            }
            return true;
        });

        tableChunkCount += fileNames.length;
        List<Future<TableChunk>> futureList = new ArrayList<>(tableChunkList.size());

        for (String fileName : fileNames){
            final String filePath = tableFile.getAbsolutePath() + "/" + fileName;
            Future<TableChunk> future = actionThreadPool.submit(new Callable<TableChunk>() {
                @Override
                public TableChunk call() throws Exception {
                    TableChunk tableChunk = createTableChunk(filePath);
                    //过滤
                    return tableChunk;
                }
            });
            futureList.add(future);
        }

        try {
            for (Future<TableChunk> future: futureList){
                tableChunkList.add(future.get());
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }

        return tableChunkList;
    }

    private TableChunk createTableChunk(String fileAbsPath) throws UnsupportedEncodingException {

        byte[] bytePool = bytePoolThreadLocal.get();

        File file = new File(fileAbsPath);
        long fileLen = file.length();
        int chunkCnt = 0;
        int filePos = 0;
        try(BufferedInputStream br = new BufferedInputStream(new FileInputStream(file), CHUNK_SIZE)){
            int readNum = 0;
            while( (readNum = br.read(bytePool, chunkCnt * CHUNK_SIZE, CHUNK_SIZE)) > 0)
            {
                filePos += readNum;
                chunkCnt++;
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        //计算列数
        int columnSize = 0;
        for (int i = 0; i < filePos; i++){
            if (bytePool[i] == '|'){
                columnSize++;
            }else if (bytePool[i] == '\n'){
                columnSize++;
                break;
            }
        }

        //计算行数
        int lineNum = 0;
        for (int i = 0; i < filePos; i++){
            if (bytePool[i] == '\n'){
                lineNum++;

//                rowCounter++;   //粗略统计不考虑线程安全
//                if( rowCounter % 1000000 == 0){
//                    System.out.println(String.format("rowCounter is %d.", rowCounter));
//                }
            }
        }

        //todo 封装行对象，进行对象复用
        int[][] rows = new int[lineNum + 1][columnSize + 1];

        int lineCursor = 0;
        int columnCursor = 1;

        rows[0][0] = 0;
        //索引行列位置
        for (int pos = 0; pos < filePos; pos++){
            if (bytePool[pos] == '|'){
                rows[lineCursor][columnCursor] = pos + 1;
                columnCursor++;
            }else if (bytePool[pos] == '\n'){
                if (bytePool[pos-1] == '\r'){
                    rows[lineCursor][columnCursor] = pos;
                }else{
                    rows[lineCursor][0] = pos + 1;
                }
                lineCursor++;
                rows[lineCursor][0] = pos + 1;
                columnCursor = 1;
            }
        }

        long baseAddress = UnsafeAllocator.allocate(fileLen);
        long baseAddressOff = baseAddress;

        TableChunk tableChunk = new TableChunk(fileAbsPath, lineNum);

        for(int columnIdx = 0; columnIdx < columnSize; columnIdx++){
            //构建列索引
            int[] columnPosIndex = new int[lineNum + 1];
            HeaderCell headerCell = new HeaderCell(baseAddressOff, columnPosIndex, lineNum);
            int posCursors = 0;

            //统计信息
            int maxLen = Integer.MIN_VALUE;
            int minLen = Integer.MAX_VALUE;
            int avgLen = 0;
            int totalLen = 0;

            int contentLen = 0;
            StringEntity minStringEntity = null;
            StringEntity maxStringEntity = null;

            for (int lineIdx = 0; lineIdx < lineNum; lineIdx++){
                int strLen = (rows[lineIdx][columnIdx + 1] - rows[lineIdx][columnIdx] - 1);
                columnPosIndex[lineIdx] = posCursors;
                posCursors += strLen;

                for (int ii = 0; ii < strLen; ii++){
                    UnsafeAllocator.setByte(baseAddressOff, bytePool[rows[lineIdx][columnIdx] + ii]);
                    baseAddressOff++;
                }

                //-----
                totalLen += strLen;

                if (maxLen < strLen){
                    maxLen = strLen;
                }

                if (minLen > strLen){
                    minLen = strLen;
                }

                //-----
                if (minStringEntity == null) {
                    minStringEntity = new StringEntity(bytePool, rows[lineIdx][columnIdx], strLen);
                } else {
                    if (minStringEntity.compare(rows[lineIdx][columnIdx], strLen) > 0) {
                        minStringEntity.setEntity(rows[lineIdx][columnIdx], strLen);
                    }
                }

                if (maxStringEntity == null) {
                    maxStringEntity = new StringEntity(bytePool, rows[lineIdx][columnIdx], strLen);
                } else {
                    if (maxStringEntity.compare(rows[lineIdx][columnIdx], strLen) < 0) {
                        maxStringEntity.setEntity(rows[lineIdx][columnIdx], strLen);
                    }
                }
            }
            //last pos
            columnPosIndex[lineNum] = posCursors;

            avgLen = totalLen / lineNum;
            boolean hasFixed = (maxLen == minLen);

            //构建列信息
            headerCell.setInfo(maxLen, minLen, avgLen, hasFixed);
            headerCell.setRange(minStringEntity.getString(), maxStringEntity.getString());
            tableChunk.addHeaderCell(headerCell);
        }

        return tableChunk;
    }

    static final class StringEntity{
        byte[] bytes;
        int startPos;
        int len;

        public StringEntity() {
        }

        public StringEntity(byte[] bytes, int startPos, int len) {
            this.bytes = bytes;
            this.startPos = startPos;
            this.len = len;
        }

        public void setEntity(int startPos, int len){
            this.startPos = startPos;
            this.len = len;
        }

        public int compare(StringEntity stringEntity){
            int len1 = len;
            int len2 = stringEntity.getLen();
            int lim = Math.min(len1, len2);
            int startPos1 = stringEntity.getStartPos();

            int k = 0;
            while (k < lim) {
                byte c1 = bytes[k + startPos];
                byte c2 = bytes[k + startPos1];
                if (c1 != c2) {
                    return c1 - c2;
                }
                k++;
            }
            return len1 - len2;
        }

        public int compare(int startPos1, int len2){
            int len1 = len;
            int lim = Math.min(len1, len2);

            int k = 0;
            while (k < lim) {
                byte c1 = bytes[k + startPos];
                byte c2 = bytes[k + startPos1];
                if (c1 != c2) {
                    return c1 - c2;
                }
                k++;
            }
            return len1 - len2;
        }

        public String getString(){
            if (bytes == null)
                return "";
            return new String(bytes, startPos, len);
        }

        public int getStartPos() {
            return startPos;
        }

        public int getLen() {
            return len;
        }

        public byte[] getBytes() {
            return bytes;
        }
    }

}
