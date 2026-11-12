-- Tiemerkintä 
-- Oulun tiemerkinnän palvelusopimus 
-- Kustannusten kirjaus 
-- Kustannusten yhteenveto 


-----------------------------
-- Tiemerkintöjen korjaus
INSERT INTO tiemerkinta_korjauskustannus (urakka,luoja,luotu,muokattu,muokkaaja,kustannusvuosi,kustannus,pk1,pk2,pk3) 
VALUES
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    2017,
    150000,
    10,
    10,
    80
),
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    2018,
    745661,
    30,
    35,
    35
),
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-07 11:32:45.497868',
    '2025-06-07 11:32:45.495',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    2019,
    250600,
    15,
    80,
    5
),
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-07 11:32:45.503045',
    '2025-06-07 11:32:45.501',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    2020,
    755600,
    4,
    76,
    20
),
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-07 11:32:45.507603',
    '2025-06-07 11:32:45.505',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    2021,
    2135045,
    1,
    89,
    10
),
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-07 11:32:45.512356',
    '2025-06-07 11:32:45.51',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    2022,
    645050,
    85,
    10,
    5
),
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-07 11:32:45.517302',
    '2025-06-07 11:32:45.515',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    2023,
    1860020,
    45,
    10,
    45
),
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-07 11:32:45.521879',
    '2025-06-07 11:32:45.52',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    2024,
    915025,
    25,
    50,
    25
),
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-07 11:32:45.526504',
    '2025-06-07 11:32:45.524',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    2025,
    1243000,
    35,
    20,
    45
);


-----------------------------
-- Uusien päällysteiden tiemerkinnät
INSERT INTO tiemerkinta_yllapitokohteen_kustannus (yllapitokohde,luoja,luotu,muokattu,muokkaaja,linjamerkinnat,pienmerkinnat,jyrsinnat) 
VALUES (
    (SELECT id FROM yllapitokohde WHERE nimi LIKE '%Ouluntie 2%')::INT,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-08 22:16:02.354937',
    '2025-06-08 22:16:02.354',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    345000.00,
    15000.00,
    850600.00
);


-----------------------------
-- Sakot ja bonukset
INSERT INTO laatupoikkeama (kohde,tekija,kasittelytapa,muu_kasittelytapa,paatos,perustelu,tarkastuspiste,
luoja,luotu,muokkaaja,muokattu,poistettu,aika,kasittelyaika,selvitys_pyydetty,selvitys_annettu,urakka,kuvaus,tr_numero,tr_alkuosa,tr_loppuosa,tr_loppuetaisyys,sijainti,tr_alkuetaisyys,ulkoinen_id,"lahde",yllapitokohde,"sisaltaa-poikkeamaraportin?") 
VALUES
(
    NULL,'tilaaja','muu','Tiemerkintä','sanktio','Sakko 2025',NULL,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-08 22:19:54.041434',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-08 22:19:54.041434',
    false,
    '2025-06-08 22:19:36',
    '2025-06-08 00:00:00',
    false,false,
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'harja-ui',NULL,NULL
),
(
    NULL,'tilaaja','muu','Tiemerkintä','sanktio','Bonus 2025',NULL,
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-08 22:20:05.003331',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    '2025-06-08 22:20:05.003331',
    false,
    '2025-06-08 00:00:00',
    '2025-06-08 00:00:00',
    false,false,
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'harja-ui',NULL,NULL
);

INSERT INTO sanktio (maara,perintapvm, maarattypvm, indeksi,laatupoikkeama,toimenpideinstanssi,tyyppi,suorasanktio,muokattu,muokkaaja,luotu,luoja,ulkoinen_id,vakiofraasi,sakkoryhma,poistettu)
VALUES
(
    4500,'2025-06-08', '2025-06-08',NULL,
    (SELECT id FROM laatupoikkeama WHERE perustelu LIKE '%Sakko 2025%')::INT,
    (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Tiemerkinnän TP')::INT,
    (SELECT id FROM sanktiotyyppi WHERE nimi = 'Ylläpidon sakko')::INT,
    true,NULL,NULL,
    '2025-06-08 22:19:54.041434',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    NULL,NULL,'yllapidon_sakko',false
),
(
    -450,'2025-06-08', '2025-06-08',NULL,
    (SELECT id FROM laatupoikkeama WHERE perustelu LIKE '%Bonus 2025%')::INT,
    (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Tiemerkinnän TP')::INT,
    (SELECT id FROM sanktiotyyppi WHERE nimi = 'Ylläpidon bonus')::INT,
    true,NULL,NULL,
    '2025-06-08 22:20:05.003331',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    NULL,NULL,'yllapidon_bonus',false
);


-----------------------------
-- Muut kustannukset 
INSERT INTO yllapito_muu_toteuma (urakka,sopimus,selite,pvm,hinta,yllapitoluokka,laskentakohde,muokattu,muokkaaja,luotu,luoja,poistettu,tyyppi) 
VALUES
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM sopimus WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2017-%')::INT,
    'Muu PK1','2025-06-01',540000,8,NULL,NULL,NULL,
    '2025-06-08 22:36:37.194469',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    false,'muu'
),
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM sopimus WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2017-%')::INT,
    'Arvonmuutos','2025-06-01',400000,10,NULL,NULL,NULL,'2025-06-08 22:36:47.935124',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    false,'arvonmuutos'
),
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM sopimus WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2017-%')::INT,
    'Indeksi','2025-06-01',100000,NULL,NULL,NULL,NULL,
    '2025-06-08 22:37:01.259497',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    false,'indeksi'
),
(
    (SELECT id FROM urakka WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimus 2017%')::INT,
    (SELECT id FROM sopimus WHERE nimi LIKE 'Oulun tiemerkinnän palvelusopimuksen pääsopimus 2017-%')::INT,
    'Lisätyötä','2025-06-01',520000,9,NULL,NULL,NULL,
    '2025-06-08 22:37:13.116829',
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,
    false,'lisatyo'
);
