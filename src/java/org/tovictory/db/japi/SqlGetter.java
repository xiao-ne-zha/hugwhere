package org.tovictory.db.japi;

import java.util.Map;

public interface SqlGetter {
    public SqlVo getSql(String sqlFname, Map<String, Object> args);
}
