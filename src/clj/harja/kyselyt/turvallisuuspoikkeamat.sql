-- name: hae-urakan-turvallisuuspoikkeamat
SELECT
  t.id,
  t.urakka,
  t.tapahtunut,
  t.paattynyt,
  t.kasitelty,
  t.tyontekijanammatti,
  t.tyotehtava,
  t.kuvaus,
  t.vammat,
  t.sairauspoissaolopaivat,
  t.sairaalavuorokaudet,
  t.sijainti,
  t.tr_numero,
  t.tr_alkuetaisyys,
  t.tr_loppuetaisyys,
  t.tr_alkuosa,
  t.tr_loppuosa,
  t.tyyppi,

  k.id              AS korjaavatoimenpide_id,
  k.kuvaus          AS korjaavatoimenpide_kuvaus,
  k.suoritettu      AS korjaavatoimenpide_suoritettu,
  k.vastaavahenkilo AS korjaavatoimenpide_vastaavahenkilo,

  kom.id            AS kommentti_id,
  kom.tekija        AS kommentti_tekija,
  kom.kommentti     AS kommentti_kommentti,
  kom.liite         AS kommentti_liite,

  l.id              AS liite_id,
  l.tyyppi          AS liite_tyyppi,
  l.koko            AS liite_koko,
  l.nimi            AS liite_nimi,
  l.liite_oid       AS liite_oid,
  l.pikkukuva       AS liite_pikkukuva

FROM turvallisuuspoikkeama t
  LEFT JOIN korjaavatoimenpide k
    ON t.id = k.turvallisuuspoikkeama

  LEFT JOIN turvallisuuspoikkeama_liite tl
    ON t.id = tl.turvallisuuspoikkeama
  LEFT JOIN liite l
    ON l.id = tl.liite

  LEFT JOIN turvallisuuspoikkeama_kommentti tpk
    ON t.id = tpk.turvallisuuspoikkeama
  LEFT JOIN kommentti kom
    ON tpk.kommentti = kom.id
       AND kom.poistettu IS NOT TRUE
WHERE t.urakka = :urakka
      AND t.tapahtunut :: DATE BETWEEN :alku AND :loppu;

-- name: liita-kommentti<!
INSERT INTO turvallisuuspoikkeama_kommentti (turvallisuuspoikkeama, kommentti)
VALUES (:turvallisuuspoikkeama, :kommentti);