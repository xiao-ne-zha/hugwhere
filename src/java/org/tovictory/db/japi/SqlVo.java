package org.tovictory.db.japi;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
        sb.append(", args=");
        if (null == args) {
            sb.append("null");
        } else {
            sb.append('[');
            for (int i=0; i<args.size(); i++) {
                Object x = args.get(i);
                if ( null == x ) {
                    sb.append("null");
                } else if (x.getClass().isArray()) {
                    sb.append(Arrays.toString((Object[]) x));
                } else {
                    sb.append(x);
                }
                if (i < args.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SqlVo sqlVo = (SqlVo) o;

        if (!Objects.equals(sql, sqlVo.sql)) return false;
        return Objects.equals(args, sqlVo.args);
    }

    @Override
    public int hashCode() {
        int result = sql != null ? sql.hashCode() : 0;
        result = 31 * result + (args != null ? args.hashCode() : 0);
        return result;
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getArgs() {
        return args;
    }
}
