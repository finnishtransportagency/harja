-- name: hae-turvallisuuspoikkeamat
-- Hakee turvallisuuspoikkeamat aikavälillä
SELECT t.id, t.tapahtunut, t.paattynyt, t.kasitelty, t.tyontekijanammatti, t.tyontekijanammatti_muu as tyontekijanammattimuu,
       t.tyotehtava, t.kuvaus, t.vammat, t.sairauspoissaolopaivat, t.sairaalavuorokaudet, t.tyyppi,
      t.vakavuusaste,
       u.nimi as urakka_nimi,
       u.id as urakka_id
  FROM turvallisuuspoikkeama t
       JOIN urakka u ON t.urakka = u.id 
 WHERE (:urakka_annettu IS FALSE OR t.urakka = :urakka)
       AND (:hallintayksikko_annettu IS FALSE OR t.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko))
       AND t.tapahtunut :: DATE BETWEEN :alku AND :loppu;
