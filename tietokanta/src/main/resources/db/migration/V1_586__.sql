-- Poista duplikaatti-turvalaitteet
DELETE FROM vv_turvalaite
WHERE id NOT IN
      (SELECT MAX(id)
       FROM vv_turvalaite
       GROUP BY turvalaitenro);