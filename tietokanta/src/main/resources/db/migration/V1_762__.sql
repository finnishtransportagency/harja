CREATE TABLE vv_urakka_turvalaiteryhma
(
  id               SERIAL PRIMARY KEY,
  urakka           INTEGER REFERENCES urakka (id),
  turvalaiteryhmat TEXT[] NOT NULL,
  alkupvm          DATE   NOT NULL,
  loppupvm         DATE   NOT NULL,
  luotu            TIMESTAMP,
  luoja            INTEGER,
  muokattu         TIMESTAMP,
  muokkaaja        INTEGER
);

CREATE UNIQUE INDEX uniikki_urakka
  ON vv_urakka_turvalaiteryhma (urakka);

COMMENT ON TABLE vv_urakka_turvalaiteryhma IS 'Urakan relaatiot turvalaiteryhmiin. Yksi urakka-alue voi koostua useasta turvalaiteryhmästä. Vesiväyläurakoiden urakka.urakkanro viittaa taulun id-kenttään.';

-- Päivitä toimenpiteen urakan käsittely. Hox. Varsinainen kantaan päätyvä sproc on R_Vesivaylien_toimenpiteet.sql-tiedostossa.
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

