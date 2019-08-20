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
write your function in rechouces/xxx.sql.
  * Note: the `:D` is the key of hugwhere

Here are a few examples:

#### Define a general dynamic sql function

    -- :name list-users :? :* :D
    -- :doc keep the constant condition in the sql
    select * from users
    --~ where id = :id and name like :l:name and is_valid = 1

    the action will be：
    (list-users conn {}) => select * from users where is_valid = 1
    (list-users conn {:id 1}) => select * from users where id = 1 and is_valid = 1
    (list-users conn {:name "nezha"}) => select * from users where name like '%nezha%' and is_valid = 1
    (list-users conn {:id 1, :name "nezha"}) => select * from users where id = 1 and name like '%nezha%' and is_valid = 1

#### Define a constant condition that is retained with dynamic parameters

    -- :name list-users2 :? :* :D
    -- :doc constant condition will keep or not with there friend condition
    select * from users
    --~ where id = :id and [name like :l:name and is_valid = 1]

    the action will be:
    (list-users2 conn {}) => select * from users
    (list-users2 conn {:name "nezha"} => select * from users where name like '%nezha%' and is_valid = 1
    (list-users2 conn {:id 1} => select * from users where id = 1
    (list-users2 conn {:id 1, :name "nezha"}) => select * from users where id = 1 and name like '%nezha%' and is_valid = 1

## Examples

[hello-hugwhere](https://github.com/xiao-ne-zha/hugwhere/tree/master/examples/hello-hugwhere)

## License

Copyright © 2019 xiao-ne-zha

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
