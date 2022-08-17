-- :name list-users :? :* :D
-- :doc keep the constant condition in the sql
select * from users
--~ where [id = :id and] [name like :l:name and] is_active = true

-- :name list-users2 :? :* :D
-- :doc constant condition will keep or not with there friend condition
select * from users
--~ where [[id = :id and] [name like :l:name and]  is_active = true]
