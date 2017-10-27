CREATE OR REPLACE FUNCTION toimenpiteen_urakka_id_trigger_proc()
  RETURNS TRIGGER AS
$$
DECLARE id_temp TEXT;
BEGIN
  id_temp := (SELECT u.id FROM urakka u, sopimus s, reimari_sopimuslinkki rsl
                WHERE u.urakkanro = (NEW."reimari-turvalaite").ryhma::text AND
                      rsl."harja-sopimus-id" = s.id AND
                      rsl."reimari-diaarinro" = btrim((NEW."reimari-sopimus").diaarinro) AND
                      s.urakka = u.id
                      LIMIT 1);

  IF id_temp IS NULL THEN
    RAISE NOTICE 'trigger: linkataan urakka-id turvalaiteryhmällä koska sopimus/diaarinro linkkaus ei onnistunut';
    id_temp := (SELECT id FROM urakka u
      WHERE
      u.urakkanro IS NOT NULL AND u.urakkanro = (NEW."reimari-turvalaite").ryhma::text LIMIT 1) LIMIT 1;
  END IF;
  NEW."urakka-id" = id_temp;
                -- urakka-id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'trigger: urakka-id arvoksi %', NEW."urakka-id";
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
