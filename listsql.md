# 查询sql配置文件语法说明

考虑到增删改函数大多形式比较固定，所以可以通过程序固定下来，不需要配置对应的sql。
所以对应应用开发来说最多的情况是配置查询语句

## 查询sql文件配置约定
对查询所用的sql语句配置文件做如下约定：
*1. 形如 `-- :list list-sql-id` 的行， 翻译为 `-- :name list-sql-id :? :* :D`, 表示这是一条返回多行数据的动态select语句
*2. 大括号 `{` 开头的行， 翻译为 `--~ {` 开头的行， 表示此行为动态sql行，需要自动根据传入参数是否为null值进行动态拼接
*3. 形如 `-- :count count-sql-id` 的行， 翻译为 `-- :name count-sql-id :? :1 :D`, 表示这是一条返回单行的动态select语句。
*4. 其它形式开头的行全部不做翻译，即保持原样进行输出

## 翻译的输出去哪里
经过listsql->hugsql函数的翻译，翻译的结果再交由hugsql进行分析，可以生成相关sql的查询函数