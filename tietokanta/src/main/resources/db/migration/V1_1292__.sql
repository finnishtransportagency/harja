-- Korjataan sanktio_laji-taulun ryhmittely ja järjestys.
WITH jarjestysarvot (koodi, uusi_jarjestys) AS (
        VALUES
                ('muistutus', 1),
                ('A', 2),
                ('B', 3),
                ('C', 4),
                ('tyon_tekematta_jattaminen', 20),
                ('asiakirjamerkintojen_paikkansa_pitamattomyys', 21),
                ('muu_sopimuksen_vastainen_toiminta', 22),
                ('vaihtosanktio', 30),
                ('vastuuhenkilon_vaihto', 30),
                ('testikeskiarvo-sanktio', 31),
                ('tenttikeskiarvo-sanktio', 32),
                ('vastuuhenkilon_tenttipistemaara_alentuminen', 32),
                ('laskutus_yli_laskutusrajan', 40),
                ('laskutus_ilman_laskutuskelpoisuutta', 41),
                ('pohjavesisuolan_ylitys', 50),
                ('talvisuolan_ylitys', 51),
                ('talvisuolan_kokonaiskayton_ylitys', 52),
                ('yllapidon_sakko', 60),
                ('yllapidon_muistutus', 61),
                ('arvonvahennyssanktio', 77)
),
integraatio AS (
        SELECT id
          FROM kayttaja
         WHERE kayttajanimi = 'Integraatio'
)
UPDATE sanktio_laji sl
   SET jarjestys = ja.uusi_jarjestys,
           muokkaaja = i.id,
           muokattu = CURRENT_TIMESTAMP
  FROM jarjestysarvot ja
           CROSS JOIN integraatio i
 WHERE sl.koodi = ja.koodi
   AND sl.jarjestys IS DISTINCT FROM ja.uusi_jarjestys;

-- Puuttuva koodi tarkoittaisi, että järjestys jäisi osittain korjaamatta.
DO $$
DECLARE
    puuttuvat TEXT;
BEGIN
    SELECT string_agg(odotettu.koodi, ', ' ORDER BY odotettu.koodi)
      INTO puuttuvat
      FROM (VALUES
                ('muistutus'), ('A'), ('B'), ('C'),
                ('tyon_tekematta_jattaminen'),
                ('asiakirjamerkintojen_paikkansa_pitamattomyys'),
                ('muu_sopimuksen_vastainen_toiminta'),
                ('vaihtosanktio'), ('vastuuhenkilon_vaihto'),
                ('testikeskiarvo-sanktio'), ('tenttikeskiarvo-sanktio'),
                ('vastuuhenkilon_tenttipistemaara_alentuminen'),
                ('laskutus_yli_laskutusrajan'),
                ('laskutus_ilman_laskutuskelpoisuutta'),
                ('pohjavesisuolan_ylitys'), ('talvisuolan_ylitys'),
                ('talvisuolan_kokonaiskayton_ylitys'),
                ('yllapidon_sakko'), ('yllapidon_muistutus'),
                ('arvonvahennyssanktio')) AS odotettu(koodi)
     WHERE NOT EXISTS (SELECT 1 FROM sanktio_laji sl WHERE sl.koodi = odotettu.koodi);

    IF puuttuvat IS NOT NULL THEN
        RAISE EXCEPTION 'Sanktioiden järjestyksestä puuttuvat lajit: %', puuttuvat;
    END IF;
END;
$$;

-- MHU19-25 C-ryhmän järjestys on profiilirivikohtainen.
WITH profiilit (nimi) AS (
                VALUES ('teiden-hoito-legacy'),
                                         ('teiden-hoito-2021-ja-uudemmat'),
                                         ('teiden-hoito-mhu2025')
),
kontekstit (soveltuvuuskonteksti) AS (
                VALUES ('urakka'), ('laatupoikkeama')
),
jarjestysarvot (sanktiotyyppi_koodi, uusi_jarjestys) AS (
                VALUES (8, 1), (9, 2), (10, 3), (12, 4), (11, 5)
),
integraatio AS (
        SELECT id
                        FROM kayttaja
                 WHERE kayttajanimi = 'Integraatio'
),
kohderivit AS (
                SELECT spr.id,
                                         ja.uusi_jarjestys
                        FROM sanktio_profiili_rivi spr
                                         JOIN sanktio_profiili sp
                                                 ON sp.id = spr.sanktio_profiili_id
                                         JOIN sanktio_laji sl
                                                 ON sl.id = spr.sanktio_laji_id
                                                AND sl.koodi = 'C'
                                         JOIN sanktiotyyppi st
                                                 ON st.id = spr.sanktiotyyppi_id
                                         JOIN kontekstit k
                                                 ON k.soveltuvuuskonteksti = spr.soveltuvuuskonteksti
                                         JOIN profiilit p
                                                 ON p.nimi = sp.nimi
                                         JOIN jarjestysarvot ja
                                                 ON ja.sanktiotyyppi_koodi = st.koodi
)
UPDATE sanktio_profiili_rivi spr
         SET jarjestys = kohderivit.uusi_jarjestys,
                         muokkaaja = i.id,
                         muokattu = CURRENT_TIMESTAMP
        FROM kohderivit
                         CROSS JOIN integraatio i
 WHERE spr.id = kohderivit.id
         AND spr.jarjestys IS DISTINCT FROM kohderivit.uusi_jarjestys;
