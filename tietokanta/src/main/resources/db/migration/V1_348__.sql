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

ALTER TABLE yhatiedot ADD CONSTRAINT yhatiedot_uniikki_yhatunnus UNIQUE (yhatunnus);
ALTER TABLE yhatiedot ADD CONSTRAINT yhatiedot_uniikki_yhaid UNIQUE (yhaid);

-- Kohteelle tarvittavat tiedot

ALTER TABLE yllapitokohde ADD COLUMN tr_numero INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_alkuosa INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_alkuetaisyys INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_loppuosa INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_loppuetaisyys INTEGER;

-- Uudelleennimeä päällystyskohde ja päällystyskohdeosa

ALTER TABLE paallystyskohde RENAME TO yllapitokohde;
ALTER TABLE paallystyskohdeosa RENAME TO yllapitokohdeosa;
ALTER TABLE yllapitokohdeosa RENAME COLUMN paallystyskohde TO yllapitokohde;

-- Ylläpitokohteelle tieto siitä onko se päällystystä vai paikkausta

CREATE TYPE yllapitokohdetyyppi AS ENUM ('paallystys', 'paikkaus');
ALTER TABLE yllapitokohde ADD COLUMN tyyppi yllapitokohdetyyppi;
UPDATE yllapitokohde SET tyyppi = (SELECT tyyppi FROM urakka WHERE id = urakka)::TEXT::yllapitokohdetyyppi;

-- Ylläpitokohteelle TR-osoite:

ALTER TABLE yllapitokohde ADD COLUMN tr_numero INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_alkuosa INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_alkuetaisyys INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_loppuosa INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_loppueosataisyys INTEGER;

-- Ylläpitokohteelle muut puuttuvat YHA-tiedot

ALTER TABLE yllapitokohde ADD COLUMN yhatunnus VARCHAR(2048);
ALTER TABLE yllapitokohde ADD COLUMN yhaid INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN yllapitoluokka INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN lahetysaika TIMESTAMP;

ALTER TABLE yllapitokohde ADD CONSTRAINT yllapitokohde_uniikki_yhatunnus UNIQUE (yhatunnus);
ALTER TABLE yllapitokohde ADD CONSTRAINT yllapitokohde_uniikki_yhaid UNIQUE (yhaid);

-- Ylläpitokohdeosalle muut puuttuvat YHA-tiedot

ALTER TABLE yllapitokohdeosa ADD COLUMN yhaid INTEGER;
ALTER TABLE yllapitokohdeosa ADD CONSTRAINT yllapitokohdeosa_uniikki_yhaid UNIQUE (yhaid);