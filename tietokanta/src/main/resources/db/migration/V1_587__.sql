-- Poista duplikaatti-turvalaittee
DELETE FROM vv_turvalaite
WHERE id IN (SELECT id
              FROM (SELECT id, ROW_NUMBER() OVER (partition BY turvalaitenro ORDER BY id) AS rnum
                      FROM vv_turvalaite) t
              WHERE t.rnum > 1);

CREATE UNIQUE INDEX vv_turvalaite_turvalaitenro ON vv_turvalaite (turvalaitenro);

ALTER TABLE vv_turvalaite DROP COLUMN tunniste;



CREATE OR REPLACE FUNCTION toimenpiteen_linkit_trigger_proc()
  RETURNS TRIGGER AS
$$
DECLARE id_temp INTEGER;
BEGIN
  id_temp := (SELECT id FROM sopimus hs, reimari_sopimuslinkki sl
    WHERE
      sl."harja-sopimus-id" = hs.id AND
      sl."reimari-sopimus-id" = (NEW."reimari-sopimus").nro LIMIT 1);

  NEW."sopimus-id" = id_temp;
                -- id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'reimari_toimenpide linkit trigger: sopimus-id arvoksi %', NEW."sopimus-id";

  id_temp := (SELECT id FROM vv_turvalaite
               WHERE turvalaitenro::text = (NEW."reimari-turvalaite").nro);

  NEW."turvalaite-id" = id_temp;
                -- id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'reimari_toimenpide linkit trigger: turvalaite-id arvoksi %', NEW."turvalaite-id";

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
