package com.zhbitcxy.tableInfo;

public class Item implements Comparable{
    private String content;
    private int id;

    public Item() {
    }

    public Item(String content, int id) {
        this.content = content;
        this.id = id;
    }


    @Override
    public int compareTo(Object o) {
        Item item = (Item)o;

        return content.compareTo(item.getContent());
    }

    public int compareTo(String str) {
        return content.compareTo(str);
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
