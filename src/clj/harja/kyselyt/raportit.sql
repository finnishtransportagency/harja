-- name: hae-raportit
-- Hakee kaikki raportit
SELECT r.nimi, r.kuvaus, r.konteksti, r.koodi, r.urakkatyyppi,
       r.nimi as id,             -- raportin ja sen 
       r.nimi as parametri_id,   -- parametrien yhdistämistä varten
       (unnest(parametrit)).nimi as parametri_nimi,
       (unnest(parametrit)).tyyppi as parametri_tyyppi,
       (unnest(parametrit)).pakollinen as parametri_pakollinen,
       (unnest(parametrit)).konteksti as parametri_konteksti
  FROM raportti r
 ORDER BY nimi;
