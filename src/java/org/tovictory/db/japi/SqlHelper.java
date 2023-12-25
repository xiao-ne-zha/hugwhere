package org.tovictory.db.japi;

import java.util.Map;

public interface SqlHelper {
    public SqlVo getSql(String sqlFname, Map<String, Object> args);
}
