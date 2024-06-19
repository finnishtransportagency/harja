-- name: hae-paikkaus-kustannukset
SELECT id, 
       tyomenetelma, 
       SUM(kokonaiskustannus) AS kokonaiskustannus
FROM (
    -- Muut paikkaukset
    SELECT   
        pt.id AS id,
        -- 0 jos toteutunutta hintaa ei ole olemassa
        COALESCE(SUM(pk."toteutunut-hinta"), 0) AS kokonaiskustannus,
        pt.nimi AS tyomenetelma
    FROM     
        paikkauskohde pk
    LEFT JOIN 
        paikkauskohde_tyomenetelma pt ON pt.id = pk.tyomenetelma 
    WHERE 
        pk.poistettu = FALSE 
        AND (:alkuaika::DATE IS NULL OR pk.luotu >= :alkuaika::DATE)
        AND (:loppuaika::DATE IS NULL OR pk.luotu <= :loppuaika::DATE)
        AND pk."urakka-id" = :urakka-id
    GROUP BY 
        pt.nimi, pt.id

    UNION ALL

    -- Reikäpaikkaukset
    SELECT   
        pt.id AS id,
        -- 0 jos hintaa ei ole olemassa
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
        pt.nimi, pt.id
) AS kustannukset
GROUP BY tyomenetelma, id
ORDER BY tyomenetelma;


-- name: tallenna-mpu-kustannus!
INSERT INTO mpu_kustannukset (urakka, selite, summa, vuosi) VALUES (:urakka-id, :selite, :summa, :vuosi);


-- name: hae-mpu-kustannukset
SELECT kustannustyyppi,
       selite, 
       SUM(summa) AS summa
 FROM mpu_kustannukset
WHERE urakka = :urakka-id 
  AND vuosi = :vuosi GROUP BY selite, kustannustyyppi;


-- name: hae-mpu-kustannus-selitteet
SELECT DISTINCT(selite) FROM mpu_kustannukset WHERE urakka = :urakka-id;
