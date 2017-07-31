-- Poista duplikaatti-turvalaittee
DELETE FROM vv_turvalaite
WHERE id IN (SELECT id
              FROM (SELECT id, ROW_NUMBER() OVER (partition BY turvalaitenro ORDER BY id) AS rnum
                      FROM vv_turvalaite) t
              WHERE t.rnum > 1);

CREATE UNIQUE INDEX vv_turvalaite_turvalaitenro ON vv_turvalaite (turvalaitenro);

ALTER TABLE vv_turvalaite DROP COLUMN tunniste;
