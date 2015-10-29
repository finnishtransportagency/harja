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
SELECT id FROM suolasakko WHERE urakka = :urakka AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi;

-- name: paivita-pohjavesialue-talvisuola!
UPDATE pohjavesialue_talvisuola
   SET talvisuolaraja = :talvisuolaraja
 WHERE urakka = :urakka AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi AND pohjavesialue = :pohjavesialue;

-- name: tallenna-pohjavesialue-talvisuola<!
INSERT INTO pohjavesialue_talvisuola
       (talvisuolaraja, urakka, hoitokauden_alkuvuosi, pohjavesialue)
VALUES (:talvisuolaraja, :urakka, :hoitokauden_alkuvuosi, :pohjavesialue);

 
 
-- name: aseta-suolasakon-kaytto!
UPDATE suolasakko
   SET kaytossa = :kaytossa
 WHERE urakka = :urakka;

-- name: onko-suolasakko-kaytossa?
SELECT EXISTS(SELECT id FROM suolasakko WHERE urakka=:urakka AND kaytossa=true) as kaytossa;

-- name: hae-teiden-hoitourakoiden-lampotilat
SELECT
  lt.id as lampotilaid,
  u.id as urakka,
  u.nimi as nimi,
  lt.alkupvm as alkupvm,
  lt.loppupvm as loppupvm,
  lt.keskilampotila as keskilampotila,
  lt.pitka_keskilampotila as pitkakeskilampotila
FROM urakka u
  LEFT JOIN lampotilat lt ON (lt.urakka = u.id AND lt.alkupvm = :alkupvm AND lt.loppupvm = :loppupvm)
WHERE (u.tyyppi = 'hoito'::urakkatyyppi AND
       u.alkupvm <= :alkupvm AND
       :loppupvm <= u.loppupvm);