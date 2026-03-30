-- name: lisaa_ajastettu_tehtava!
INSERT INTO ajastetut_tehtavat (tyyppi, alkuaika_valilta, loppuaika_valilta, onnistunut, virhe, luotu)
VALUES (:tyyppi::ajastettu_tehtava, :alkuaika_valilta, :loppuaika_valilta, :onnistunut, :virhe, NOW());

-- name: hae-viimeisin-onnistunut-ajokerta
SELECT alkuaika_valilta, loppuaika_valilta, onnistunut, virhe, luotu
  FROM ajastetut_tehtavat
 WHERE tyyppi = :tyyppi::ajastettu_tehtava
   AND onnistunut = TRUE
 ORDER BY loppuaika_valilta DESC
 LIMIT 1;
