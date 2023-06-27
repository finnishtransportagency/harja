-- Muuta työmaapäiväkirjan säätietotaulun nimeä vastaan paremmin sitä, mitä sillä haetaan
ALTER TABLE tyomaapaivakirja_saa RENAME TO tyomaapaivakirja_saaasema;

-- Poistetaan viranomaisen avustus tapahtumatyypistä ja luodaan sille oma taulu
-- 1. Luodaan ensin taulu
CREATE TABLE tyomaapaivakirja_toimeksianto (
id                  serial primary key,
urakka_id           integer not null references urakka (id),
tyomaapaivakirja_id integer not null references tyomaapaivakirja (id) ON DELETE CASCADE,
versio              integer not null,
kuvaus              text not null,
aika                numeric not null
);

-- 2. Siirretään tauluun hieman puutteellisena, mutta kuitenkin, kaikki tiedossa oleva viranomaisen avustus
INSERT INTO tyomaapaivakirja_toimeksianto (urakka_id, tyomaapaivakirja_id, kuvaus, aika)
SELECT urakka_id, tyomaapaivakirja_id, kuvaus, 1 FROM tyomaapaivakirja_tapahtuma WHERE tyyppi = 'viranomaisen_avustus';

-- 3. Poistetaan nyt väärässä taulussa olevat viranomaisen avustukset
DELETE FROM tyomaapaivakirja_tapahtuma WHERE tyyppi = 'viranomaisen_avustus';

-- 4. Muutetaan tyypit, että viranomaisten avustukset ei voi enää kulkeutua väärään tauluun
ALTER TYPE tyomaapaivakirja_tapahtumatyyppi RENAME TO _tt;

CREATE TYPE tyomaapaivakirja_tapahtumatyyppi AS ENUM ('onnettomuus', 'liikenteenohjausmuutos', 'palaute', 'tilaajan-yhteydenotto','muut_kirjaukset');

ALTER TABLE tyomaapaivakirja_tapahtuma RENAME COLUMN tyyppi TO _tyyppi;

ALTER TABLE tyomaapaivakirja_tapahtuma ADD tyyppi tyomaapaivakirja_tapahtumatyyppi;

UPDATE tyomaapaivakirja_tapahtuma SET tyyppi = _tyyppi :: TEXT :: tyomaapaivakirja_tapahtumatyyppi;

ALTER TABLE tyomaapaivakirja_tapahtuma DROP COLUMN _tyyppi;
DROP TYPE _tt;

-- Lisätään vielä versio työmaapaiväkirja tauluun
ALTER TABLE tyomaapaivakirja ADD COLUMN versio integer not null default 1;

-- Päivitä työmaapäiväkirja taulun versiot kalusto taulun mukaan
UPDATE tyomaapaivakirja
   SET versio = (SELECT kalusto.versio FROM tyomaapaivakirja_kalusto kalusto
                  WHERE kalusto.tyomaapaivakirja_id = id
                  ORDER BY kalusto.versio DESC
                  LIMIT 1);
