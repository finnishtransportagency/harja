-- name: lisaa_ajastettu_tehtava!
INSERT INTO ajastetut_tehtavat (tyyppi, suoritusyritys_aika, onnistunut, virhe)
VALUES (:tyyppi::ajastetuntehtavan_tyyppi, :ajankohta, :onnistunut, :virhe);

-- name: hae-viimeisin-onnistunut-ajokerta
-- single?:true
SELECT suoritusyritys_aika
  FROM ajastetut_tehtavat
 WHERE tyyppi = :tyyppi::ajastetuntehtavan_tyyppi
   AND onnistunut = TRUE
 ORDER BY suoritusyritys_aika DESC
 LIMIT 1;
