-- Linkkaa toimenpiteet sopimukseen sopimuksen diaarinumeron tai reimarin sopimus-id:n perusteella

CREATE OR REPLACE FUNCTION toimenpiteen_linkit_trigger_proc()
  RETURNS TRIGGER AS
$$
DECLARE id_temp INTEGER;
BEGIN
  IF NEW."harjassa-luotu" IS TRUE
  THEN
    RETURN NEW;
  ELSE
    id_temp := (SELECT id
                FROM sopimus hs, reimari_sopimuslinkki sl
                WHERE
                  sl."harja-sopimus-id" = hs.id AND
                  sl."reimari-sopimus-id" = (NEW."reimari-sopimus").nro
                LIMIT 1);
    IF id_temp IS NULL
    THEN
      id_temp := (SELECT hs.id
                  FROM sopimus hs, reimari_sopimuslinkki sl
                  WHERE
                    sl."harja-sopimus-id" = hs.id AND
                    btrim(sl."reimari-diaarinro") = btrim((NEW."reimari-sopimus").diaarinro)
                  LIMIT 1);
    END IF;
    -- RAISE NOTICE 'sopimus-id:ksi toimenpiteelle %', id_temp;

    NEW."sopimus-id" = id_temp;
    -- id:ksi tulee NULL jos ei löydy, joka on ok
    -- RAISE NOTICE 'reimari_toimenpide linkit trigger: sopimus-id arvoksi %', NEW."sopimus-id";

  NEW."turvalaitenro" = (NEW."reimari-turvalaite").nro;
  -- RAISE NOTICE 'reimari_toimenpide linkit trigger: turvalaitenro arvoksi %', NEW."turvalaitenro";

    RETURN NEW;
  END IF;
END;
$$
LANGUAGE plpgsql;

-- Linkkaa urakka toimenpiteeseen sopimuksen tai turvalaiteryhmän perusteella
CREATE OR REPLACE FUNCTION toimenpiteen_urakka_id_trigger_proc()
  RETURNS TRIGGER AS
$$
DECLARE
  id_urakka_alue  INT;
  DECLARE id_temp TEXT;
BEGIN
  IF NEW."harjassa-luotu" IS TRUE
  THEN
    RETURN NEW;
  ELSE
    id_urakka_alue := (SELECT id
                       from vv_urakka_turvalaiteryhma
                       WHERE turvalaiteryhmat @> ARRAY [(NEW."reimari-turvalaite").ryhma::text]
                         AND NEW."reimari-luotu"::DATE BETWEEN alkupvm::DATE and loppupvm::DATE
                       LIMIT 1);
    IF (id_urakka_alue IS NULL) THEN
      RAISE NOTICE 'Toimenpiteen % urakka-aluetta ei voitu päätellä, joten urakkaa ei voi selvittää.', NEW."reimari-id"::text;
    ELSE
      RAISE NOTICE 'Toimenpiteen % urakka-alue on %.', NEW."reimari-id"::text, CAST(id_urakka_alue AS text);
      id_temp := (SELECT u.id
                  FROM urakka u,
                       sopimus s,
                       reimari_sopimuslinkki rsl
                  WHERE u.urakkanro = CAST(id_urakka_alue AS text)
                    AND rsl."harja-sopimus-id" = s.id
                    AND rsl."reimari-diaarinro" = btrim((NEW."reimari-sopimus").diaarinro)
                    AND s.urakka = u.id
                  LIMIT 1);
      IF id_temp IS NULL THEN
        RAISE NOTICE 'trigger: linkataan urakka-id turvalaiteryhmällä koska sopimus/diaarinro linkkaus ei onnistunut';
        id_temp := (SELECT id
                    FROM urakka u
                    WHERE u.urakkanro IS NOT NULL
                      AND u.urakkanro = CAST(id_urakka_alue AS text)
                    LIMIT 1)
                   LIMIT 1;
      END IF;
    END IF;
  END IF;
  NEW."urakka-id" = id_temp;
  -- urakka-id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'trigger: urakka-id arvoksi %', NEW."urakka-id";
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- Aseta vaylanro Reimarin väylätietojen perusteella

CREATE OR REPLACE FUNCTION vv_aseta_toimenpiteen_vayla() RETURNS trigger AS $$
BEGIN
  IF NEW."vaylanro" IS NULL THEN
     BEGIN
         NEW."vaylanro" = (NEW."reimari-vayla").nro::integer;
     EXCEPTION WHEN OTHERS THEN
         RAISE NOTICE 'valyanro arvoa % ei voitu muuntaa kokonaisluvuksi', (NEW."reimari-vayla").nro;
         NULL;
     END;
  END IF;
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

CREATE OR REPLACE FUNCTION muodosta_vesivaylaurakan_geometria()
  RETURNS TRIGGER AS $$
BEGIN
  IF NEW.turvalaitteet IS NOT NULL
  THEN
    NEW.urakka_alue := (SELECT ST_ConvexHull(ST_UNION(geometria))
                        FROM vatu_turvalaite
                        WHERE turvalaitenro = ANY ((NEW.turvalaitteet) :: TEXT []));
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
