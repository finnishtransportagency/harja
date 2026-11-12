-- name: hae-urakan-kalustoresurssit
-- Hakee urakan kalustoresurssit hoitoluokkaryhmittäin
  SELECT id,
         urakka_id        AS "urakka-id",
         hoitoluokkaryhma,
         maara,
         muokattu
    FROM suunnittelu_kalustoresurssi
   WHERE urakka_id = :urakka-id
     AND poistettu IS NOT TRUE;

-- name: tallenna-kalustoresurssi<!
-- Tallentaa tai päivittää yhden hoitoluokkaryhmän kalustomäärän
INSERT INTO suunnittelu_kalustoresurssi
       (urakka_id, hoitoluokkaryhma, maara, luoja, luotu)
VALUES (:urakka-id, :hoitoluokkaryhma, :maara, :kayttaja-id, NOW())
    ON CONFLICT (urakka_id, hoitoluokkaryhma)
    DO UPDATE SET maara     = EXCLUDED.maara,
                  muokkaaja = EXCLUDED.luoja,
                  muokattu  = NOW(),
                  poistettu = FALSE;
