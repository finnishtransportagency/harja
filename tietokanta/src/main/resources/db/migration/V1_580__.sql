ALTER TABLE organisaatio ADD COLUMN postitoimipaikka VARCHAR(64);


SELECT
  urk.id,
  urk.nimi,
  urk.ytunnus,
  urk.katuosoite,
  urk.postinumero,
  u.nimi AS urakka_nimi,
  u.id AS urakka_id,
  u.alkupvm AS urakka_alkupvm,
  u.loppupvm AS urakka_loppupvm
FROM organisaatio urk
  LEFT JOIN urakka u ON urk.id = u.urakoitsija
WHERE urk.tyyppi = 'urakoitsija'::organisaatiotyyppi;


SELECT y.id, y.ytunnus, y.nimi,
  u.tyyppi as urakkatyyppi
FROM urakka u
  LEFT JOIN organisaatio y ON u.urakoitsija = y.id
WHERE y.tyyppi = 'urakoitsija'::organisaatiotyyppi;