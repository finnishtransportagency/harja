-- name: poista-urakan-yha-tiedot
-- Poistaa urakan yha-tiedot
DELETE FROM yhatiedot WHERE urakka = :urakka;

-- name: lisaa-urakalle-yha-tiedot
-- Lisää urakalle YHA-tiedot
INSERT INTO yhatiedot (urakka, yhatunnus, yhaid, yhanimi, elyt, vuodet, kohdeluettelo_paivitetty, luotu, linkittaja, muokattu)
    VALUES (:urakka, :yhatunnus, :yhaid, :yhanimi, :elyt, :vuodet, null, NOW(), :kayttaja, NOW());

-- name: paivita-yhatietojen-kohdeluettelon-paivitysaika
-- Päivittää urakan YHA-tietoihin kohdeluettelon uudeksi päivitysajaksi nykyhetken
UPDATE yhatiedot SET
    kohdeluettelo_paivitetty = NOW(),
    muokattu = NOW()
WHERE urakka = :urakka;

-- name: hae-urakka-yhatietoineen
SELECT
    u.id,
    u.nimi,
    u.sampoid,
    u.alue,
    u.alkupvm,
    u.loppupvm,
    u.tyyppi,
    u.sopimustyyppi,
    hal.id                   AS hallintayksikko_id,
    hal.nimi                 AS hallintayksikko_nimi,
    hal.lyhenne              AS hallintayksikko_lyhenne,
    urk.id                   AS urakoitsija_id,
    urk.nimi                 AS urakoitsija_nimi,
    urk.ytunnus              AS urakoitsija_ytunnus,
    yt.yhatunnus             AS yhatiedot_yhatunnus,
    yt.yhaid                 AS yhatiedot_yhaid,
    yt.yhanimi               AS yhatiedot_yhanimi,
    yt.elyt                  AS yhatiedot_elyt,
    yt.vuodet                AS yhatiedot_vuodet,
    (SELECT array_agg(concat(id, '=', sampoid))
     FROM sopimus s
     WHERE urakka = u.id)    AS sopimukset,
    ST_Simplify(au.alue, 50) AS alueurakan_alue
FROM urakka u
    LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
    LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
    LEFT JOIN hanke h ON u.hanke = h.id
    LEFT JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
    LEFT JOIN yhatiedot yt ON u.id = yt.urakka
WHERE u.id = :urakka;