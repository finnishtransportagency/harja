-- name: hae-urakat-joilla-puuttuu-kolmanneksen-tunnit
-- Tarkistaa löytyykö toteumaa ulkoisella id:llä
SELECT
  u.id,
  u.sampoid,
  u.nimi,
  hallintayksikko
FROM urakka u
WHERE NOT exists(SELECT ut.id
                 FROM urakan_tyotunnit ut
                 WHERE ut.urakka = u.id AND
                       ut.vuosi = :vuosi AND
                       ut.vuosikolmannes = :vuosikolmannes);

-- hae-urakan-tiedot-lahettavaksi-tyotuntien-kanssa
SELECT
  u.urakkanro        AS alueurakkanro,
  u.sampoid          AS "urakka-sampoid",
  hlo.kayttajatunnus AS "tilaajanvastuuhenkilo-kayttajatunnus",
  hlo.etunimi        AS "tilaajanvastuuhenkilo-etunimi",
  hlo.sukunimi       AS "tilaajanvastuuhenkilo-sukunimi",
  hlo.sahkoposti     AS "tilaajanvastuuhenkilo-sposti",
  u.tyyppi           AS "urakka-tyyppi",
  o.lyhenne          AS "urakka-ely",
  u.loppupvm         AS "urakka-loppupvm",
  u.nimi             AS "urakka-nimi",
  h.nimi             AS "hanke-nimi",
  h.sampoid          AS "hanke-sampoid"
FROM urakka u
  LEFT JOIN urakanvastuuhenkilo hlo
    ON hlo.urakka = u.id AND hlo.id = (SELECT id
                                       FROM urakanvastuuhenkilo
                                       WHERE rooli = 'ELY_Urakanvalvoja' AND urakka = u.id
                                       ORDER BY ensisijainen DESC
                                       LIMIT 1)
  LEFT JOIN hanke h
    ON u.hanke = h.id
  LEFT JOIN organisaatio o ON u.hallintayksikko = o.id
WHERE u.id = :urakkaid;
