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

--name: tallenna-tiemerkinta-kustannuskirjaukset
INSERT INTO tiemerkinta_korjauskustannukset
    VALUES (kustannus = :kustannus,
      pk1 = :pk1,
      pk2 = :pk2,
      pk3 = :pk3)
  ON CONFLICT (kustannusvuosi, urakka)
  DO UPDATE SET kustannus = :kustannus, pk1 = :pk1, pk2 = :pk2, pk3 = :pk3;
