-- name: tallenna-reittimerkinta!
INSERT INTO tarkastusreitti
(id, pistetyyppi, tarkastusajo, aikaleima, sijainti, sijainti_tarkkuus, kitkamittaus, havainnot, lampotila,
 talvihoito_tasaisuus, soratie_tasaisuus, lumisuus, kuvaus, kuva,
 polyavyys, sivukaltevuus, kiinteys, laadunalitus, liittyy_merkintaan,
tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys)
VALUES
  (:id, 0, :tarkastusajo, to_timestamp(:aikaleima / 1000), ST_MakePoint(:x, :y), :sijainti_tarkkuus,
        :kitkamittaus, ARRAY [:havainnot] :: INTEGER [], :lampotila, :talvihoito_tasaisuus,
        :soratie_tasaisuus, :lumisuus, :kuvaus, :kuva, :polyavyys,
   :sivukaltevuus, :kiinteys, :laadunalitus, :liittyy_merkintaan,
  :tr_numero, :tr_alkuosa, :tr_alkuetaisyys, :tr_loppuosa, :tr_loppuetaisyys)
ON CONFLICT DO NOTHING;

-- name: luo-uusi-tarkastusajo<!
-- Tekee uuden tarkastusajon ja palauttaa sen id:n
INSERT INTO tarkastusajo (ulkoinen_id, luoja, luotu)
VALUES (:ulkoinen_id, :kayttaja, now());

-- name: paata-tarkastusajo!
-- Päättää aiemmin aloitetun tarkastusajon
UPDATE tarkastusajo
SET paatetty = now()
WHERE id = :id AND paatetty IS NULL AND luoja = :kayttaja;

-- name: hae-kayttajatiedot
-- Hakee kayttajatiedot
SELECT *
FROM kayttaja
WHERE kayttajanimi = :kayttajanimi AND poistettu = FALSE;

-- name: hae-tr-osoite
-- Hakee tierekisteriosoitteen yhdelle pisteelle
SELECT
  tie,
  aosa,
  aet
FROM tierekisteriosoite_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY, CAST(:treshold AS INTEGER)) AS tr_osoite;

-- name: pisteet-tr-osoitteeksi
-- Muuntaa pisteet TR-osoitteeksi
SELECT *
FROM tierekisteriosoite_pisteille(ST_MakePoint(:x1, :y1) :: GEOMETRY,
                                  ST_MakePoint(:x2, :y2) :: GEOMETRY, CAST(:treshold AS INTEGER)) AS tr_osoite;

-- name: tallenna-kuva<!
INSERT INTO liite (lahde, tyyppi, koko, liite_oid, pikkukuva, luoja, luotu)
VALUES (:lahde :: lahde, :tyyppi, :koko, :oid, :pikkukuva, :luoja, now());

-- name: hae-reitin-merkinnat-tieosoitteilla-raw
SELECT
  x.id,
  x.sijainti,
  x.kitkamittaus,
  x.lampotila,
  x.talvihoito_tasaisuus as "talvihoito-tasaisuus",
  x.soratie_tasaisuus as "soratie-tasaisuus",
  x.lumisuus,
  x.kuvaus,
  x.kuva,
  x.havainnot,
  x.tarkastusajo,
  x.aikaleima,
  x.polyavyys,
  x.sivukaltevuus,
  x.kiinteys,
  x."gps-tarkkuus",
  x."laheiset-tr-osoitteet",
  x.laadunalitus,
  x."liittyy-merkintaan",
  x."kayttajan-syottama-tie",
  x."kayttajan-syottama-aosa",
  x."kayttajan-syottama-aet",
  x."kayttajan-syottama-losa",
  x."kayttajan-syottama-let"
FROM (SELECT
        t.id,
        t.sijainti,
        t.kitkamittaus,
        t.lampotila,
        t.talvihoito_tasaisuus,
        t.soratie_tasaisuus,
        t.lumisuus,
        t.kuvaus,
        t.kuva,
        t.havainnot,
        t.tarkastusajo,
        t.aikaleima,
        t.polyavyys,
        t.sivukaltevuus,
        t.kiinteys,
        t.sijainti_tarkkuus AS "gps-tarkkuus",
        t.liittyy_merkintaan as "liittyy-merkintaan",
        t.tr_numero as "kayttajan-syottama-tie",
        t.tr_alkuosa as "kayttajan-syottama-aosa",
        t.tr_alkuetaisyys as "kayttajan-syottama-aet",
        t.tr_loppuosa as "kayttajan-syottama-losa",
        t.tr_loppuetaisyys as "kayttajan-syottama-let",
        laheiset_osoitteet_pisteelle(t.sijainti, COALESCE(:laheiset_tiet_threshold::INTEGER, 100))
          AS "laheiset-tr-osoitteet",
        t.laadunalitus
      FROM tarkastusreitti t
        INNER JOIN tarkastusajo a ON a.id = t.tarkastusajo
      WHERE t.tarkastusajo = :tarkastusajo
      ORDER BY t.id ASC) x;

