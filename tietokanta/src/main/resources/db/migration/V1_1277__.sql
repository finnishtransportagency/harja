-- Korjataan sanktio_laji -taulun jarjestysarvot:
--   C-ryhma, vastuuhenkilo, laskutus, suola, yllapito -> omat ryhmansa
--   arvonvahenyssanktio siirretaan erikseen myohemmin

WITH integraatio AS (
    SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'
)
UPDATE sanktio_laji
SET jarjestys = CASE koodi
    -- Pääryhmät (1-4)
    WHEN 'muistutus'             THEN  1
    WHEN 'A'                     THEN  2
    WHEN 'B'                     THEN  3
    WHEN 'C'                     THEN  4

    -- C-ryhmän sanktiot (20-22)
    WHEN 'tyon_tekematta_jattaminen'          THEN 20
    WHEN 'asiakirjamerkintojen_paikkansa_pitamattomyys' THEN 21
    WHEN 'muu_sopimuksen_vastainen_toiminta'  THEN 22

    -- Vastuuhenkilösanktiot (30-34)
    WHEN 'tenttikeskiarvo-sanktio'                THEN 30
    WHEN 'testikeskiarvo-sanktio'                 THEN 31
    WHEN 'vaihtosanktio'                          THEN 32
    WHEN 'vastuuhenkilon_tenttipistemaara_alentuminen' THEN 33
    WHEN 'vastuuhenkilon_vaihto'                  THEN 34

    -- Laskutussanktiot (40-41)
    WHEN 'laskutus_yli_laskutusrajan'              THEN 40
    WHEN 'laskutus_ilman_laskutuskelpoisuutta'     THEN 41

    -- Suolasanktiot (50-52)
    WHEN 'pohjavesisuolan_ylitys'             THEN 50
    WHEN 'talvisuolan_ylitys'                 THEN 51
    WHEN 'talvisuolan_kokonaiskayton_ylitys'  THEN 52

    -- Ylläpidon sanktiot (60-61)
    WHEN 'yllapidon_sakko'       THEN 60
    WHEN 'yllapidon_muistutus'   THEN 61

    -- Arvonvähennys siirretään erikseen (77)
    WHEN 'arvonvahennyssanktio'  THEN 77

    ELSE jarjestys
  END,
  muokkaaja = (SELECT id FROM integraatio),
  muokattu  = CURRENT_TIMESTAMP;
