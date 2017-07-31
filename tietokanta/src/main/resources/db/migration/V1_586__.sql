-- Poista duplikaatti-turvalaitteet
DELETE FROM vv_turvalaite
WHERE id IN
      (SELECT MAX(id)
       FROM vv_turvalaite
       GROUP BY turvalaitenro);