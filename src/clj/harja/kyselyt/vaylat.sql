-- name: luo-tai-paivita-vayla<!
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
ON CONFLICT (vaylanro)
  DO UPDATE
    SET
      sijainti  = ST_GeomFromGeoJSON(:sijainti),
      tunniste  = :tunniste,
      vaylanro  = :vaylanro,
      nimi      = :nimi,
      tyyppi    = :tyyppi :: VV_VAYLATYYPPI,
      arvot     = :arvot :: JSONB,
      poistettu = FALSE;
