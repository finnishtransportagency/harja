-- name: hae-paikkaus-kustannukset
SELECT   
    pt.id,
    COALESCE(SUM(p.kustannus), 0) AS kokonaiskustannus,
    pt.nimi AS tyomenetelma
 FROM     
    paikkauskohde_tyomenetelma pt
LEFT JOIN 
    paikkaus p ON pt.id = p.tyomenetelma AND p."urakka-id" = :urakka-id
              AND (:alkuaika::DATE IS NULL OR p.alkuaika >= :alkuaika::DATE)
              AND (:loppuaika::DATE IS NULL OR p.loppuaika <= :loppuaika::DATE)
              AND p.poistettu = FALSE
GROUP BY 
    pt.id, pt.nimi ORDER BY tyomenetelma;


-- name: tallenna-mpu-kustannus!
INSERT INTO mpu_kustannukset (urakka, selite, summa, vuosi) VALUES (:urakka-id, :selite, :summa, :vuosi);
