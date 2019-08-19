CREATE TABLE users
(id int auto_increment PRIMARY KEY,
name VARCHAR(30),
email VARCHAR(30),
admin BOOLEAN,
last_login TIME,
is_active BOOLEAN,
pass VARCHAR(300));

--;;
insert into users values (1, 'Zhangfei', 'fzhang@ustcinfo.com', true, '21:45:00', true, '123456');
--;;
insert into users values (2, 'Guanyu', 'yguan@ustcinfo.com', true, '21:45:00', true, '123456');
--;;
insert into users values (3, 'Liubei', 'liu.bei@ustcinfo.com', true, '21:45:00', true, '123456');
--;;
insert into users values (4, 'Zhaoyun', 'yzhao@ustcinfo.com', false ,'13:33:00', false, '123455');
--;;
insert into users values (5, 'Huangzhong', 'zhuang@ustcinfo.com', false ,'13:33:00', false, '123455');
--;;





CREATE TABLE students
(id int auto_increment PRIMARY KEY,
name VARCHAR(30),
sex VARCHAR(20),
chinese NUMBER(24),
math NUMBER(24),
english NUMBER(24));


--;;
insert into students values (1,'Tom','man',80,80,90);
--;;
insert into students values (2,'Jerry','man',81,70,90);
--;;
insert into students values (3,'Jack','man',60,70,90);
--;;
insert into students values (4,'Bill','man',80,84,40);
--;;
insert into students values (5,'Jane','woman',80,80,90);
--;;
