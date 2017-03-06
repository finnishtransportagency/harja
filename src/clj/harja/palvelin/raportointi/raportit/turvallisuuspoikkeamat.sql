-- name: hae-turvallisuuspoikkeamat
-- Hakee turvallisuuspoikkeamat aikavälillä
SELECT
t.id,
t.tapahtunut,
t.kasitelty,
t.tyontekijanammatti,
t.tyontekijanammatti_muu as tyontekijanammattimuu,
t.kuvaus,
t.vammat,
t.sairauspoissaolopaivat,
t.sairaalavuorokaudet,
t.tyyppi,
t.vakavuusaste,
u.id as urakka_id,
u.nimi as urakka_nimi,
o.id as hallintayksikko_id,
o.nimi as hallintayksikko_nimi
  FROM turvallisuuspoikkeama t
       JOIN urakka u ON t.urakka = u.id AND u.urakkanro IS NOT NULL
       JOIN organisaatio o ON u.hallintayksikko = o.id
 WHERE (:urakka_annettu IS FALSE OR t.urakka = :urakka)
       AND (:hallintayksikko_annettu IS FALSE OR t.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko))
       AND (:urakka_annettu IS TRUE OR (:urakka_annettu IS FALSE AND (:urakkatyyppi::urakkatyyppi IS NULL OR u.tyyppi = :urakkatyyppi :: urakkatyyppi)))
       AND t.tapahtunut :: DATE BETWEEN :alku AND :loppu;
