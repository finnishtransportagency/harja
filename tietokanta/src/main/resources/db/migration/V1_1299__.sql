-- Profiilin summamääritys voi olla rakenteisesti määritelty laskettava summa.
-- Palvelin toteuttaa vain tunnetut laskentatavat; ohjetekstiä ei koskaan tulkita kaavana.
ALTER TABLE sanktio_profiili_rivi_summamaaritys
    ADD COLUMN laskentatapa       TEXT,
    ADD COLUMN laskentaparametrit JSONB;

COMMENT ON COLUMN sanktio_profiili_rivi_summamaaritys.laskentatapa IS
    'Tyypitetty laskentatapa, pakollinen kun maaritystapa = laskettu. Palvelin tuntee vain check-rajoituksessa luetellut tavat.';
COMMENT ON COLUMN sanktio_profiili_rivi_summamaaritys.laskentaparametrit IS
    'Laskentatavan rakenteiset parametrit (syoteavain, yksikko, desimaalit, yksikkohinta tai prosentti, valinnaiset minimi ja maksimi) JSON-objektina.';

ALTER TABLE sanktio_profiili_rivi_summamaaritys
    DROP CONSTRAINT sanktio_profiili_rivi_summamaaritys_maaritystapa_check,
    DROP CONSTRAINT sanktio_profiili_rivi_summamaaritys_sisalto_check;

ALTER TABLE sanktio_profiili_rivi_summamaaritys
    ADD CONSTRAINT sanktio_profiili_rivi_summamaaritys_maaritystapa_check
        CHECK (maaritystapa IN ('automaattinen', 'manuaalinen', 'laskettu')),
    ADD CONSTRAINT sanktio_profiili_rivi_summamaaritys_laskentatapa_check
        CHECK (laskentatapa IS NULL OR laskentatapa IN ('tiekm_yksikkohinta', 'prosenttiosuus_syotteesta')),
    ADD CONSTRAINT sanktio_profiili_rivi_summamaaritys_laskentaparametrit_check
        CHECK (laskentaparametrit IS NULL OR jsonb_typeof(laskentaparametrit) = 'object'),
    ADD CONSTRAINT sanktio_profiili_rivi_summamaaritys_sisalto_check
        CHECK (
            (maaritystapa = 'automaattinen'
                AND summa_euroina IS NOT NULL
                AND laskentatapa IS NULL
                AND laskentaparametrit IS NULL)
                OR
            (maaritystapa = 'manuaalinen'
                AND (summa_euroina IS NOT NULL OR ohjeteksti IS NOT NULL)
                AND laskentatapa IS NULL
                AND laskentaparametrit IS NULL)
                OR
            (maaritystapa = 'laskettu'
                AND summa_euroina IS NULL
                AND laskentatapa IS NOT NULL
                AND laskentaparametrit IS NOT NULL)
            );

-- Laskettavan sanktion tapahtumasyöte ja laskennassa käytetty profiilin snapshot.
ALTER TABLE sanktio
    ADD COLUMN laskennan_syote JSONB;

COMMENT ON COLUMN sanktio.laskennan_syote IS
    'Vain laskettaville sanktioille: laskennassa käytetty syöte, laskentatapa ja profiilin laskentaparametrit tallennushetken snapshotina.';

ALTER TABLE sanktio
    DROP CONSTRAINT sanktio_maaritystapa_check;

ALTER TABLE sanktio
    ADD CONSTRAINT sanktio_maaritystapa_check
        CHECK (maaritystapa IS NULL OR maaritystapa IN ('automaattinen', 'manuaalinen', 'laskettu')),
    ADD CONSTRAINT sanktio_laskennan_syote_check
        CHECK (
            CASE
                WHEN maaritystapa = 'laskettu'
                    THEN laskennan_syote IS NOT NULL AND jsonb_typeof(laskennan_syote) = 'object'
                ELSE laskennan_syote IS NULL
                END
            );

-- MHU26: HARJA-2616 sohjo-ojan ja lumivallin madallus (sanktiotyyppi 22): tiekm × 200 €.
-- Muu töiden tekemättä jättäminen (sanktiotyyppi 23) jää manuaaliseksi, eikä sillä ole summamääritysriviä.
UPDATE sanktio_profiili_rivi_summamaaritys sprsm
SET maaritystapa       = 'laskettu',
    summa_euroina      = NULL,
    ohjeteksti         = NULL,
    laskentatapa       = 'tiekm_yksikkohinta',
    laskentaparametrit = '{"syoteavain": "tiekm", "yksikko": "tiekm", "desimaalit": 2, "yksikkohinta": 200.0}'::JSONB,
    muokkaaja          = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
    muokattu           = CURRENT_TIMESTAMP
FROM sanktio_profiili_rivi spr,
     sanktio_profiili sp,
     sanktio_laji sl,
     sanktiotyyppi st
WHERE sprsm.sanktio_profiili_rivi_id = spr.id
  AND spr.sanktio_profiili_id = sp.id
  AND spr.sanktio_laji_id = sl.id
  AND spr.sanktiotyyppi_id = st.id
  AND sp.nimi = 'teiden-hoito-mhu2026'
  AND sl.koodi = 'tyon_tekematta_jattaminen'
  AND st.koodi = 22;

-- MHU26: HARJA-2621 laskutus ilman laskutuskelpoisuutta (sanktiotyyppi 0): 20 % laskutuskelvottomana laskutetusta osuudesta.
UPDATE sanktio_profiili_rivi_summamaaritys sprsm
SET maaritystapa       = 'laskettu',
    summa_euroina      = NULL,
    ohjeteksti         = NULL,
    laskentatapa       = 'prosenttiosuus_syotteesta',
    laskentaparametrit = '{"syoteavain": "laskutuskelvottomana_laskutettu_osuus", "yksikko": "€", "desimaalit": 2, "prosentti": 20}'::JSONB,
    muokkaaja          = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
    muokattu           = CURRENT_TIMESTAMP
FROM sanktio_profiili_rivi spr,
     sanktio_profiili sp,
     sanktio_laji sl,
     sanktiotyyppi st
WHERE sprsm.sanktio_profiili_rivi_id = spr.id
  AND spr.sanktio_profiili_id = sp.id
  AND spr.sanktio_laji_id = sl.id
  AND spr.sanktiotyyppi_id = st.id
  AND sp.nimi = 'teiden-hoito-mhu2026'
  AND sl.koodi = 'laskutus_ilman_laskutuskelpoisuutta'
  AND st.koodi = 0;