-- name: hae-jatkuvat-vakiohavainto-idt
SELECT id
FROM vakiohavainto
WHERE jatkuva = TRUE;

-- name: hae-pistemaiset-vakiohavainnot
SELECT
  avain,
  nimi
FROM vakiohavainto
WHERE jatkuva = FALSE;

-- name: hae-tarkastusajon-reitti
SELECT sijainti, sijainti_tarkkuus FROM tarkastusreitti
  WHERE tarkastusajo = :id
ORDER by id;

-- name: luo-uusi-tarkastus<!
INSERT INTO tarkastus (urakka, aika, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
                       sijainti, tarkastaja, tyyppi, tarkastusajo, luoja, havainnot, lahde, laadunalitus,
                       nayta_urakoitsijalle)
VALUES
  (:urakka, :aika, :tr_numero, :tr_alkuosa, :tr_alkuetaisyys, :tr_loppuosa, :tr_loppuetaisyys,
            :sijainti, :tarkastaja, :tyyppi :: tarkastustyyppi, :tarkastusajo, :luoja, :havainnot, :lahde :: lahde, :laadunalitus,
   :nayta_urakoitsijalle);

-- name: luo-uusi-tarkastuksen-vakiohavainto<!
INSERT INTO tarkastus_vakiohavainto (tarkastus, vakiohavainto) VALUES (:tarkastus, :vakiohavainto);

-- name: luo-uusi-talvihoitomittaus<!
INSERT INTO talvihoitomittaus (tarkastus, talvihoitoluokka, lumimaara, tasaisuus, kitka, ajosuunta, lampotila_tie, lampotila_ilma)
VALUES (:tarkastus, :talvihoitoluokka, :lumimaara, :tasaisuus, :kitka, :ajosuunta, :lampotila_tie, :lampotila_ilma);

-- name: luo-uusi-soratiemittaus<!
INSERT INTO soratiemittaus (tarkastus, hoitoluokka, tasaisuus, kiinteys, polyavyys, sivukaltevuus)
VALUES (:tarkastus, :hoitoluokka, :tasaisuus, :kiinteys, :polyavyys, :sivukaltevuus);

-- name: luo-uusi-tarkastus-liite<!
INSERT INTO tarkastus_liite (tarkastus, liite) VALUES (:tarkastus, :liite);

-- name: paattele-urakka
-- Palauttaa sen (hoito)urakan jonka alueella reitin pisteitä osuu eniten
WITH ur AS (SELECT
              u.id,
              u.tyyppi,
              u.alkupvm,
              u.loppupvm,
              a.alue
            FROM urakka u
              INNER JOIN alueurakka a ON u.urakkanro = a.alueurakkanro
            WHERE NOW() BETWEEN u.alkupvm AND u.loppupvm
                  AND u.nimi NOT ILIKE '%testi%')
SELECT ur.id
FROM tarkastusreitti r
  INNER JOIN ur ON ST_Contains(ur.alue, r.sijainti)
WHERE r.tarkastusajo = :tarkastusajo
      AND ur.tyyppi = 'hoito' :: urakkatyyppi
GROUP BY ur.id
ORDER BY count(ur.id) DESC
LIMIT 1;

-- name: hae-pisteen-hoitoluokka
SELECT *
FROM hoitoluokka_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY, :tietolaji :: hoitoluokan_tietolajitunniste,
                           CAST(:treshold AS INTEGER));

-- name: hae-vakiohavaintojen-avaimet
SELECT
  avain,
  id
FROM vakiohavainto;

-- name: tr-osoitteelle-viiva
SELECT tierekisteriosoitteelle_viiva AS geom
FROM tierekisteriosoitteelle_viiva(CAST(:tr_numero AS INTEGER),
                                   CAST(:tr_alkuosa AS INTEGER),
                                   CAST(:tr_alkuetaisyys AS INTEGER),
                                   CAST(:tr_loppuosa AS INTEGER),
                                   CAST(:tr_loppuetaisyys AS INTEGER));

-- name: hae-urakkatyypin-urakat
-- Hakee urakkatyypin urakat käsivalintaa varten. Mahdollistetaan usealla tyypillä haku,
-- esim. paallystys ja tiemerkinta yhtaikaa
SELECT
  id,
  sampoid,
  nimi,
  alkupvm,
  loppupvm,
  tyyppi
FROM urakka
WHERE tyyppi IN (:tyyppi :: urakkatyyppi);

-- name: ajo-paatetty
SELECT paatetty FROM tarkastusajo WHERE id = :id;
