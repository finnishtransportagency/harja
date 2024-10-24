-- name: uusi-lampotila<!
INSERT INTO lampotilat (urakka, alkupvm, loppupvm, keskilampotila,
                        keskilampotila_1991_2020, keskilampotila_1981_2010, keskilampotila_1971_2000)
VALUES (:urakka, :alku, :loppu, :keskilampo, :lampo_91, :lampo_81, :lampo_71);

-- name: paivita-lampotila<!
UPDATE lampotilat SET
  urakka = :urakka, alkupvm = :alku, loppupvm = :loppu, keskilampotila = :keskilampo,
  keskilampotila_1991_2020 = :lampo_91, keskilampotila_1981_2010 = :lampo_81, keskilampotila_1971_2000 = :lampo_71
WHERE id = :id;

-- name: hae-urakan-suolasakot
-- Hakee urakan suolasakot urakan id:llä
SELECT
  ss.id, ss.maara, ss.vainsakkomaara, ss.hoitokauden_alkuvuosi, ss.maksukuukausi, ss.indeksi, ss.urakka,
  ss.kaytossa, ss.talvisuolaraja
FROM suolasakko ss
  WHERE ss.urakka = :urakka;

-- name: hae-urakoiden-talvisuolarajat
-- Hakee useamman urakan talvisuolan käyttörajat urakoiden id:illä,
-- joilla suolaraja on käytössä (ss.kaytossa)
SELECT ss.urakka as urakka_id,
       u.nimi as urakka_nimi,
       ss.talvisuolaraja
  FROM suolasakko ss
    JOIN urakka u ON ss.urakka = u.id
 WHERE ss.urakka in (:urakka_idt)
       AND ss.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
       AND ss.kaytossa IS TRUE
GROUP BY ss.urakka, u.nimi, ss.talvisuolaraja;

-- name: hae-urakan-lampotilat
-- Hakee urakan lämpotilat urakan id:llä
SELECT
  id,
  alkupvm,
  loppupvm,
  keskilampotila,
  keskilampotila_1991_2020 as "keskilampotila-1991-2020",
  keskilampotila_1981_2010 as "keskilampotila-1981-2010",
  keskilampotila_1971_2000 as "keskilampotila-1971-2000"
FROM lampotilat
WHERE urakka = :urakka;

-- name: hae-urakan-pohjavesialue-talvisuolarajat
SELECT *
  FROM pohjavesialue_talvisuola pt
 WHERE pt.urakka = :urakka;

-- name: hae-urakan-pohjavesialue-talvisuolarajat-teittain
SELECT pa.nimi AS nimi, pa.tunnus AS pohjavesialue, pt.urakka, pt.hoitokauden_alkuvuosi, pt.talvisuolaraja, pa.tr_numero AS tie
  FROM (SELECT DISTINCT nimi, tunnus, tr_numero FROM pohjavesialue) AS pa 
  LEFT JOIN pohjavesialue_talvisuola pt ON pt.pohjavesialue = pa.tunnus AND pa.tr_numero = pt.tie
  WHERE pt.urakka = :urakka ORDER by pa.nimi ASC;

-- name: luo-suolasakko<!
INSERT INTO suolasakko (maara, vainsakkomaara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, luotu, luoja, talvisuolaraja, kaytossa)
    VALUES (:maara, :vainsakkomaara, :hoitokauden_alkuvuosi, :maksukuukausi, :indeksi, :urakka, NOW(), :kayttaja, :talvisuolaraja, :kaytossa);

-- name: paivita-suolasakko!
UPDATE suolasakko
   SET maara = :maara, vainsakkomaara = :vainsakkomaara, maksukuukausi = :maksukuukausi,
       indeksi = :indeksi, muokattu = NOW(), muokkaaja = :kayttaja,
       talvisuolaraja = :talvisuolaraja, kaytossa = :kaytossa
 WHERE id = :id;

-- name: hae-suolasakko-id
SELECT id FROM suolasakko WHERE urakka = :urakka AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi;

-- name: paivita-pohjavesialue-talvisuola!
UPDATE pohjavesialue_talvisuola
   SET talvisuolaraja = :talvisuolaraja,
       muokkaaja = :muokaaja,
       muokattu = current_timestamp
 WHERE urakka = :urakka AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi AND pohjavesialue = :pohjavesialue AND tie = :tie;

-- name: tallenna-pohjavesialue-talvisuola<!
INSERT INTO pohjavesialue_talvisuola
       (talvisuolaraja, urakka, hoitokauden_alkuvuosi, pohjavesialue, tie, luoja, luotu)
VALUES (:talvisuolaraja, :urakka, :hoitokauden_alkuvuosi, :pohjavesialue, :tie, :luoja, current_timestamp);

-- name: hae-teiden-hoitourakoiden-lampotilat
SELECT
  lt.id as lampotilaid,
  u.id as urakka,
  u.nimi as nimi,
  lt.alkupvm as alkupvm,
  lt.loppupvm as loppupvm,
  lt.keskilampotila as keskilampotila,
  lt.keskilampotila_1991_2020 as "keskilampotila-1991-2020",
  lt.keskilampotila_1981_2010 as "keskilampotila-1981-2010",
  lt.keskilampotila_1971_2000 as "keskilampotila-1971-2020"
FROM urakka u
  LEFT JOIN lampotilat lt ON (lt.urakka = u.id AND lt.alkupvm = :alkupvm AND lt.loppupvm = :loppupvm)
WHERE (u.tyyppi IN ('hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi) AND
       u.alkupvm <= :alkupvm AND
       :loppupvm <= u.loppupvm);
