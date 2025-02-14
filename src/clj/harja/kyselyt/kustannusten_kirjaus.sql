-- name: hae-tiemerkinnan-kustannuskirjaukset
SELECT
    ukk.id,
    ukk.urakka,
    ukk.kustannusvuosi,
    ukk.kustannus,
    ukk.pk1,
    ukk.pk2,
    ukk.pk3
    FROM tiemerkinta_korjauskustannukset ukk
WHERE ukk.urakka = :urakka_id
ORDER BY ukk.kustannusvuosi ASC;
