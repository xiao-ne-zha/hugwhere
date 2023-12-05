package org.tovictory.db.japi;

import java.util.List;

public class SqlVo {
    private final String sql;
    private final List<Object> args;

    public SqlVo(String sql, List<Object> args) {
        this.sql = sql;
        this.args = args;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SqlVo{");
        sb.append("sql='").append(sql).append('\'');
        sb.append(", args=").append(args);
        sb.append('}');
        return sb.toString();
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getArgs() {
        return args;
    }
}
