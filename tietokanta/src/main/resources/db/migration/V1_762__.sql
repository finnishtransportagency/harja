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

-- Päivitä toimenpiteen urakan käsittely
CREATE OR REPLACE FUNCTION toimenpiteen_urakka_id_trigger_proc()
  RETURNS TRIGGER AS
$$
DECLARE
  id_urakka_alue  INT;
  DECLARE id_temp TEXT;
BEGIN
   id_urakka_alue := (SELECT id
                      from vv_urakka_turvalaiteryhma
                      WHERE turvalaiteryhmat @> ARRAY [(NEW."reimari-turvalaite").ryhma::text]
                      AND NEW."reimari-luotu"::DATE BETWEEN alkupvm::DATE and loppupvm::DATE LIMIT 1);
  IF (id_urakka_alue IS NULL) THEN
    RAISE NOTICE 'Toimenpiteen % urakka-aluetta ei voitu päätellä, joten urakkaa ei voi selvittää.', NEW."reimari-id"::text;
  ELSE
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
  NEW."urakka-id" = id_temp;
  -- urakka-id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'trigger: urakka-id arvoksi %', NEW."urakka-id";
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Päivitä
INSERT INTO vv_urakka_turvalaiteryhma (urakka, turvalaiteryhmat, alkupvm, loppupvm, luotu, luoja)
  (SELECT id,
          string_to_array(urakkanro, ','),
          alkupvm,
          loppupvm,
          NOW(),
          (select id from kayttaja where kayttajanimi = 'Integraatio')
   from urakka
   where tyyppi = 'vesivayla-hoito'
     and urakkanro is not null);
UPDATE urakka
set urakkanro = (select v.id from vv_urakka_turvalaiteryhma v where v.urakka = urakka.id)::TEXT,
    muokattu  = NOW(),
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE tyyppi = 'vesivayla-hoito'
  and urakkanro is not null;
