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

  k.id AS korjaavatoimenpide_id,
  k.kuvaus AS korjaavatoimenpide_kuvaus,
  k.suoritettu AS korjaavatoimenpide_suoritettu,
  k.vastaavahenkilo AS korjaavatoimenpide_vastaavahenkilo,

  l.id AS liite_id,
  l.tyyppi AS liite_tyyppi,
  l.koko AS liite_koko,
  l.nimi AS liite_nimi,
  l.liite_oid AS liite_oid,
  l.pikkukuva AS liite_pikkukuva
FROM turvallisuuspoikkeama t
LEFT JOIN korjaavatoimenpide k
    ON t.id = k.turvallisuuspoikkeama
LEFT JOIN turvallisuuspoikkeama_liite tl
    ON t.id = tl.turvallisuuspoikkeama
LEFT JOIN liite l
    ON l.id = tl.liite
WHERE t.urakka = :urakka
      AND t.tapahtunut::DATE BETWEEN :alku AND :loppu;