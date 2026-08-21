-- name: hae-paikkaus-kustannukset
-- Hakee könttänä muiden paikkausten, reikäpaikkausten sekä ylläpidon kustannusten tiedot
SELECT id, 
       tyomenetelma, 
       kustannustyyppi,
       SUM(kokonaiskustannus) AS kokonaiskustannus,
       selite
FROM (
    -- Paikkauskohteiden kustannukset
    SELECT
        CONCAT('paikkauskohde-',pt.id)          AS id,
        NULL::mpu_kustannustyyppi_enum          AS kustannustyyppi,
        COALESCE(SUM(pk."toteutunut-hinta"), 0) AS kokonaiskustannus,
        pt.nimi                                 AS tyomenetelma,
        ''                                      AS selite
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

    -- Reikäpaikkausten kustannukset
    SELECT
        CONCAT('reikapaikkaus-tyomenetelma-',pt.id) AS id,
        NULL::mpu_kustannustyyppi_enum              AS kustannustyyppi,
        COALESCE(SUM(p.kustannus), 0)               AS kokonaiskustannus,
        pt.nimi                                     AS tyomenetelma,
        ''                                          AS selite
    FROM     
        paikkauskohde_tyomenetelma pt
    LEFT JOIN 
        paikkaus p ON pt.id = p.tyomenetelma AND p."urakka-id" = :urakka-id
                  AND (:alkuaika::DATE IS NULL OR p.alkuaika >= :alkuaika::DATE)
                  AND (:loppuaika::DATE IS NULL OR p.loppuaika <= :loppuaika::DATE)
                  AND p.poistettu = FALSE
                  AND p."paikkaus-tyyppi" = 'reikapaikkaus'
    GROUP BY 
        pt.nimi, pt.id

    UNION ALL
    
    -- Muut paikkauskustannukset
    SELECT CONCAT('kustannus-',id)                  AS id,
		   kustannustyyppi,
		   SUM(summa)                               AS kokonaiskustannus,
		   ''                                       AS tyomenetelma,
	     selite
	  FROM
          paikkauskustannukset
    WHERE
        urakka = :urakka-id
        AND poistettu IS FALSE
      -- Samalla kyselyllä haetaan yksittäisen vuoden kustannukset sekä useamman vuoden kustannukset
        AND (:vuosi::INTEGER IS NULL OR (:vuosi::INTEGER IS NOT NULL AND vuosi = :vuosi))
        AND (:alkuvuosi::INTEGER IS NULL AND :loppuvuosi::INTEGER IS NULL
                 OR (:alkuvuosi::INTEGER IS NOT NULL
        AND :alkuvuosi::INTEGER IS NOT NULL AND vuosi BETWEEN :alkuvuosi::INTEGER AND :loppuvuosi::INTEGER))
    GROUP BY
        id, selite, kustannustyyppi
) AS kustannukset
GROUP BY tyomenetelma, id, kustannustyyppi, selite
ORDER BY tyomenetelma, id;


-- name: tallenna-yllapito-kustannus!
WITH paivitetty AS (
UPDATE paikkauskustannukset
   SET urakka = :urakka-id,
       selite = :selite,
       kustannustyyppi = :kustannustyyppi::mpu_kustannustyyppi_enum,
       summa = :summa,
       vuosi = :vuosi,
       poistettu = :poistettu,
       muokattu = NOW(),
       muokkaaja = :luoja
WHERE id = :id
    RETURNING *
) INSERT INTO paikkauskustannukset (
    urakka,
    selite,
    kustannustyyppi,
    summa,
    vuosi,
    poistettu,
    luotu,
    luoja
) SELECT
    :urakka-id,
    :selite,
    :kustannustyyppi::mpu_kustannustyyppi_enum,
    :summa,
    :vuosi,
    -- Uusille riveille poistettu aina false 
    FALSE,
    NOW(),
    COALESCE(:luoja, (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'))
    WHERE NOT EXISTS (SELECT 1 FROM paivitetty)
RETURNING *;


-- name: hae-kustannusten-selitteet
SELECT DISTINCT(selite) FROM paikkauskustannukset WHERE urakka = :urakka-id;
