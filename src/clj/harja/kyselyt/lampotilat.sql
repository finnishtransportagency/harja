-- name: uusi-lampotila<!
INSERT INTO lampotilat (urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila) VALUES (:urakka, :alku, :loppu, :keskilampo, :pitkalampo);

-- name: paivita-lampotila<!
UPDATE lampotilat SET
  urakka = :urakka, alkupvm = :alku, loppupvm = :loppu, keskilampotila = :keskilampo, pitka_keskilampotila = :pitkalampo
WHERE id = :id;

-- name: hae-urakan-suokasakot-ja-lampotilat
-- Hakee urakan suolasakot urakan id:llä
SELECT
  ss.id, ss.maara, ss.hoitokauden_alkuvuosi, ss.maksukuukausi, ss.indeksi, ss.urakka,
  ss.kaytossa, ss.talvisuolaraja,
  lt.id AS lt_id,
  lt.alkupvm AS lt_alkupvm,
  lt.loppupvm AS lt_loppupvm,
  lt.keskilampotila as keskilampotila,
  lt.pitka_keskilampotila as pitkakeskilampotila
FROM suolasakko ss
  LEFT JOIN lampotilat lt ON ss.urakka = lt.urakka
                             AND (ss.hoitokauden_alkuvuosi = (SELECT EXTRACT(YEAR FROM lt.alkupvm))
                                  OR lt.id IS null)
  WHERE ss.urakka = :urakka;

-- name: hae-urakan-pohjavesialue-talvisuolarajat
SELECT *
  FROM pohjavesialue_talvisuola pt
 WHERE pt.urakka = :urakka;


-- name: luo-suolasakko<!
INSERT INTO suolasakko (maara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, luotu, luoja, talvisuolaraja)
    VALUES (:maara, :hoitokauden_alkuvuosi, :maksukuukausi, :indeksi, :urakka, NOW(), :kayttaja, :talvisuolaraja);

-- name: paivita-suolasakko!
UPDATE suolasakko
   SET maara = :maara, maksukuukausi = :maksukuukausi,
       indeksi = :indeksi, muokattu = NOW(), muokkaaja = :kayttaja,
       talvisuolaraja = :talvisuolaraja
 WHERE id = :id;

-- name: hae-suolasakko-id
SELECt id FROM suolasakko WHERE urakka = :urakka AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi;

-- name: aseta-suolasakon-kaytto!
UPDATE suolasakko
   SET kaytossa = :kaytossa
 WHERE urakka = :urakka;

-- name: onko-suolasakko-kaytossa?
SELECT EXISTS(SELECT id FROM suolasakko WHERE urakka=:urakka AND kaytossa=true) as kaytossa;
