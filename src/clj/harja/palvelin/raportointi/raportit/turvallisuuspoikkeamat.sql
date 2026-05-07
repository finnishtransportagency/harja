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
o.id as elinvoimakeskus_id,
o.nimi as elinvoimakeskus_nimi
  FROM turvallisuuspoikkeama t
       JOIN urakka u ON t.urakka = u.id AND u.urakkanro IS NOT NULL
       JOIN organisaatio o ON u.elinvoimakeskus_id = o.id
 WHERE (:urakka_annettu IS FALSE OR t.urakka = :urakka)
       AND (:elinvoimakeskus_annettu IS FALSE OR t.urakka IN (SELECT id FROM urakka WHERE elinvoimakeskus_id = :elinvoimakeskus))
       AND (:urakka_annettu IS TRUE OR
            (:urakka_annettu IS FALSE AND
             (TRUE IN (SELECT unnest(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR
              u.tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[]))))
       AND t.tapahtunut :: DATE BETWEEN :alku AND :loppu;
