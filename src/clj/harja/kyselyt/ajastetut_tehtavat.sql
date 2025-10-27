-- name: paivita-ajastetun-tehtavan-onnistuminen!
UPDATE ajastetut_tehtavat
SET suoritusyritys_aika = NOW(), onnistunut = :onnistunut
WHERE nimi = 'siirra_toteumat_analytiikalle';

-- name: paivita-viimeisin-onnistuminen!
UPDATE ajastetut_tehtavat SET viimeisin_onnistunut = NOW()
WHERE nimi = 'siirra_toteumat_analytiikalle';
