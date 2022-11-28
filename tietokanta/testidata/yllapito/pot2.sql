-- Replikoidaan olemassa oleva potti vanhasta tietomallista
-- Kenties päästään tekemään niiden pohjalta aikanaan myös
-- hyviä testejä, esim. että YHA:an lähtevä hyötykuorma on identtinen jne.
DO $$
DECLARE
    urakkaid INTEGER;
    kohdeid INTEGER;
    kohdeosaid_kaista11 INTEGER;
    kohdeosaid_kaista21 INTEGER;
    massa1_id INTEGER;
    massa2_id INTEGER;
    paallystysilmoituksen_id INTEGER;
    murskeen_toimenpide_id INTEGER;
    verkon_alusta_toimenpide_id INTEGER;
    ab_alusta_toimenpide_id INTEGER;
    tas_alusta_toimenpide_id INTEGER;
    task_alusta_toimenpide_id INTEGER;
    tjyr_alusta_toimenpide_id INTEGER;
    murske1_id INTEGER;

    paikkauspot_urakkaid INTEGER;
    paikkauspot_massaid INTEGER;
    paikkauspot_murskeid INTEGER;

BEGIN
    urakkaid = (SELECT id FROM urakka where nimi = 'Utajärven päällystysurakka');
    kohdeid = (SELECT id FROM yllapitokohde WHERE nimi = 'Tärkeä kohde mt20');
    kohdeosaid_kaista11 = (SELECT id FROM yllapitokohdeosa WHERE nimi = 'Tärkeä kohdeosa kaista 11');
    kohdeosaid_kaista21 = (SELECT id FROM yllapitokohdeosa WHERE nimi = 'Tärkeä kohdeosa kaista 12');
    massa1_id = (SELECT id from pot2_mk_urakan_massa WHERE dop_nro = '1234567' and urakka_id = urakkaid);
    massa2_id = (SELECT id from pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' and urakka_id = urakkaid);
    murskeen_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Murske');
    verkon_alusta_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Verkko');
    ab_alusta_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Asfalttibetoni');
    tas_alusta_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Massatasaus');
    task_alusta_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Kuumennustasaus');
    tjyr_alusta_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Tasausjyrsintä');
    murske1_id = (SELECT id from pot2_mk_urakan_murske WHERE dop_nro = '1234567-dop' and urakka_id = urakkaid);

    -- Luodaan myös ns. paikkaus POT testidataan, helpottaa asioiden testaamista
    paikkauspot_urakkaid = (SELECT id FROM urakka where nimi = 'Utajärven päällystysurakka');
    paikkauspot_massaid = (SELECT id from pot2_mk_urakan_massa WHERE dop_nro = '764567-dop' and urakka_id = paikkauspot_urakkaid);
    paikkauspot_murskeid = (SELECT id from pot2_mk_urakan_murske WHERE dop_nro = '3524534-dop' and urakka_id = paikkauspot_urakkaid);


INSERT INTO paallystysilmoitus(
    paallystyskohde,
    takuupvm,
    tila,
    paatos_tekninen_osa,
    lisatiedot,
    poistettu,
    muokkaaja,
    muokattu,
    luoja,
    luotu,
    versio)
VALUES
(kohdeid,
 '2024-12-31',
 'aloitettu' ::paallystystila,
 NULL,
 'Jouduttiin tekemään alustatöitä hieman suunniteltua enemmän joten meni pari päivää pitkäksi.',
 FALSE,
 NULL,
 NULL,
 (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'),
 NOW(),
 2);

    paallystysilmoituksen_id = (SELECT id FROM paallystysilmoitus WHERE versio = 2 and paallystyskohde = kohdeid);

-- kulutuskerros (= järj.nro 1 DEFAULT)
    INSERT INTO pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id) VALUES (kohdeosaid_kaista11, 22, massa1_id, 3, 100.205239647471, 8283, 830, true, null, paallystysilmoituksen_id);
    INSERT INTO pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id) VALUES (kohdeosaid_kaista21, 23, massa2_id, 3, 100, 8283, 828.3, false, null, paallystysilmoituksen_id);

    INSERT INTO pot2_alusta (tr_numero, tr_alkuetaisyys, tr_alkuosa, tr_loppuetaisyys, tr_loppuosa, tr_ajorata, tr_kaista, toimenpide, pot2_id, poistettu, verkon_tyyppi, verkon_tarkoitus, verkon_sijainti, lisatty_paksuus, massamaara, murske, kasittelysyvyys, leveys, pinta_ala, kokonaismassamaara, massa, sideaine, sideainepitoisuus, sideaine2)
    VALUES (20, 1066, 1, 3827, 1, 1, 11, murskeen_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, 10, null, 1, null, null, null, null, null, null, null, null),
           (20, 1066, 1, 2000, 1, 1, 12, verkon_alusta_toimenpide_id, paallystysilmoituksen_id, false, 1, 1, 1, null, null, null, null, null, null, null, null, null, null, null),
           (20, 2000, 1, 2050, 1, 1, 12, ab_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, 34, null, null, null, 12, 23, massa1_id, null, null, null),
           (20, 2050, 1, 2100, 1, 1, 12, tas_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, 55, null, null, null, null, 12, massa1_id, null, null, null),                                                                            (20, 2100, 1, 2150, 1, 1, 12, task_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
           (20, 2150, 1, 2200, 1, 1, 12, tjyr_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null);


    -- paikkaus-POT tiedot
    INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, poistettu, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, yllapitokohdetyotyyppi, tunnus, yhaid, yllapitoluokka, lahetysaika, keskimaarainen_vuorokausiliikenne, nykyinen_paallyste, tr_ajorata, tr_kaista, suorittava_tiemerkintaurakka, lahetetty, lahetys_onnistunut, lahetysvirhe, yllapitokohdetyyppi, vuodet, yha_kohdenumero, muokattu, muokkaaja, yha_tr_osoite, velho_lahetyksen_aika, velho_lahetyksen_tila, velho_lahetyksen_vastaus) VALUES (5, 8, '999', 'Pottilan AB-levityskohde', false, 4, 101, 1, 101, 200, 'paallystys', null, null, null, null, null, null, null, null, null, '2022-11-28 09:33:49.375000', true, null, 'paallyste', '{2022}', null, '2022-11-28 10:27:23.266990', 3, null, '2022-11-28 09:33:49.407000', 'valmis', null);

    INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, poistettu, sijainti, yhaid, tr_ajorata, tr_kaista, toimenpide, ulkoinen_id, paallystetyyppi, raekoko, tyomenetelma, massamaara, muokattu, keskimaarainen_vuorokausiliikenne, yllapitoluokka, nykyinen_paallyste) VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Pottilan AB-levityskohde'), 'Pottilan AB-levityskohde osa 1', 4, 101, 1, 101, 200, false, '0105000000010000000102000000070000006EB1DE0E038D1741DBBB6DFD627359417E8CB96B2B8D174117D9CE6761735941F7E461E1768D174188F4DBF360735941992A18D5C18D1741D712F2B56173594155302A29C68E17419E5E297F6C73594196438B6C638F174152B81EDD6C7359416C66904CE28F174159CD3A006D735941', null, 1, 11, '12', null, null, null, null, null, '2022-11-28 10:27:23.266990', null, null, null);

    INSERT INTO paikkauskohde ("luoja-id", "ulkoinen-id", nimi, poistettu, luotu, "muokkaaja-id", muokattu, "urakka-id", "yhalahetyksen-tila", tarkistettu, "tarkistaja-id", "ilmoitettu-virhe", alkupvm, loppupvm, tilattupvm, tyomenetelma, tierekisteriosoite_laajennettu, "paikkauskohteen-tila", "suunniteltu-maara", "suunniteltu-hinta", yksikko, lisatiedot, "pot?", valmistumispvm, tiemerkintapvm, "toteutunut-hinta", "tiemerkintaa-tuhoutunut?", takuuaika, "yllapitokohde-id") VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), 999, 'Pottilan AB-levityskohde', false, '2022-11-28 09:09:57.746000', 3, '2022-11-28 09:09:57.746000', 5, null, null, null, null, '2022-11-28', '2022-11-30', '2022-11-28', 1, ROW(4,101,1,101,200,0,NULL,NULL,NULL,NULL)::tr_osoite_laajennettu, 'tilattu', 199, 2000, 'jm', null, true, '2022-11-25', null, 5000, null, 3, (SELECT id FROM yllapitokohde WHERE nimi = 'Pottilan AB-levityskohde'));


    INSERT INTO paallystysilmoitus (paallystyskohde, luotu, muokattu, luoja, muokkaaja, poistettu, takuupvm, paatos_tekninen_osa, kasittelyaika_tekninen_osa, tila, id, perustelu_tekninen_osa, asiatarkastus_pvm, asiatarkastus_tarkastaja, asiatarkastus_hyvaksytty, asiatarkastus_lisatiedot, versio, lisatiedot) VALUES ((SELECT id FROM yllapitokohde WHERE nimi = 'Pottilan AB-levityskohde'), '2022-11-28 08:47:43.112868', NULL, (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'),NULL, false, '2025-11-26', 'hyvaksytty', '2022-11-28 09:28:36.000000', 'lukittu', 7, null, null, null, null, null, 2, null);

    INSERT INTO pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id, jarjestysnro, velho_lahetyksen_aika, velho_rivi_lahetyksen_tila, velho_lahetyksen_vastaus, massamenekki) VALUES ((SELECT id FROM yllapitokohdeosa WHERE nimi = 'Pottilan AB-levityskohde osa 1'), 12, 3, 2.00, 398, 34, false, null, 7, 1, '2022-11-28 09:30:06.384000', 'onnistunut', '1.2.246.578.12.1.3151958991.813429633', 85.4);

    INSERT INTO pot2_alusta (tr_numero, tr_alkuetaisyys, tr_alkuosa, tr_loppuetaisyys, tr_loppuosa, tr_ajorata, tr_kaista, toimenpide, pot2_id, poistettu, verkon_tyyppi, verkon_tarkoitus, verkon_sijainti, lisatty_paksuus, massamaara, murske, kasittelysyvyys, leveys, pinta_ala, kokonaismassamaara, massa, sideaine, sideainepitoisuus, sideaine2, velho_lahetyksen_aika, velho_rivi_lahetyksen_tila, velho_lahetyksen_vastaus) VALUES (4, 1, 101, 200, 101, 1, 11, 23, (SELECT id FROM paallystysilmoitus WHERE paallystyskohde = (SELECT id FROM yllapitokohde WHERE nimi = 'Pottilan AB-levityskohde')), false, null, null, null, 5, null, 2, null, null, null, null, null, null, null, null, null, 'ei-lahetetty', null);


END;
$$ LANGUAGE plpgsql;