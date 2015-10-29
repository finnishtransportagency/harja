-- Lisää funktio päällystys-/paikkausurakoiden geometrian päivittämiselle
CREATE OR REPLACE FUNCTION paivita_paallystys_ja_paikkausurakoiden_geometriat() RETURNS VOID AS $$
BEGIN
  UPDATE urakka
  SET alue = uusi_alue
  FROM (SELECT u.id, u.nimi, ST_BUFFER(ST_UNION(sijainti), 100) AS uusi_alue
        FROM paallystyskohdeosa osa
          JOIN paallystyskohde pk ON osa.paallystyskohde = pk.id
          JOIN urakka u ON pk.urakka = u.id
        GROUP BY u.id, u.nimi) AS geometriat
  WHERE urakka.id = geometriat.id;
  RETURN;
END;
$$ LANGUAGE plpgsql;