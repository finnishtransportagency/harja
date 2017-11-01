CREATE OR REPLACE FUNCTION toimenpiteen_linkit_trigger_proc()
  RETURNS TRIGGER AS
$$
DECLARE id_temp INTEGER;
BEGIN
  id_temp := (SELECT id FROM sopimus hs, reimari_sopimuslinkki sl
    WHERE
      sl."harja-sopimus-id" = hs.id AND
      sl."reimari-sopimus-id" = (NEW."reimari-sopimus").nro LIMIT 1);
  IF id_temp IS NULL THEN
    id_temp := (SELECT hs.id FROM sopimus hs, reimari_sopimuslinkki sl
                  WHERE
                  sl."harja-sopimus-id" = hs.id AND
                  btrim(sl."reimari-diaarinro") = btrim((NEW."reimari-sopimus").diaarinro) LIMIT 1);
  END IF;
  -- RAISE NOTICE 'sopimus-id:ksi toimenpiteelle %', id_temp;

  NEW."sopimus-id" = id_temp;
                -- id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'reimari_toimenpide linkit trigger: sopimus-id arvoksi %', NEW."sopimus-id";

  id_temp := (SELECT id FROM vv_turvalaite
               WHERE turvalaitenro::text = (NEW."reimari-turvalaite").nro);

  NEW."turvalaite-id" = id_temp;
                -- id:ksi tulee NULL jos ei löydy, joka on ok
  -- RAISE NOTICE 'reimari_toimenpide linkit trigger: turvalaite-id arvoksi %', NEW."turvalaite-id";

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

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
    -- RAISE NOTICE 'trigger: linkataan urakka-id turvalaiteryhmällä koska sopimus/diaarinro linkkaus ei onnistunut';
    id_temp := (SELECT id FROM urakka u
      WHERE
      u.urakkanro IS NOT NULL AND u.urakkanro = (NEW."reimari-turvalaite").ryhma::text LIMIT 1) LIMIT 1;
  END IF;
  NEW."urakka-id" = id_temp;
                -- urakka-id:ksi tulee NULL jos ei löydy, joka on ok
  -- RAISE NOTICE 'trigger: urakka-id arvoksi %', NEW."urakka-id";
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Aseta vayla-id reimarin väylänimen perusteella

-- Koska Reimarin antama väyläid on tällä hetkellä ERI kuin inspire
-- palvelusta tuoduille väylille, joudutaan match tekemään nimellä.
-- Muodostetaan reimari toimenpiteen väylä id triggerillä.

CREATE OR REPLACE FUNCTION vv_aseta_toimenpiteen_vayla() RETURNS trigger AS $$
DECLARE
  v reimari_vayla;
  id_ INTEGER;
BEGIN
  v := NEW."reimari-vayla";
  SELECT INTO id_ id FROM vv_vayla WHERE nimi = v.nimi LIMIT 1;
  NEW."vayla-id" := id_;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Aseta hintatyyppi lisätyö-kentän perusteella

CREATE OR REPLACE FUNCTION toimenpiteen_hintatyyppi_trigger_proc()
  RETURNS TRIGGER AS
$$
BEGIN
  IF NEW."hintatyyppi" IS NULL THEN
        NEW."hintatyyppi" = CASE WHEN NEW."reimari-lisatyo" IS FALSE THEN 'kokonaishintainen'
                                 WHEN NEW."reimari-lisatyo" IS TRUE THEN 'yksikkohintainen'
                                 ELSE 'kokonaishintainen'
                            END;
     RAISE NOTICE 'reimari_toimenpide hintatyyppi trigger: hintatyypiksi %', NEW."hintatyyppi";
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
