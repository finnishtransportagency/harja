-- Lisää muokkaustiedot (luoja, luotu, muokkaaja, muokattu), sekä poistettu sarake
ALTER TABLE kayttajan_lisaoikeudet_urakkaan
    ADD COLUMN luotu     TIMESTAMP,
    ADD COLUMN luoja     INTEGER REFERENCES kayttaja (id),
    ADD COLUMN muokattu  TIMESTAMP                        DEFAULT NULL,
    ADD COLUMN muokkaaja INTEGER REFERENCES kayttaja (id) DEFAULT NULL,
    ADD COLUMN poistettu BOOLEAN                          DEFAULT FALSE;


-- Aseta luoja sarakkeseen arvoksi käyttäjä "Integraatio" kaikille olemassa oleville riveille
UPDATE toteutuneet_kustannukset
   SET luoja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
 WHERE luoja IS NULL;

-- Aseta NOT NULL rajoite "luoja" sarakkeelle
ALTER TABLE toteutuneet_kustannukset
    ALTER COLUMN luoja SET NOT NULL;
