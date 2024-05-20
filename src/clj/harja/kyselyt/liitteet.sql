-- name: tallenna-liite<!
-- Tallentaa uuden liitteen. Liitteen pitää olla tallennettuna jo ja fileyard UUID annettava.
INSERT
  INTO liite
       (nimi, tyyppi, koko, liite_oid, "fileyard-hash", s3hash, pikkukuva, luoja, luotu, urakka, kuvaus, lahde, "virustarkastettu?")
VALUES (:nimi, :tyyppi, :koko, :liite_oid, :fileyard-hash, :s3hash, :pikkukuva, :luoja, current_timestamp, :urakka,
        :kuvaus, :lahdejarjestelma::lahde, :virustarkastettu?);

-- name: hae-liite-lataukseen
-- Hakee liitteen tiedot sen latausta varten.
SELECT "fileyard-hash", liite_oid, s3hash, tyyppi, koko, urakka, "virustarkastettu?" FROM liite WHERE id = :id;

-- name: hae-siltatarkastusliite-lataukseen
-- Siltatarkastuksen liitteet pitää pystyä lataamaan, jos käyttäjällä on oikeus yhteenkään sillan urakoihin.
SELECT l."fileyard-hash", l.liite_oid, l.s3hash, l.tyyppi, l.koko, l.urakka, l."virustarkastettu?", s.urakat
FROM liite l
         LEFT JOIN siltatarkastus_kohde_liite stkl ON l.id = stkl.liite
         LEFT JOIN siltatarkastus st ON stkl.siltatarkastus = st.id
         LEFT JOIN silta s ON st.silta = s.id
WHERE l.id = :id;

-- name: hae-pikkukuva-lataukseen
-- Hakee liitteen pikkukuvan sen latausta varten.
SELECT pikkukuva, urakka, "virustarkastettu?" FROM liite WHERE id = :id;


-- name: hae-urakan-liite-id
-- Hakee liitteen id:llä ja urakalla liitteen. Tätä on tarkoitus käyttää
-- varmistamaan, että käyttäjän antama liitteen id kuuluu oikeaan urakkaan
-- eikä liite id:tä manipuloimalla pääse käsiksi väärän urakan tietoihin.
SELECT id FROM liite WHERE urakka = :urakka AND id = :id;

-- name: hae-siirrettavat-liitteet
-- Hakee liitteitä siirrettäväksi fileyard tallennukseen
SELECT id, nimi, liite_oid FROM liite
 WHERE "fileyard-hash" IS NULL
   AND liite_oid IS NOT NULL
   AND koko > 0
 ORDER BY random()
 LIMIT 10;

-- name: merkitse-liite-siirretyksi!
-- Merkitsee annetulle liitteelle fileyard hash arvon ja poistaa OID:n
UPDATE liite SET liite_oid = NULL, "fileyard-hash" = :fileyard-hash
 WHERE id = :id;

-- name: poista-laatupoikkeaman-kommentin-liite!
UPDATE kommentti
SET liite = NULL
WHERE id IN (
  SELECT id
  FROM kommentti
  WHERE liite = :liite
        AND id IN (SELECT kommentti
                   FROM laatupoikkeama_kommentti
                   WHERE laatupoikkeama = :laatupoikkeama)
  LIMIT 1);

-- name: poista-turvallisuuspoikkeaman-kommentin-liite!
UPDATE kommentti
SET liite = NULL
WHERE id IN (
  SELECT id
  FROM kommentti
  WHERE liite = :liite
        AND id IN (SELECT kommentti
                   FROM turvallisuuspoikkeama_kommentti
                   WHERE turvallisuuspoikkeama = :turvallisuuspoikkeama)
  LIMIT 1);

-- name: hae-liite-meta-tiedoilla
SELECT nimi, koko FROM liite l
 WHERE l.nimi = :nimi
   AND l.urakka = :urakka-id;

-- name: merkitse-liite-virustarkistetuksi!
-- Merkitsee annetulle s3hash arvolle liitteen virustarkistetuksi.
-- Tätä tarvitaan s3 ämpäriin liitettyjen liitteiden käsittelyyn.
UPDATE liite SET "virustarkastettu?" = TRUE WHERE s3hash = :s3hash;

-- name: liite-virustarkastettu?
-- luotu columni pitää sisällään suomen ajassa tallennetun timestampin. NOW() funktio palauttaa UTC ajan.
-- Lisäämm
SELECT l."virustarkastettu?" as "virustarkastettu?",
       extract('epoch' FROM :nyt::TIMESTAMP) - extract('epoch' FROM l.luotu) as "sekuntia-luonnista",
       l.s3hash
  FROM liite l
 WHERE l.id = :id;
