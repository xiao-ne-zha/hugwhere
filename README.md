# hugwhere

该工具主要是方便配置hugsql的动态where语句。主要功能有
  * 根据参数中非nil值，动态拼接where语句，丢弃其中nil值参数相关的sql片段。如`where a = :a or b like :b` 在参数:b为nil时，输出`where a = :a`
  * 默认保留无变参部分的sql片段。如`where a=1 and b = :b` 在参数:b为nil时，输出结果为`where a=1`
  * 可以强制随变参存在或消失的sql片段。如`where [a=1 and b = :b]` 在参数:b为nil时，输出结果为nil
  * 支持where中使用函数、子查询（暂未实现)

## 使用方法

### 安装依赖

lein依赖中添加：`[org.to.vitory.db/hugwhere "0.1.0-SNAPSHOT"]`

### 在sql文件中的使用

## License

Copyright © 2019 xiao-ne-zha

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
