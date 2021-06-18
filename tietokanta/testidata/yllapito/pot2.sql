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
    INSERT INTO public.pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id) VALUES (kohdeosaid_kaista11, 22, massa1_id, 3, 100.205239647471, 8283, 830, true, null, paallystysilmoituksen_id);
    INSERT INTO public.pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id) VALUES (kohdeosaid_kaista21, 23, massa2_id, 3, 100, 8283, 828.3, false, null, paallystysilmoituksen_id);

    INSERT INTO public.pot2_alusta (tr_numero, tr_alkuetaisyys, tr_alkuosa, tr_loppuetaisyys, tr_loppuosa, tr_ajorata, tr_kaista, toimenpide, pot2_id, poistettu, verkon_tyyppi, verkon_tarkoitus, verkon_sijainti, lisatty_paksuus, massamaara, murske, kasittelysyvyys, leveys, pinta_ala, kokonaismassamaara, massa, sideaine, sideainepitoisuus, sideaine2)
    VALUES (20, 1066, 1, 3827, 1, 1, 11, murskeen_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, 10, null, 1, null, null, null, null, null, null, null, null),
           (20, 1066, 1, 2000, 1, 1, 12, verkon_alusta_toimenpide_id, paallystysilmoituksen_id, false, 1, 1, 1, null, null, null, null, null, null, null, null, null, null, null),
           (20, 2000, 1, 2050, 1, 1, 12, ab_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, 34, null, null, null, 12, 23, massa1_id, null, null, null),
           (20, 2050, 1, 2100, 1, 1, 12, tas_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, 55, null, null, null, null, 12, massa1_id, null, null, null),                                                                            (20, 2100, 1, 2150, 1, 1, 12, task_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
           (20, 2150, 1, 2200, 1, 1, 12, tjyr_alusta_toimenpide_id, paallystysilmoituksen_id, false, null, null, null, null, null, null, null, null, null, null, null, null, null, null);


END;
$$ LANGUAGE plpgsql;