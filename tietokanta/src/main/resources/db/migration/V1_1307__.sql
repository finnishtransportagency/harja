-- Siirretään MHU25- ja MHU26-profiilien laskutusrajan ylityksen laskentasääntö profiiliin.
-- Palvelin laskee summan profiilin perusteella; ohjeteksti ei ole laskentakaava.
WITH integraatio AS (
    SELECT id
    FROM kayttaja
    WHERE kayttajanimi = 'Integraatio'
)
UPDATE sanktio_profiili_rivi_summamaaritys sprsm
SET maaritystapa = 'laskettu',
    summa_euroina = NULL,
    ohjeteksti = '20 % summasta, joka on laskutettu yli',
    laskentatapa = 'prosenttiosuus_syotteesta',
    laskentaparametrit = '{"syoteavain": "laskutusrajan_ylitys", "yksikko": "€", "desimaalit": 2, "prosentti": 20}'::JSONB,
    laskentaversio = 1,
    muokkaaja = (SELECT id FROM integraatio),
    muokattu = CURRENT_TIMESTAMP
FROM sanktio_profiili_rivi spr
JOIN sanktio_profiili sp ON sp.id = spr.sanktio_profiili_id
JOIN sanktio_laji sl ON sl.id = spr.sanktio_laji_id
JOIN sanktiotyyppi st ON st.id = spr.sanktiotyyppi_id
WHERE sprsm.sanktio_profiili_rivi_id = spr.id
  AND sp.nimi IN ('teiden-hoito-mhu2025', 'teiden-hoito-mhu2026')
  AND sl.koodi = 'laskutus_yli_laskutusrajan'
  AND st.koodi = 0;
