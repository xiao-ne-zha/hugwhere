# hugwhere
中文说明请参看 [README.cn](https://github.com/xiao-ne-zha/hugwhere/blob/master/README.cn.md)

## Usage

### Installation dependency

Added in lein dependency:
`[org.to.victory.db/hugwhere "0.1.0-SNAPSHOT"]`

### Initialize hugwhere in the code

    (require '[org.to.victory.db.hack-hugsql :as hh])
    (hh/hack-hugsql)

### Use in sql file
For example write your function in rechouces/xxx.sql.
  * Note: the `:D` is the key of hugwhere, and you can see the trick in the following: --~ where ... or /*~ where ... ~*/

```clojure
-- :name test1 :? :* :D
select * from users
--~ where a = 100 and b = :b or c like :c

-- :name list-users :? :* :D
select * from users
--~ where id = :id and name like :l:name and is_valid = 1

-- :name test2 :? :* :D
select * from users
--~ where [a = 100 and b = :b or c like :c]

-- :name list-users2 :? :* :D
select * from users
--~ where id = :id and [name like :l:name and is_valid = 1]

-- :name test-influence :? :* :D
select * from users
--~ where a = 1 and [b like :ll:b and c = 1] and [d = :d and e != 0]

-- :name test-func :? :* :D
select * from users
--~ where a=1 and b = f(:b,1) and c = fs(:c,:d)
```

### Use hugsql api to create these functions
For example , I use the def-sqlvec-fns

    (hugsql.core/def-sqlvec-fns "xxx.sql")

### Then you can test the flowing in repl:

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
["select * from users\nwhere id = ?" 1]
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
["select * from users\nwhere a = 1 and b = f(?,1)" "name"]
(test-func-sqlvec {:c 100}) =>
["select * from users\nwhere a = 1"]
(test-func-sqlvec {:c 100, :d 1}) =>
["select * from users\nwhere a = 1 and c = fs(?,?)" 100 1]
(test-func-sqlvec {:b "name", :c 100}) =>
["select * from users\nwhere a = 1 and b = f(?,1)" "name"]
(test-func-sqlvec {:b nil, :c 100, :d 1}) =>
["select * from users\nwhere a = 1 and c = fs(?,?)" 100 1]
```


## Examples project

[hello-hugwhere](https://github.com/xiao-ne-zha/hugwhere/tree/master/examples/hello-hugwhere)

## License

Copyright © 2019 xiao-ne-zha

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
