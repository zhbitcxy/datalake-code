package com.zhbitcxy.tableInfo;

public class Row {
    private int lineOffset;
    private int[] cells;
    private int cellCursor;

    public Row(int lineOffset, int cellSize) {
        this.lineOffset = lineOffset;
        cells = new int[cellSize];
        cellCursor = 0;
    }

    public void addCell(int cellOffset){
        this.cells[cellCursor] = cellOffset;
        cellCursor++;
    }

    public long getLineOffset() {
        return lineOffset;
    }

    public int[] getCells() {
        return cells;
    }

    public String getCellString(final byte[] bytePool, int i) {
        return new String(bytePool, (lineOffset + cells[i]), (lineOffset + (cells[i+1] - cells[i]) - 1));
    }
}
