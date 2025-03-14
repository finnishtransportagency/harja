-- name: hae-tiemerkinta-kustannuskirjaukset
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

-- name: hae-tiemerkinta-kustannuskirjaus-kustannusvuodella
SELECT
    ukk.id,
    ukk.urakka,
    ukk.kustannusvuosi
    FROM tiemerkinta_korjauskustannus ukk
WHERE ukk.urakka = :urakka AND ukk.kustannusvuosi = :kustannusvuosi
ORDER BY ukk.kustannusvuosi ASC;

--name: lisaa-tiemerkinta-kustannuskirjaus!
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
           :pk3);

--name: paivita-tiemerkinta-kustannuskirjaus!
UPDATE tiemerkinta_korjauskustannus
SET
    muokattu = :muokattu,
    muokkaaja = :muokkaaja,
    kustannus = :kustannus,
    pk1 = :pk1,
    pk2 = :pk2,
    pk3 = :pk3
WHERE urakka = :urakka AND kustannusvuosi = :kustannusvuosi;

--name: tallenna-tiemerkinta-kustannuskirjaus!
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
