# EasySQL 语句编写指南

## 目录

1. [概述](#概述)
2. [基本语法](#基本语法)
3. [参数语法](#参数语法)
4. [动态块语法](#动态块语法)
5. [动态列选择](#动态列选择)
6. [高级特性](#高级特性)
7. [完整示例](#完整示例)
8. [最佳实践](#最佳实践)

---

## 概述

EasySQL 是基于 HugSQL 的 SQL 模板语言扩展，提供了更简洁的语法和强大的动态 SQL 功能。它支持：

- **参数化查询**：防止 SQL 注入
- **动态块**：根据参数动态生成 SQL 片段
- **动态列选择**：支持运行时动态指定查询列
- **类型化参数**：支持不同的参数类型（如 `:l:value` 表示 LIKE 参数）
- **元数据提取**：自动提取参数信息和结果列信息

---

## 基本语法

### 语句结构

一个 EasySQL 语句由以下元素组成：

```
statement = (other | parameter | block | select-list)+
```

- **other**: 普通 SQL 文本
- **parameter**: 参数占位符（如 `:id`）
- **block**: 动态块（用 `{{}}` 包围）
- **select-list**: 动态列选择（用 `[[]]` 包围）

### 注释语法

```sql
-- :name 语句ID  [类型声明]
-- 普通注释内容
```

---

## 参数语法

### 参数格式

参数使用冒号 `:` 开头，支持两种格式：

1. **简单参数**：`:param_name`
2. **带类型参数**：`:type:param_name`

### 常用参数类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `:v` | SQL 值参数（默认类型） | `:id` |
| `:l` | LIKE 参数 - 中间相似（自动添加 `%value%`） | `:l:name` |
| `:ll` | LIKE 参数 - 左边相似（自动添加 `value%`） | `:ll:code` |
| `:rl` | LIKE 参数 - 右边相似（自动添加 `%value`） | `:rl:email` |
| `:i` | SQL 标识符（表名、列名等，自动添加引号） | `:i:table_name` |
| `:sql` | 原始 SQL 片段（直接插入 SQL） | `:sql:order_clause` |
| `:v*` | SQL 值数组（展开为多个值） | `:v*:ids` |
| `:t` | 元组（自动添加括号，支持不同类型值） | `:t:coord` |
| `:t*` | 元组数组（展开为多个带括号的元组） | `:t*:coords` |
| `:i*` | 标识符数组（展开为多个带引号的标识符） | `:i*:columns` |
| `:sql*` | SQL 片段数组（展开为多个 SQL 片段） | `:sql*:conditions` |

### 参数命名规则

- 以 `_` 开头的参数为系统参数（如 `:_order_by`、`:_limit`）
- 支持嵌套路径：`:user.profile.name`
- 参数名可包含：字母、数字、连字符、点号

### 参数类型详解

#### LIKE 参数类型

LIKE 参数用于模糊查询，自动添加百分号包装：

```sql
-- :l - 中间相似（包含）
SELECT * FROM users WHERE name LIKE :l:name
-- 参数: {:name "John"}
-- 生成: WHERE name LIKE '%John%'

-- :ll - 左边相似（以...开头）
SELECT * FROM products WHERE code LIKE :ll:code
-- 参数: {:code "PROD"}
-- 生成: WHERE code LIKE 'PROD%'

-- :rl - 右边相似（以...结尾）
SELECT * FROM users WHERE email LIKE :rl:email
-- 参数: {:email "@example.com"}
-- 生成: WHERE email LIKE '%@example.com'
```

#### 标识符参数

标识符参数用于动态指定表名、列名等，自动添加引号防止注入：

```sql
-- :i - 单个标识符
SELECT * FROM :i:table_name WHERE status = :status
-- 参数: {:table_name "users" :status "active"}
-- 生成: SELECT * FROM "users" WHERE status = 'active'

-- :i* - 标识符数组
SELECT :i*:columns FROM users
-- 参数: {:columns ["id" "name" "email"]}
-- 生成: SELECT "id", "name", "email" FROM users
```

#### SQL 片段参数

SQL 片段参数允许直接插入 SQL 代码（需谨慎使用）：

```sql
-- :sql - 单个 SQL 片段
SELECT * FROM users WHERE 1 = 1 :sql:extra_condition
-- 参数: {:extra_condition "AND status = 'active'"}
-- 生成: SELECT * FROM users WHERE 1 = 1 AND status = 'active'

-- :sql* - SQL 片段数组
SELECT * FROM users WHERE 1 = 1 :sql*:conditions
-- 参数: {:conditions ["AND status = 'active'" "AND create_date > '2024-01-01'"]}
-- 生成: SELECT * FROM users WHERE 1 = 1 AND status = 'active' AND create_date > '2024-01-01'
```

#### 值数组参数

值数组参数用于 IN 子句：

```sql
-- :v* - 值数组
SELECT * FROM users WHERE id IN (:v*:ids)
-- 参数: {:ids [1 2 3 4 5]}
-- 生成: SELECT * FROM users WHERE id IN (1, 2, 3, 4, 5)
```

#### 元组参数

元组参数用于表示包含多个值的组合，自动添加括号：

**元组 vs 值数组的区别：**
1. 元组自动添加左右括号
2. 元组中的值可以有不同的数据类型

```sql
-- :t - 单个元组
-- 用于表示坐标点、范围查询等
INSERT INTO points (x, y) VALUES :t:coord
-- 参数: {:coord [100.5 200.3]}
-- 生成: INSERT INTO points (x, y) VALUES (100.5, 200.3)

-- :t - 不同类型的值
INSERT INTO events (user_id, event_type, created_at)
VALUES :t:event
-- 参数: {:event [123 "login" "2024-01-15 10:30:00"]}
-- 生成: INSERT INTO events (user_id, event_type, created_at)
--      VALUES (123, 'login', '2024-01-15 10:30:00')

-- :t* - 元组数组
-- 用于批量插入或多值比较
INSERT INTO points (x, y) VALUES :t*:coords
-- 参数: {:coords [[1.0 2.0] [3.5 4.2] [5.1 6.8]]}
-- 生成: INSERT INTO points (x, y) VALUES (1.0, 2.0), (3.5, 4.2), (5.1, 6.8)

-- :t* - 用于多值比较（组合条件）
SELECT * FROM ranges
WHERE (start_x, start_y) IN (:t*:start_points)
-- 参数: {:start_points [[10 20] [30 40] [50 60]]}
-- 生成: SELECT * FROM ranges
--      WHERE (start_x, start_y) IN ((10, 20), (30, 40), (50, 60))
```

### 示例

```sql
-- 简单参数
SELECT * FROM users WHERE id = :id

-- LIKE 参数（中间相似）
SELECT * FROM users WHERE name LIKE :l:name

-- LIKE 参数（左边相似）
SELECT * FROM products WHERE code LIKE :ll:code

-- LIKE 参数（右边相似）
SELECT * FROM users WHERE email LIKE :rl:email

-- 标识符参数（动态表名）
SELECT * FROM :i:table_name WHERE status = :status

-- 值数组参数（IN 子句）
SELECT * FROM users WHERE id IN (:v*:ids)

-- 元组参数（坐标点）
INSERT INTO points (x, y, z) VALUES :t:location

-- 元组数组参数（批量插入）
INSERT INTO points (x, y) VALUES :t*:coords

-- 元组数组参数（组合条件查询）
SELECT * FROM rectangles
WHERE (width, height) IN (:t*:sizes)

-- 嵌套路径参数
SELECT * FROM users WHERE department_id = :dept.id

-- 多个参数组合
SELECT * FROM users
WHERE id = :id
  AND status = :status
  AND name LIKE :l:name
  AND create_date >= :start_date
```

---

## 动态块语法

### 基本语法

动态块用双大括号 `{{}}` 包围，根据参数值动态决定是否包含该块。

```
block = <'{{'> (other | parameter | block)+ <'}}'>
```

### 块的保留规则

1. **有参数的块**：当块内任意参数值为 `nil` 时，整个块被移除
2. **无参数的块**：当所有子块都为 `nil` 时，该块被移除
3. **否则**：保留块的所有内容

### 语法形式

EasySQL 提供三种动态语法，它们都使用 `smart-block` 函数处理，但作用范围和行为不同：

#### 1. 双大括号语法 `{{ }}`（推荐）

`{{ }}` 可以在行内**部分使用**，只有双大括号**内部**的内容是动态的：

```sql
SELECT * FROM users
WHERE 1 = 1
{{ AND status = :status }}
{{ AND name LIKE :l:name }}
{{ AND create_date >= :create_date_start }}
```

**行内混合使用示例：**
```sql
-- {{ }} 外的内容是静态的，{{ }} 内的内容是动态的
{{ AND name = :name }} AND address = :address
```

**当 `name` 为 nil，`address` 有值时：**
```sql
-- 生成
AND address = ?
```

**当 `name` 和 `address` 都有值时：**
```sql
-- 生成
AND name = ? AND address = ?
```

**特点：**
- `{{ }}` 内的参数使用 `not-nil?` 谓词检查（值为 nil 时移除）
- 可以在同一行混合使用静态和动态内容
- 支持嵌套

#### 2. `--$` 语法

`--$` 将**整行**（去掉 `--$` 后）传递给 `smart-block`，如果行内包含 `{{ }}` 或 `[[]]`，则这些部分是动态的：

```sql
SELECT * FROM users
WHERE 1 = 1
--$ {{ AND status = :status }}
--$ {{ AND name LIKE :l:name }}
```

**行内混合使用示例：**
```sql
--$ {{ AND name = :name }} AND address = :address
```

**当 `name` 为 nil，`address` 有值时：**
```sql
-- 生成
AND address = ?
```

**与 `{{` 语法的关系：**
```sql
-- 下面两行是等效的
{{ AND name = :name }} AND address = :address
--$ {{ AND name = :name }} AND address = :address
```

**使用建议：**
- `--$` 主要用于**标记整行**需要特殊处理的情况
- 一般情况推荐使用 `{{ }}` 语法，更直观
- `--$` 可以配合其他 `--~` 指令使用

#### 3. `--@` 语法（键存在检查）

`--@` 将整行传递给 `smart-block`，但使用 `contain-para-name?` 谓词，**只检查键是否存在，不检查值**：

```sql
UPDATE users
SET id = :id
--@ {{, name = :name}}{{, email = :email}}
--@ {{, status = :status}}
WHERE id = :id
```

**使用场景：需要区分"键不存在"和"值为 nil"**

```sql
-- 当 params 为 {:id 1} 时
-- 生成: UPDATE users SET id = 1 WHERE id = 1

-- 当 params 为 {:id 1 :name nil} 时
-- 生成: UPDATE users SET id = 1, name = NULL WHERE id = 1
-- 注意：即使 name 的值为 nil，该列仍会被更新为 NULL
```

**三种语法对比：**

| 特性 | `{{ }}` | `--$` | `--@` |
|------|---------|-------|-------|
| 作用范围 | `{{ }}` 内部 | 整行 | 整行 |
| 谓词函数 | `not-nil?` | `not-nil?` | `contain-para-name?` |
| 检查条件 | 值不为 nil | 值不为 nil | 键存在 |
| 值为 nil 时 | 移除块 | 移除行内动态部分 | **保留行** |
| 键不存在时 | 移除块 | 移除行内动态部分 | 移除行 |
| 行内混合 | ✅ 支持 | ✅ 支持 | ✅ 支持 |
| 推荐场景 | 一般查询条件 | 特殊标记 | UPDATE SET |

**选择建议：**
- **一般查询条件**：使用 `{{ }}`
- **UPDATE 的 SET 子句**（需要显式设置为 NULL）：使用 `--@`
- **需要区分"不存在"和"值为 nil"**：使用 `--@`

#### 4. 嵌套块

支持块内嵌套块（仅 `{{ }}` 语法支持）：

```sql
SELECT * FROM users
WHERE 1 = 1
{{ AND status = :status
   {{ AND name LIKE :l:name }}
   {{ AND create_date >= :create_date_start }}
}}
```

#### 5. 复杂逻辑（Clojure 表达式）

对于复杂逻辑，可以使用 `/*~ */` 形式：

```sql
/*~
(when (:name params)
  "and name = :name")
~*/
```

### 使用场景

#### 条件 WHERE 子句

```sql
SELECT * FROM orders
WHERE user_id = :user_id
{{ AND status = :status }}
{{ AND create_time >= :start_time }}
{{ AND create_time <= :end_time }}
```

**当 `params` 为 `{:user_id 123}` 时，生成：**
```sql
SELECT * FROM orders WHERE user_id = 123
```

**当 `params` 为 `{:user_id 123 :status "active"}` 时，生成：**
```sql
SELECT * FROM orders WHERE user_id = 123 AND status = 'active'
```

#### 条件更新

```sql
UPDATE users
SET id = :id
--@ {{, name = :name}}{{, email = :email}}
--@ {{, updated_at = :updated_at}}
WHERE id = :id
```

**当 `params` 为 `{:id 1 :name "John"}` 时，生成：**
```sql
UPDATE users SET id = 1, name = 'John' WHERE id = 1
```

#### 分页

```sql
SELECT * FROM users
ORDER BY id
{{ LIMIT :_limit }}
{{ OFFSET :_offset }}
```

---

## 动态列选择

### 基本语法

使用双中括号 `[[]]` 包围 SELECT 列表：

```sql
SELECT
[[col1, col2, col3]]
FROM table_name
```

### 重要语法规则

⚠️ **`[[` 和 `]]` 必须遵守以下规则：**

1. **`[[` 的位置**：
   - ✅ `[[` 必须在行首，或前面只有空白符
   - ❌ `[[` 前面不能有其他内容（包括 SQL 关键字）

2. **`]]` 的位置**：
   - ✅ `]]` 必须独占一行
   - ❌ `]]` 后面不能有其他内容（除了换行符）

**正确示例：**
```sql
-- 正确：[[ 在行首，]] 独占一行
SELECT
[[id, code, name, count(1) AS cnt]]
FROM obd
WHERE model_id = :model_id
```

**错误示例：**
```sql
-- 错误：[[ 不在行首
SELECT [[id, code, name]]

-- 错误：]] 不独占一行
SELECT
[[id, code, name]] FROM obd

-- 错误：跨行使用
SELECT
[[id, code, name
, count(1) AS cnt]]
FROM obd
```

### 与 `{{` 的区别

| 特性 | `[[ ]]` | `{{ }}` |
|------|---------|---------|
| 位置要求 | `[[` 必须在行首（或前导空白后） | `{{` 可以在行内任意位置 |
| 闭合要求 | `]]` 必须独占一行 | `}}` 不需要独占一行 |
| 主要用途 | 动态列选择 | 动态条件块 |
| 参数支持 | ❌ 不允许 | ✅ 允许 |
| 运行时替换 | ✅ 通过 `:_cols` 参数 | ❌ 通过参数值判断 |

### 安全特性

- **防止 SQL 注入**：严格限制列名格式，只允许字母、数字、下划线和点号
- **无参数支持**：select-list 内不允许使用参数
- **语法验证**：使用 BNF 语法定义的合法 SQL 表达式

### ⚠️ 重要限制：一个 SQL 语句只能有一个 `[[ ]]`

**禁止在同一 SQL 语句中使用多个 `[[ ]]` 块！**

#### 错误示例 1：多个 `[[ ]]`（不提供 `:_cols`）

```sql
-- ❌ 错误：多个 [[ ]]
SELECT
[[id, name]]
[[email, phone]]
FROM users
```

**生成的 SQL：**
```sql
SELECT id, name email, phone FROM users
```
❌ **问题**：缺少逗号分隔，SQL 语法错误

#### 错误示例 2：多个 `[[ ]]`（提供 `:_cols`）

```sql
-- ❌ 错误：多个 [[ ]]
SELECT
[[id, name]]
[[email, phone]]
FROM users
```

```clojure
;; params
{:_cols [["id" "user_id"] ["name" nil]]}
```

**生成的 SQL：**
```sql
SELECT id AS user_id, name id AS user_id, name FROM users
```
❌ **问题**：重复的列，SQL 语法错误

#### 原因分析

当 SQL 中有多个 `[[ ]]` 时，每个 `select-list` 都会：
1. 如果提供 `:_cols`：所有块都使用相同的 `:_cols` 参数，导致重复输出
2. 如果不提供 `:_cols`：每个块输出自己的原始内容，导致缺少逗号分隔

**正确做法：**
```sql
-- ✅ 正确：只使用一个 [[ ]]
SELECT
[[id, name, email, phone]]
FROM users
```

### 支持的列表达式

1. **简单列名**
   ```sql
   [[id, name, email]]
   ```

2. **带别名**
   ```sql
   [[id, name AS user_name, email]]
   ```

3. **函数调用**
   ```sql
   [[id, COUNT(*) AS total, MAX(amount) AS max_amount]]
   ```

4. **CASE 表达式**
   ```sql
   [[id, CASE WHEN status = 1 THEN 'active' ELSE 'inactive' END AS status_text]]
   ```

5. **CAST 表达式**
   ```sql
   [[id, CAST(amount AS VARCHAR) AS amount_str]]
   ```

6. **算术表达式**
   ```sql
   [[id, price * quantity AS total]]
   ```

7. **限定列名**
   ```sql
   [[user.id, user.name, department.name AS dept_name]]
   ```

### 动态列选择（`:_cols` 参数）

通过提供 `:_cols` 参数实现运行时动态列选择：

```sql
SELECT 
[[id, code, name, count(1) AS cnt]]
FROM obd
WHERE model_id = :model_id
```

**当提供 `:_cols` 参数时：**
```clojure
;; params
{:model_id 1
 :_cols [["id" nil]
         ["name" "user_name"]
         ["COUNT(*)" "total"]]}
```

**生成 SQL：**
```sql
SELECT id, name AS user_name, COUNT(*) AS total
FROM obd
WHERE model_id = 1
```

**`:_cols` 参数格式：**
```clojure
[["列表达式" "别名"]  ;; 别名为空字符串或 nil 时省略
 ["column_name" nil]
 ["COUNT(*)" "total"]
 ["price * quantity" "amount"]]
```

### 列表达式语法规则

根据 [parablock.bnf](resources/parablock.bnf:6-24)，select-content 支持以下语法：

```bnf
select-content = select-item (',' select-item)*
select-item = select-expr (<'AS'>? column-alias)?
select-expr = qualified-name
            | function-call
            | literal
            | case-expr
            | cast-expr
            | arithmetic-expr
```

**限制：**
- 不允许子查询
- 不允许参数（防止注入）
- 函数名、列名使用严格的字符限制

---

## 高级特性

### 排序（ORDER BY）

使用 `order by :_order_by` 语法：

```sql
-- :name list-users
SELECT * FROM users
WHERE status = :status
order by :_order_by
```

**参数格式：**
```clojure
{:_order_by ["name" ["create_time" "desc"]]}
```

**生成 SQL：**
```sql
SELECT * FROM users WHERE status = 'active' ORDER BY "name", "create_time" DESC
```

### 分页（LIMIT/OFFSET）

```sql
SELECT * FROM users
WHERE status = :status
order by :_order_by
{{ LIMIT :_limit }}
{{ OFFSET :_offset }}
```

## 完整示例

### 复杂查询示例

```sql
-- :name search-obd
-- 查询 OBD 设备信息，支持多条件动态查询和动态列选择
SELECT
   [[ID, CODE, NAME, TML_ID, MODEL_ID, LIFE_STATE_ID, CREATE_DATE, count(1) AS cnt]]
FROM OBD
WHERE model_id = :model_id AND life_state_id = :life_state_id
{{ AND code like :l:code }}
{{ AND name like :l:name }}
{{ AND create_date >= :create_date_start }}
{{ AND create_date <= :create_date_end }}
order by :_order_by
{{ LIMIT :_limit OFFSET :_offset }}
```

**参数示例：**
```clojure
{:model_id 1
 :life_state_id 2
 :code "ABC"
 :name "Test"
 :create_date_start "2024-01-01"
 :create_date_end "2024-12-31"
 :_order_by ["code" ["create_date" "desc"]]
 :_limit 10
 :_offset 0}
```

### INSERT 示例

```sql
-- :name insert-user
INSERT INTO users (name, email, status, create_date)
VALUES (:name, :email, :status, :create_date)
```

### INSERT 元组示例

```sql
-- :name insert-point
-- 插入单个坐标点（使用元组）
INSERT INTO points (x, y, z)
VALUES :t:location

-- 参数示例
{:location [100.5 200.3 50.7]}
-- 生成: INSERT INTO points (x, y, z) VALUES (100.5, 200.3, 50.7)
```

```sql
-- :name insert-points-batch
-- 批量插入坐标点（使用元组数组）
INSERT INTO points (x, y)
VALUES :t*:coords

-- 参数示例
{:coords [[1.0 2.0] [3.5 4.2] [5.1 6.8]]}
-- 生成: INSERT INTO points (x, y) VALUES (1.0, 2.0), (3.5, 4.2), (5.1, 6.8)
```

```sql
-- :name insert-event
-- 插入事件（使用元组支持不同类型的值）
INSERT INTO events (user_id, event_type, created_at)
VALUES :t:event

-- 参数示例
{:event [123 "login" "2024-01-15 10:30:00"]}
-- 生成: INSERT INTO events (user_id, event_type, created_at)
--      VALUES (123, 'login', '2024-01-15 10:30:00')
```

### 元组查询示例

```sql
-- :name find-rectangles-by-size
-- 使用元组数组进行组合条件查询
SELECT * FROM rectangles
WHERE (width, height) IN (:t*:sizes)
{{ AND area > :min_area }}
order by :_order_by

-- 参数示例
{:sizes [[10 20] [30 40] [50 60]]
 :min_area 100
 :_order_by ["width" "height"]}
```

### UPDATE 示例

```sql
-- :name update-user
UPDATE users
SET id = :id
--@ {{, name = :name}}{{, email = :email}}
--@ {{, status = :status}}{{, updated_at = :updated_at}}
WHERE id = :id
```

**说明：**
- 使用 `--@` 语法，只检查参数键是否存在
- 每行使用 `{{ }}` 包裹，确保当参数为 nil 时也能更新为 NULL
- 多个字段可以用 `{{ }}` 连接写在一行，或分行写

**当 `params` 为 `{:id 1 :name "John" :email nil}` 时，生成：**
```sql
UPDATE users SET id = 1, name = 'John', email = NULL WHERE id = 1
```

**当 `params` 为 `{:id 1}` 时，生成：**
```sql
UPDATE users SET id = 1 WHERE id = 1
```

### DELETE 示例

```sql
-- :name delete-user
DELETE FROM users
WHERE id = :id
{{ AND status = :status }}
```

---

## 最佳实践

### 1. 参数命名

- 使用有意义的参数名
- 系统参数使用 `_` 前缀（`:_order_by`、`:_limit`、`:_offset`、`:_cols`）
- 保持命名一致性

### 2. 动态块使用

- 简单条件使用 `{{` 语法
- 复杂逻辑使用 `/*~ */` 语法
- 避免过深的嵌套

### 3. 列选择

- 避免使用 `SELECT *`
- 使用 `[[col1, col2, ...]]` 明确指定列
- 为计算列添加别名

### 4. SQL 注入防护

- 始终使用参数化查询
- 不要在 `[[` 列表内使用参数
- 对于动态标识符，使用 `:i:` 类型参数

### 5. 性能优化

- 合理使用索引列作为条件
- 避免 SELECT 过多列
- 使用分页限制结果集

### 6. 可读性

- 合理缩进
- 添加注释说明业务逻辑
- 复杂查询拆分为多个语句

### 7. 错误处理

- 必需参数（块外）确保提供
- 可选参数（块内）考虑默认值
- 使用元数据提取验证参数完整性


**文档版本**: 1.0
**最后更新**: 2025-01-15
**维护者**: Victory Team
