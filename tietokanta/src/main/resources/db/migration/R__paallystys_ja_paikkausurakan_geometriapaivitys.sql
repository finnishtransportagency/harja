CREATE OR REPLACE FUNCTION paivita_paallystys_ja_paikkausurakoiden_geometriat()
  RETURNS VOID AS $$
BEGIN
  UPDATE urakka
  SET alue = uusi_alue
  FROM (SELECT
          u.id,
          u.nimi,
          ST_BUFFER(ST_SIMPLIFY(ST_UNION(sijainti), 3000), 3000) AS uusi_alue
        FROM yllapitokohdeosa osa
          JOIN yllapitokohde ypk ON osa.yllapitokohde = ypk.id AND ypk.poistettu IS NOT TRUE
          JOIN urakka u ON ypk.urakka = u.id
        WHERE u.tyyppi = 'paallystys' OR u.tyyppi = 'paikkaus'
        GROUP BY u.id, u.nimi) AS geometriat
  WHERE urakka.id = geometriat.id;
  RETURN;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_paallystys_tai_paikkausurakan_geometria(urakkaid INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE urakka
  SET alue = uusi_alue
  FROM (SELECT
          u.id,
          u.nimi,
          ST_BUFFER(ST_SIMPLIFY(ST_COLLECT(sijainti), 3000), 3000) AS uusi_alue
        FROM yllapitokohdeosa osa
          JOIN yllapitokohde ypk ON osa.yllapitokohde = ypk.id AND ypk.poistettu IS NOT TRUE
          JOIN urakka u ON ypk.urakka = u.id
        WHERE (u.tyyppi = 'paallystys' OR u.tyyppi = 'paikkaus')
              AND u.id = urakkaid
        GROUP BY u.id, u.nimi) AS geometriat
  WHERE urakka.id = urakkaid AND geometriat.id = urakkaid;
  RETURN;
END;
$$ LANGUAGE plpgsql;

SELECT paivita_paallystys_ja_paikkausurakoiden_geometriat();
