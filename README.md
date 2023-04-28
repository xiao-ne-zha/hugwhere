# hugwhere
 Like sql => like hugsql => like hugwhere, yes, you do!
中文说明请参看 [README.cn](https://github.com/xiao-ne-zha/hugwhere/blob/master/README.cn.md)

This tool is mainly designed to facilitate the configuration of dynamic WHERE statements in HugSQL. Its main features include:

* 1. Dynamically concatenating WHERE statements based on non-nil parameters, discarding SQL fragments related to nil values. For example, where a = :a [[ or b like :b ]] will output where a = :a when the parameter b is nil.
* 2. By default, preserving SQL fragments without variable parts. For example, where a=1 [[ and b = :b ]] will output where a=1 when the parameter b is nil.
* 3. Forcing SQL fragments to be present or absent depending on the presence of variable parts. For example, where [[ a=1 and b = :b ]] will output nil when the parameter b is nil.
* 4. Supporting the use of functions within WHERE statements. Note that if the function uses a parameter, it should be separated from :x by spaces.
* 5. Adding three syntax options for the LIKE operator. Thanks to HugSQL's design, you can use these three syntax options directly in your SQL files once you've depended on this library. The three options are:
   *  5.1 Middle match: :like:value or abbreviated as :l:value. This will convert the passed-in value to the form %value%.
   *  5.2 Left match: :left-like:value or abbreviated as :ll:value. This will convert the passed-in value to the form value%.
   *  5.3 Right match: :right-like:value or abbreviated as :rl:value. This will convert the passed-in value to the form %value.

**Note: The dynamic part of the tool mainly relies on the double [[ ]] brackets to determine. The usage rules of brackets are as follows, which can be understood by referring to the examples used in the SQL file.**
* The part without brackets is considered as fixed SQL, which is output as it is without dynamic concatenation.
* The keyword parameter :x in the brackets must be separated by spaces before and after. The keyword parameters in the brackets are directly dependent on the SQL fragments in the brackets. When all the directly dependent parameters are not provided, all the SQL fragments in the brackets are discarded.
* The brackets can be nested in the brackets. The nested brackets are indirect dependencies. When there are only indirect dependencies inside a bracket, and all the indirect dependencies are empty, the SQL fragments inside the brackets are discarded. Otherwise, concatenate the SQL fragments inside the brackets.
* There should be spaces before and after the double brackets.
* The double brackets were changed from single brackets to avoid conflicts with the parentheses separators in SQL. Even so, to avoid conflicts with the double brackets, it is best to add spaces between consecutive single brackets in the SQL.

## Usage

### Installation dependency

Added in lein dependency:
`[org.tovictory.db/hugwhere "0.3.1"]`

### Initialize hugwhere in the code

    (require '[org.to.victory.db.hack-hugsql :as hh])
    (hh/hack-hugsql)

### Use in sql file
For example write your function in resources/xxx.sql.
  * Note: the `:D` is the key of hugwhere, and you can see the trick in the following: --~ where ... or /*~ where ... ~*/

```clojure
-- :name test1 :? :* :D
select * from users
--~ where a = 100 [[ and b = :b ]] [[ or c like :c ]]

-- :name list-users :? :* :D
select * from users
--~ where [[ id = :id and ]] [[ name like :l:name and ]] is_valid = 1

-- :name test2 :? :* :D
select * from users
--~ where [[ a = 100 [[ and b = :b ]] [[ or c like :c ]] ]]

-- :name list-users2 :? :* :D
select * from users
--~ where [[ [[ id = :id and ]] [[ name like :l:name and ]] is_valid = 1 ]]

-- :name test-influence :? :* :D
select * from users
--~ where a = 1 [[ and b like :ll:b and c = 1 ]] [[ and d = :d and e != 0 ]]

-- :name test-func :? :* :D
select * from users
--~ where a = 1 [[ and b = f( :b ,1) ]] [[ and c = fs( :c , :d ) ]]
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
