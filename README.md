# hugwhere

该工具主要是方便配置hugsql的动态where语句。主要功能有
  * 1.根据参数中非nil值，动态拼接where语句，丢弃其中nil值参数相关的sql片段。如`where a = :a or b like :b` 在参数:b为nil时，输出`where a = :a`
  * 2.默认保留无变参部分的sql片段。如`where a=1 and b = :b` 在参数:b为nil时，输出结果为`where a=1`
  * 3.可以强制随变参存在或消失的sql片段。如`where [a=1 and b = :b]` 在参数:b为nil时，输出结果为nil
  * 4.支持where中使用函数、子查询（暂未实现)
  * 5.增加了三种用于like的值语法，感谢hugsql的设计，当你依赖了本库就可以直接在sql文件中使用着三种语法。这三种分别是
    * 5.1 中间像: `:like:value` 或 简写 `:l:value`, 会将传入的`value`转变为 `%value%` 形式
    * 5.2 左边像: `:left-like:value` 或简写 `:ll:value`, 会将传入的`value`转变为 `value%` 形式
    * 5.3 右边像: `:right-like:value` 或简写 `:rl:value`, 会将传入的`value`转变为 `%value` 形式

## 使用方法

### 安装依赖

lein依赖中添加：`[org.to.vitory.db/hugwhere "0.1.0-SNAPSHOT"]`

### 在sql文件中的使用
按hugsql约定，在resouces/xxx.sql里面写明你的函数，下面是几个例子

#### 定义一个一般动态sql函数

    -- :name list-users :? :*
    -- :doc 固定条件默认保留，动态条件根据参数是否为nil保留
    select * from users
    --~ (where params "id = :id and name like :l:name and is_valid = 1")

    以上函数，调用时实际执行的sql如下：
    (list-users conn {}) => select * from users where is_valid = 1
    (list-users conn {:id 1}) => select * from users where id = 1 and is_valid = 1
    (list-users conn {:name "nezha"}) => select * from users where name like '%nezha%' and is_valid = 1
    (list-users conn {:id 1, :name "nezha"}) => select * from users where id = 1 and name like '%nezha%' and is_valid = 1

#### 定义一个随动态参数保留的固定条件

    -- :name list-users2
    -- :doc 固定条件随参数动态去留
    select * from users
    --~ (where params "id = :id and [name like :l:name and is_valid = 1]")

    以上函数，调用时实际执行的sql如下：
    (list-users2 conn {}) => select * from users
    (list-users2 conn {:name "nezha"} => select * from users where name like '%nezha%' and is_valid = 1
    (list-users2 conn {:id 1} => select * from users where id = 1
    (list-users2 conn {:id 1, :name "nezha"}) => select * from users where id = 1 and name like '%nezha%' and is_valid = 1

## License

Copyright © 2019 xiao-ne-zha

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
