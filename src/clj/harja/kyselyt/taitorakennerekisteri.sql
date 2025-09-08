-- name: hae-siltatarkastukset-taitorakennerekisterille
SELECT
  st.id as siltatarkastus_id,
  st.tarkastusaika,
  st.tarkastaja,
  st.luotu,
  st.muokattu,
  st.poistettu,
  -- Urakka tiedot
  u.id as urakka_id,
  u.nimi as urakka_nimi,
  u.sampoid as urakka_tunnus,
  u.hallintayksikko as urakka_hallintayksikko,
  -- Silta tiedot
  s.id as silta_id,
  s.siltatunnus,
  s.siltanimi,
  s.silta_oid,
  s.siltaid,
  -- Tarkastuskohteet
  (SELECT json_agg(
    json_build_object(
      'kohde_id', sk.kohde,
      'tulos', sk.tulos,
      'lisatieto', sk.lisatieto,
      'liitteet', (
        SELECT json_agg(
          json_build_object(
            'liite_id', l.id,
            'liite_oid', l.liite_oid,
            'nimi', l.nimi,
            'tyyppi', l.tyyppi,
            'koko', l.koko,
            'kuvaus', l.kuvaus,
            'virustarkastettu', l."virustarkastettu?"
          )
        )
        FROM siltatarkastus_kohde_liite skl
        JOIN liite l ON skl.liite = l.id
        WHERE skl.siltatarkastus = st.id
        AND skl.kohde = sk.kohde
      )
    )
  ) FROM siltatarkastuskohde sk WHERE sk.siltatarkastus = st.id) as tarkastuskohteet
FROM siltatarkastus st
JOIN urakka u ON st.urakka = u.id
JOIN silta s ON st.silta = s.id
WHERE (st.luotu BETWEEN :alkuaika AND :loppuaika)
   OR (st.muokattu BETWEEN :alkuaika AND :loppuaika)
ORDER BY st.luotu DESC;

-- name: loytyyko-silta-oidilla
-- Palauttaa boolean arvon, joka kertoo löytyykö silta annetulla silta_oid:lla
SELECT EXISTS(
  SELECT 1
  FROM silta s
  WHERE s.silta_oid = :silta-oid
);

-- name: hae-sillan-siltatarkastukset-taitorakennerekisterille
SELECT
  st.id as siltatarkastus_id,
  st.tarkastusaika,
  st.tarkastaja,
  st.luotu,
  st.muokattu,
  st.poistettu,
  -- Urakka tiedot
  u.id as urakka_id,
  u.nimi as urakka_nimi,
  u.sampoid as urakka_tunnus,
  -- Silta tiedot
  s.id as silta_id,
  s.siltatunnus,
  s.siltanimi,
  s.silta_oid,
  s.siltaid,
  -- Tarkastuskohteet
  (SELECT json_agg(
    json_build_object(
      'kohde_id', sk.kohde,
      'tulos', sk.tulos,
      'lisatieto', sk.lisatieto,
      'liitteet', (
        SELECT json_agg(
          json_build_object(
            'liite_id', l.id,
            'liite_oid', l.liite_oid,
            'nimi', l.nimi,
            'tyyppi', l.tyyppi,
            'koko', l.koko,
            'kuvaus', l.kuvaus,
            'virustarkastettu', l."virustarkastettu?"
          )
        )
        FROM siltatarkastus_kohde_liite skl
        JOIN liite l ON skl.liite = l.id
        WHERE skl.siltatarkastus = st.id
        AND skl.kohde = sk.kohde
      )
    )
  ) FROM siltatarkastuskohde sk WHERE sk.siltatarkastus = st.id) as tarkastuskohteet
FROM siltatarkastus st
JOIN urakka u ON st.urakka = u.id
JOIN silta s ON st.silta = s.id
WHERE s.silta_oid = :silta-oid
ORDER BY st.luotu DESC;
