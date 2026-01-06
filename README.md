# hugwhere

如果你喜欢用SQL访问数据库，你一定会喜欢使用[hugsql](https://www.hugsql.org/)，而如果你使用hugsql，那么你一定也会喜欢用hugwhere来省去拼写动态WHERE条件的繁琐。

## 简介

该工具主要用于配置hugsql的动态WHERE语句。主要功能有：

1. **根据参数中非nil值，动态拼接WHERE语句**，丢弃其中nil值参数相关的SQL片段。例如 `where a = :a {{ or b like :b }}` 在参数`:b`为nil时，输出`where a = :a`

2. **默认保留无变参部分的SQL片段**。例如 `where a=1 {{ and b = :b }}` 在参数`:b`为nil时，输出结果为`where a=1`

3. **可以强制让SQL片段随变参存在或消失**。例如 `{{where a=1 and b = :b }}` 在参数`:b`为nil时，输出结果为nil

4. **增加了三种用于LIKE的值语法**，感谢hugsql的设计，当你依赖了本库就可以直接在SQL文件中使用这三种语法：
    - **中间像**: `:like:value` 或简写 `:l:value`，会将传入的`value`转变为 `%value%` 形式
    - **左边像**: `:left-like:value` 或简写 `:ll:value`，会将传入的`value`转变为 `value%` 形式
    - **右边像**: `:right-like:value` 或简写 `:rl:value`，会将传入的`value`转变为 `%value` 形式

## 重要说明

1.0.0版本是语法层面的最终版本，以后只会修复bug，不再做语法上的调整。
本次重大调整是将参数块的分隔符由双中括号 `[[` `]]` 改为 `{` `}`，主要原因是发现大括号在一般的SQL语句中被使用的更少。特别是PostgreSQL中，中括号在数组类型中很常用，但是大括号可以放心使用。

3.0.0版本重大变更（不向后兼容）：由于PostgreSQL中也使用大括号，为了避免冲突，参数块的分隔符已从单大括号 `{` `}` 改为双大括号 `{{` `}}`。现在可以安全地在SQL中使用单大括号，只有双大括号才具有特殊语法意义。**此变更不向后兼容**，使用此版本需要将原有SQL中的单大括号块语法 `{...}` 修改为双大括号 `{{...}}`。

## 安装依赖

### Leiningen

在依赖中添加：
```clojure
[org.clojars.xiao-ne-zha/hugwhere "3.0.0-SNAPSHOT"]
```

### Clojure CLI/deps.edn
```clojure
org.clojars.xiao-ne-zha/hugwhere {:mvn/version "3.0.0-SNAPSHOT"}
```

## 使用方法

### 1. 在代码中初始化hugwhere

在你的系统第一次访问数据库前，调用下面的代码（或者在系统初始化阶段调用）：

```clojure
(require '[org.tovictory.db.hug-params] ;; 引入此命名空间主要是为了使like参数生效
         '[org.tovictory.db.hugwhere :refer [smart-block order-by]])
;; 使用hugsql的相关sql函数生成方法，注意传递动态部分的req,如：

(hs/def-sqlvec-fns-from-string
  sqls
  {:require-str "[org.tovictory.db.hugwhere :refer [smart-block order-by not-nil? contain-para-name?]]"})
```

### 2. 在SQL文件中的使用

按照hugsql约定，在`resources/xxx.sql`里面写明你的函数，下面是几个例子：

```sql
-- :name test1 :? :*
select * from users
--~ where a = 100 {{ and b = :b }} {{ or c like :c }}

-- :name list-users :? :*
select * from users
--~ where {{ id = :id and }} {{ name like :l:name and }} is_valid = 1

-- :name test2 :? :* :D
select * from users
--~ where {{ a = 100 {{ and b = :b }} {{ or c like :c }} }}

-- :name list-users2 :? :*
select * from users
--~ where {{ {{ id = :id and }} {{ name like :l:name and }} is_valid = 1 }}

-- :name test-influence :? :*
select * from users
--~ where a = 1 {{ and b like :ll:b and c = 1 }} {{ and d = :d and e != 0 }}

-- :name test-func :? :*
select * from users
--~ where a = 1 {{ and b = f( :b ,1) }} {{ and c = fs( :c , :d ) }}
```

### 3. 根据上述文件生成数据库访问函数

这里为了方便说明问题，采用hugsql的`def-sqlvec-fns`来生成SQL语句函数。正常项目中，应该主要用的是`def-db-fns`来生成：

```clojure
(hugsql.core/def-sqlvec-fns "xxx.sql")
```

## 语法说明

双大括号的使用规则如下，可以参见SQL文件中使用的例子进行理解：

1. **无双大括号包括的部分**，认为是固定SQL，保留原样输出，不做动态拼接
2. **双大括号内的关键字参数**: `:x`，前面需有空格隔开。双大括号内的关键字参数为双大括号内SQL片段的直接依赖。当直接依赖的参数有一个未提供值时，双大括号内的SQL片段全部丢弃
3. **双大括号内可以嵌套双大括号**。嵌套的双大括号作为间接依赖。当一个双大括号内部，仅仅有间接依赖时，间接依赖全部为空，就丢弃双大括号内的SQL片段。否则，拼接双大括号内的SQL片段
4. **双大括号前后可以没有空格**

## 使用示例及结果

### 基本使用示例
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
```

### 复杂示例
```clojure
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
```

### 函数和复杂表达式示例
```clojure
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

(test-func-sqlvec {:c 100 :d 1}) =>
["select * from users\nwhere a = 1 and c = fs( ? , ? )" 100 1]

(test-func-sqlvec {:c 100}) =>
;; error :d must exist
```

## ORDER BY 支持

hugwhere还提供了`order-by`宏，用于动态生成ORDER BY子句。使用方法如下：

```clojure
;; 在SQL文件中
-- :name list-users-by-order :? :*
select * from users
--~ where is_valid = 1
--~ #order-by :order-by-cols

;; 在Clojure代码中调用
(list-users-by-order-sqlvec {:order-by-cols ["name" "age"]})
;; 会生成: ORDER BY name, age

(list-users-by-order-sqlvec {:order-by-cols [[:name "desc"] :age]})
;; 会生成: ORDER BY name DESC, age
```

## 测试支持

hugwhere还提供了两个辅助函数用于条件检查：
- `not-nil?`: 检查参数是否不为nil
- `contain-para-name?`: 检查参数是否包含指定名称

## Examples project

[hello-hugwhere](https://github.com/xiao-ne-zha/hugwhere/tree/master/examples/hello-hugwhere)

## License

Copyright © 2019 xiao-ne-zha

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.