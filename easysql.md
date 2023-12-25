# 查询sql配置文件语法说明

考虑到增删改函数大多形式比较固定，所以可以通过程序固定下来，不需要配置对应的sql。
所以对应应用开发来说最多的情况是配置查询语句

## 查询sql文件配置约定
对查询所用的sql语句配置文件做如下约定, 依据命名的含义确定：

*1. 大括号 `{` 开头的行， 翻译为 `--~ {` 开头的行， 表示此行为动态sql行，需要自动根据传入参数是否为null值进行动态拼接
*2. 形如 `-- :name xxx-total ` 或 `-- :name xxx-count` 的行， 翻译为 `-- :name xxx-count :? :1 :D`, 表示这是一条返回单行数据的动态select语句
*3. 形如 `-- :name insert-xxx ` 的行， 翻译为 `-- :name insert-xxx :? :n :D`, 表示这是一条返回影响行数的插入语句
*4. 形如 `-- :name update-xxx ` 的行， 翻译为 `-- :name update-xxx :? :n :D`, 表示这是一条返回影响行数的修改语句
*5. 形如 `-- :name delete-xxx ` 的行， 翻译为 `-- :name delete-xxx :? :n :D`, 表示这是一条返回影响行数的删除语句
*6. 形如 `-- :name xxx ` 的行， 翻译为 `-- :name xxx :? :* :D`, 表示这是一条返回多行数据的动态select语句
*7. 其它形式开头的行全部不做翻译，即保持原样进行输出

## 排序的配置示例

使用`(order-by :order-by-property-name)`的方式进行配置，此时会将标识符用双引号包围起来（此为ansi标准）。

.sql文件中配置如下：

```sql
-- :name xxxxx
select * from xxxx
--~ (order-by :order_by)
```

或者简化配置为：

```sql
-- :name xxxxx
select * from xxxx
order-by :order_by
```

此时传递来的参数中 order_by 属性的值为 `["city" ["name" "desc"]]`, 翻译出来的sql语句为：

```sql
select * from xxxx
order by "city", "name" desc
```

此时传递来的参数中 order_by 属性的值为 `[["city" "asc"] ["name" "desc"]]`, 翻译出来的sql语句为：

```sql
select * from xxxx
order by "city" asc, "name" desc
```

若想调整标识符的引用符号从ansi标准的双引号，更改为mysql或mssql或关闭, 在sql配置中可以配置为

 `(order-by :order-by-property-name {:quoting :mysql})`
或
` (order-by :order-by-property-name {:quoting :mssql})`
或
` (order-by :order-by-property-name {:quoting :off})`

## 翻译的输出去哪里
经过listsql->hugsql函数的翻译，翻译的结果再交由hugsql进行分析，可以生成相关sql的查询函数
