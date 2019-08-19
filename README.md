# hugwhere
中文说明请参看 [README.cn](https://github.com/xiao-ne-zha/hugwhere/blob/master/README.cn.md)

This tool is mainly to facilitate the configuration of the dynamic where statement of hugsql. The main functions are
  * 1.According to the non-nil value in the parameter, dynamically splicing the where statement, discarding the sql fragment related to the nil value parameter. For example, `where a = :a or b like :b` when the parameter `:b` is nil, the output `where a = :a`
  * 2.By default, the sql fragment without the variable part is retained. For example, `where a=1 and b = :b` when the parameter `:b` is nil, the output iswhere a=1 `where a=1`
  * 3.It is possible to force the sql fragment to exist or disappear with the variable parameter. For example, `where [a=1 and b = :b]` when the parameter `:b` is nil, the output is nil.
  * 4.Support the use of functions, subqueries (not yet implemented)
  * 5.Added three value syntax for like, thanks to the design of hugsql, you can use three syntax directly in the sql file when you rely on this library. These three are
    * 5.1 middle like or normal like: `:like:value` or short `:l:value`, will transform `value` to `%value%`
    * 5.2 left like: `:left-like:value` or short `:ll:value`, will transform `value` to `value%`
    * 5.3 right like: `:right-like:value` or short `:rl:value`, will transform `value` to `%value`

## Usage

### Installation dependency

Added in lein dependency:`[org.to.victory.db/hugwhere "0.1.0-SNAPSHOT"]`

### Initialize hugwhere in the code
Call the following code before your system first accesses the database (or a good practice is to call the system initialization phase)


    (require '[org.to.victory.db.hack-hugsql :as hh])
    (hh/hack-hugsql)


### Use in sql file
According to the hugsql convention, write your function in rechouces/xxx.sql.
  * Note: the `:D` is the key of hugwhere

Here are a few exampls:

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
