package org.tovictory.db.japi;

import java.util.List;

public class SqlVo {
    private final String sql;
    private final List<Object> args;

    public SqlVo(String sql, List<Object> args) {
        this.sql = sql;
        this.args = args;
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getArgs() {
        return args;
    }
}
