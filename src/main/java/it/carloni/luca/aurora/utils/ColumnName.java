package it.carloni.luca.aurora.utils;

public enum ColumnName {

    DT_INSERIMENTO("dt_inserimento"),
    DT_RIFERIMENTO("dt_riferimento"),
    ROW_COUNT("row_count"),
    ROW_ID("row_id"),
    TS_INSERIMENTO("ts_inserimento");

    private final String name;

    public String getName() { return name; }

    ColumnName(String name) { this.name = name; }
}
