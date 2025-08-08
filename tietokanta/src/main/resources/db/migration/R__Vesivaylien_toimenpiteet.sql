-- Aseta vaylanro Reimarin väylätietojen perusteella

CREATE OR REPLACE FUNCTION vv_aseta_toimenpiteen_vayla() RETURNS trigger AS $$
BEGIN
  IF NEW."vaylanro" IS NULL THEN
     BEGIN
         NEW."vaylanro" = (NEW."reimari-vayla").nro::integer;
     EXCEPTION WHEN OTHERS THEN
         --RAISE NOTICE 'valyanro arvoa % ei voitu muuntaa kokonaisluvuksi', (NEW."reimari-vayla").nro;
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
     --RAISE NOTICE 'reimari_toimenpide hintatyyppi trigger: hintatyypiksi %', NEW."hintatyyppi";
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
