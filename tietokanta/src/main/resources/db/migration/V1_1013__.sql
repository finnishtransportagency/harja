-- Luodaan toimenpiteille uusi taulu, minne jätetään entisen toimenpidekoodi-taulun tason 4 rivit
CREATE TABLE toimenpide (
    id                     SERIAL PRIMARY KEY,
    nimi                   VARCHAR(255) CHECK (nimi NOT LIKE '%^%'),
    koodi                  VARCHAR(16) UNIQUE,
    emo                    INTEGER REFERENCES toimenpide (id),  -- ylemmän tason toimenpidekoodi (tai NULL jos tämän taso on 1.),
    taso                   SMALLINT CHECK (taso > 0 AND taso < 4),
    luotu                  TIMESTAMP,
    muokattu               TIMESTAMP,
    luoja                  INTEGER REFERENCES kayttaja(id),
    muokkaaja              INTEGER REFERENCES kayttaja(id),
    poistettu              BOOLEAN DEFAULT FALSE,
    tuotenumero            INTEGER,
    piilota                BOOLEAN
);
CREATE INDEX toimenpide_taso ON toimenpide (taso);
CREATE INDEX toimenpide_emo ON toimenpide (emo);
CREATE INDEX toimenpide_koodi ON toimenpide (koodi);

INSERT INTO toimenpide (id, nimi, koodi, emo, taso, luotu, muokattu, luoja, muokkaaja, poistettu, tuotenumero, piilota)
(SELECT id, nimi, koodi, emo, taso, luotu, muokattu, luoja, muokkaaja, poistettu, tuotenumero, piilota
  FROM toimenpidekoodi
WHERE taso < 4);
