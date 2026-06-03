-- name: tallenna-jarjestelman-tila<!
INSERT INTO jarjestelman_tila (palvelimen_osoite, palvelimen_versio, tila, "osa-alue", paivitetty)
  VALUES (:palvelimen-osoite, :palvelimen-versio, :tila::JSONB, :osa-alue, NOW())
  ON CONFLICT (palvelimen_osoite, palvelimen_versio, "osa-alue")
    DO UPDATE SET tila = :tila::JSONB,
                  paivitetty = NOW();

-- name: hae-jarjestelman-tila
SELECT palvelimen_osoite, palvelimen_versio, tila, paivitetty
FROM jarjestelman_tila
WHERE "osa-alue"=:osa-alue AND
      (:kehitys? IS TRUE OR
       palvelimen_versio = :palvelimen-versio)
ORDER BY paivitetty DESC;

-- name: hae-jarjestelman-asetukset
SELECT valikatselmus_validoinnit_kaytossa, arvonvahennys_validoinnit_kaytossa FROM jarjestelman_asetukset;

-- name: toggle-valikatselmus-validoinnit!
UPDATE jarjestelman_asetukset
   SET valikatselmus_validoinnit_kaytossa = :validoinnit,
       muokattu = NOW(),
       muokkaaja = :kayttajaid;

-- name: toggle-arvonvahennys-validoinnit!
UPDATE jarjestelman_asetukset
SET arvonvahennys_validoinnit_kaytossa = :validoinnit,
    muokattu = NOW(),
    muokkaaja = :kayttajaid;
