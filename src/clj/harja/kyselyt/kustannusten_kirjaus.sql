-- name: hae-tiemerkinta-kustannuskirjaus
SELECT
    ukk.id,
    ukk.urakka,
    ukk.kustannusvuosi,
    ukk.kustannus,
    ukk.pk1,
    ukk.pk2,
    ukk.pk3
    FROM tiemerkinta_korjauskustannus ukk
WHERE ukk.urakka = :urakka
ORDER BY ukk.kustannusvuosi ASC;

--name: tallenna-tiemerkinta-kustannuskirjaus
INSERT INTO tiemerkinta_korjauskustannus (urakka, luoja, muokattu, muokkaaja, kustannusvuosi, kustannus, pk1, pk2, pk3)
VALUES (
      :urakka,
      :luoja,
      :muokattu,
      :muokkaaja,
      :kustannusvuosi,
      :kustannus,
      :pk1,
      :pk2,
      :pk3)
 ON CONFLICT (urakka, kustannusvuosi)
 DO UPDATE SET urakka = :urakka, muokattu = :muokattu, muokkaaja = :muokkaaja, kustannusvuosi = :kustannusvuosi, kustannus = :kustannus, pk1 = :pk1, pk2 = :pk2, pk3 = :pk3
RETURNING urakka;
