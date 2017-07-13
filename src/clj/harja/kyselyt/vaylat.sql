-- name: poista-vaylat!
UPDATE vv_vayla
SET poistettu = TRUE
WHERE tunniste IS NOT NULL;

-- name: luo-vayla<!
INSERT INTO vv_vayla
(sijainti,
 tunniste,
 vaylanro,
 nimi,
 tyyppi,
 arvot,
 poistettu)
VALUES (ST_GeomFromGeoJSON(:sijainti),
        :tunniste,
        :vaylanro,
        :nimi,
        :tyyppi :: VV_VAYLATYYPPI,
        :arvot :: JSONB,
        FALSE)
ON CONFLICT (tunniste)
  DO UPDATE
    SET
      sijainti  = ST_GeomFromGeoJSON(:sijainti),
      tunniste  = :tunniste,
      vaylanro  = :vaylanro,
      nimi      = :nimi,
      tyyppi    = :tyyppi :: VV_VAYLATYYPPI,
      arvot     = :arvot :: JSONB,
      poistettu = FALSE;

