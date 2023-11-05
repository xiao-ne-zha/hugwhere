# hugwhere
我想如果你喜欢用sql访问数据库，你会喜欢使用[hugsql](https://www.hugsql.org/),那么你一定会喜欢用hugwhere来省去拼写动态where条件的繁琐。

该工具主要是方便配置hugsql的动态where语句。主要功能有
  * 1.根据参数中非nil值，动态拼接where语句，丢弃其中nil值参数相关的sql片段。如`where a = :a { or b like :b }` 在参数:b为nil时，输出`where a = :a`
  * 2.默认保留无变参部分的sql片段。如`where a=1 { and b = :b }` 在参数:b为nil时，输出结果为`where a=1`
  * 3.可以强制随变参存在或消失的sql片段。如`{where a=1 and b = :b }` 在参数:b为nil时，输出结果为nil
  * 4.增加了三种用于like的值语法，感谢hugsql的设计，当你依赖了本库就可以直接在sql文件中使用着三种语法。这三种分别是
    * 4.1 中间像: `:like:value` 或 简写 `:l:value`, 会将传入的`value`转变为 `%value%` 形式
    * 4.2 左边像: `:left-like:value` 或简写 `:ll:value`, 会将传入的`value`转变为 `value%` 形式
    * 4.3 右边像: `:right-like:value` 或简写 `:rl:value`, 会将传入的`value`转变为 `%value` 形式

注意：动态部分，主要靠双{ }大括号来确定，大括号的使用规则如下，可以参见sql文件中使用的例子进行理解。
  * 1. 无大括号包括的部分，认为是固定sql，保留原样输出，不做动态拼接
  * 2. 大括号内的关键字参数 :x , 前面需有空格隔开。大括号内的关键字参数为大括号内sql片段的直接依赖。当直接依赖的参数有一个未提供值时，大括内的sql片段全部丢弃
  * 3. 大括号内可以嵌套大括号。 嵌套的大括号作为间接依赖。当一个大括号内部，仅仅有间接依赖时，间接依赖全部为空，就丢弃大括号内的sql片段。否则，拼接大括内的sql片段。
  * 4. 大括号前后可以没有空格。

## 重要说明
该1.0.0版本是语法层面最终版本，以后只会修复bug.不再做语法上面的调整。
本次重大调整是将参数块的分隔符由 双中括号 `[[` `]]` 改为 `{` `}`, 主要原因是发现大括号在一般的sql语句中被使用的更少。特别是postgresql中，中括号在数组类型中很常用，但是大括号可以放心使用。

## 使用方法

### 安装依赖

lein依赖中添加：`[org.to.victory.db/hugwhere "1.0.0"]`

### 在代码中初始化hugwhere

在你的系统第一次访问数据库前，调用下面的代码（或者一个较好的实践是系统初始化阶段调用）

    (require '[org.tovictory.db.hack-hugsql :as hh])
    (hh/hack-hugsql)


### 在sql文件中的使用
按hugsql约定，在resources/xxx.sql里面写明你的函数，下面是几个例子
注意在 :name 行的最后增加 :D 是打开动态where的开关。 而 --~ where ... 是单行动态where的写法， /*~ where ... ~*/ 是多行动态where的写法

```clojure
-- :name test1 :? :* :D
select * from users
--~ where a = 100 { and b = :b } { or c like :c }

-- :name list-users :? :* :D
select * from users
--~ where { id = :id and } { name like :l:name and } is_valid = 1

-- :name test2 :? :* :D
select * from users
--~ where { a = 100 { and b = :b } { or c like :c } }

-- :name list-users2 :? :* :D
select * from users
--~ where { { id = :id and } { name like :l:name and } is_valid = 1 }

-- :name test-influence :? :* :D
select * from users
--~ where a = 1 { and b like :ll:b and c = 1 } { and d = :d and e != 0 }

-- :name test-func :? :* :D
select * from users
--~ where a = 1 { and b = f( :b ,1) } { and c = fs( :c , :d ) }
```

### 根据上述文件生成数据库访问函数
这里为了方便说明问题，采用hugsql的def-sqlvec-fns来生成sql语句函数。正常项目中，应该主要用的是def-db-fns来生成

    (hugsql.core/def-sqlvec-fns "xxx.sql")

### 然后你就可以在repl里面测试一下内容了

```clojure
(test1-sqlvec nil) =>
["select * from users\nwhere a = 100"]
(test1-sqlvec {:b nil}) =>
["select * from users\nwhere a = 100"]
(test1-sqlvec {:b 1}) =>
["select * from users\nwhere a = 100 and b = ?" 1]
(test1-sqlvec {:c "name"}) =>
["select * from users\nwhere a = 100 or c like ?" "name"]
(test1-sqlvec {:b 1, :c "x"}) =>
["select * from users\nwhere a = 100 and b = ? or c like ?" 1 "x"]
(list-users-sqlvec nil) =>
["select * from users\nwhere is_valid = 1"]
(list-users-sqlvec {:id 1}) =>
["select * from users\nwhere id = ? and is_valid = 1" 1]
(list-users-sqlvec {:name "nezha"}) =>
["select * from users\nwhere name like ? and is_valid = 1" "%nezha%"]
(list-users-sqlvec {:id 1, :name "nezha"}) =>
["select * from users\nwhere id = ? and name like ? and is_valid = 1" 1 "%nezha%"]
(test2-sqlvec nil) =>
["select * from users"]
(test2-sqlvec {:b nil}) =>
["select * from users"]
(test2-sqlvec {:b 1}) =>
["select * from users\nwhere a = 100 and b = ?" 1]
(test2-sqlvec {:c "name"}) =>
["select * from users\nwhere a = 100 or c like ?" "name"]
(test2-sqlvec {:b 1, :c "x"}) =>
["select * from users\nwhere a = 100 and b = ? or c like ?" 1 "x"]
(list-users2-sqlvec nil) =>
["select * from users"]
(list-users2-sqlvec {:id 1}) =>
["select * from users\nwhere id = ? and is_valid = 1" 1]
(list-users2-sqlvec {:name "nezha"}) =>
["select * from users\nwhere name like ? and is_valid = 1" "%nezha%"]
(list-users2-sqlvec {:id 1, :name "nezha"}) =>
["select * from users\nwhere id = ? and name like ? and is_valid = 1" 1 "%nezha%"]
(test-influence-sqlvec nil) =>
["select * from users\nwhere a = 1"]
(test-influence-sqlvec {:b "x"}) =>
["select * from users\nwhere a = 1 and b like ? and c = 1" "x%"]
(test-influence-sqlvec {:d 100}) =>
["select * from users\nwhere a = 1 and d = ? and e != 0" 100]
(test-influence-sqlvec {:b "x", :d 100}) =>
["select * from users\nwhere a = 1 and b like ? and c = 1 and d = ? and e != 0" "x%" 100]
(test-func-sqlvec nil) =>
["select * from users\nwhere a = 1"]
(test-func-sqlvec {:b "name"}) =>
["select * from users\nwhere a = 1 and b = f( ? ,1)" "name"]
(test-func-sqlvec {:c 100}) =>
;; error :d must exists
(test-func-sqlvec {:c 100, :d 1}) =>
["select * from users\nwhere a = 1 and c = fs( ? , ? )" 100 1]
(test-func-sqlvec {:b "name", :c 100}) =>
;; error :d must exists
(test-func-sqlvec {:b nil, :c 100, :d 1}) =>
["select * from users\nwhere a = 1 and c = fs( ? , ? )" 100 1]
```


## Examples project

[hello-hugwhere](https://github.com/xiao-ne-zha/hugwhere/tree/master/examples/hello-hugwhere)

## License

Copyright © 2019 xiao-ne-zha

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
