-- YHA:n vaatimat tietomallimuutokset

-- Uusi taulu urakan yhatiedoille, linkittyy urakkaan

CREATE TABLE yhatiedot (
  id                       SERIAL PRIMARY KEY,
  urakka                   INTEGER REFERENCES urakka (id),
  yhatunnus                VARCHAR(255),
  yhaid                    INTEGER,
  yhanimi                  VARCHAR(2048),
  elyt                     VARCHAR(2048) [],
  vuodet                   INTEGER [],
  kohdeluettelo_paivitetty TIMESTAMP,
  luotu                    TIMESTAMP,
  muokattu                 TIMESTAMP
);

ALTER TABLE yhatiedot ADD CONSTRAINT yhatiedot_uniikki_yhaid UNIQUE (yhaid);

-- Uudelleennimeä päällystyskohde ja päällystyskohdeosa

ALTER TABLE paallystyskohde RENAME TO yllapitokohde;
ALTER TABLE paallystyskohdeosa RENAME TO yllapitokohdeosa;
ALTER TABLE yllapitokohdeosa RENAME COLUMN paallystyskohde TO yllapitokohde;

-- Ylläpitokohteelta pois muu_tyo boolean. Tätä käytettiin tutkimaan onko kohde luotu Harjassa vai YHAssa. Jatkossa tiedetään yhaid:n perusteella.
ALTER TABLE yllapitokohde DROP COLUMN muu_tyo;

-- Kohteelle tarvittavat tiedot

ALTER TABLE yllapitokohde ADD COLUMN tr_numero INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_alkuosa INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_alkuetaisyys INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_loppuosa INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_loppuetaisyys INTEGER;

-- Ylläpitokohteelle tieto siitä onko se päällystystä vai paikkausta

CREATE TYPE yllapitokohdetyyppi AS ENUM ('paallystys', 'paikkaus');
ALTER TABLE yllapitokohde ADD COLUMN tyyppi yllapitokohdetyyppi;
UPDATE yllapitokohde SET tyyppi = (SELECT tyyppi FROM urakka WHERE id = urakka)::TEXT::yllapitokohdetyyppi;

-- Ylläpitokohteelle muut puuttuvat YHA-tiedot

ALTER TABLE yllapitokohde ADD COLUMN yhatunnus VARCHAR(2048);
ALTER TABLE yllapitokohde ADD COLUMN yhaid INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN yllapitoluokka INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN lahetysaika TIMESTAMP;

ALTER TABLE yllapitokohde ADD CONSTRAINT yllapitokohde_uniikki_yhaid UNIQUE (yhaid);

-- Ylläpitokohdeosalle muut puuttuvat YHA-tiedot

ALTER TABLE yllapitokohdeosa ADD COLUMN yhaid INTEGER;
ALTER TABLE yllapitokohdeosa ADD CONSTRAINT yllapitokohdeosa_uniikki_yhaid UNIQUE (yhaid);