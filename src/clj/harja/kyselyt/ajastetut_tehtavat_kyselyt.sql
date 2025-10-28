-- name: paivita-ajastetun-tehtavan-onnistuminen!
UPDATE ajastetut_tehtavat
SET suoritusyritys_aika = NOW(),
    onnistunut          = :onnistunut
  WHERE tyyppi = :tyyppi :: ajastetuntehtavan_tyyppi;

-- name: paivita-viimeisin-onnistuminen!
UPDATE ajastetut_tehtavat
    SET viimeisin_onnistunut = NOW()
  WHERE tyyppi = :tyyppi :: ajastetuntehtavan_tyyppi;
