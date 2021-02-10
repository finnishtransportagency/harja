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
    verkon_toimenpide_id INTEGER;
    murske1_id INTEGER;

BEGIN
    urakkaid = (SELECT id FROM urakka where nimi = 'Utajärven päällystysurakka');
    kohdeid = (SELECT id FROM yllapitokohde WHERE nimi = 'Tärkeä kohde mt20');
    kohdeosaid_kaista11 = (SELECT id FROM yllapitokohdeosa WHERE nimi = 'Tärkeä kohdeosa kaista 11');
    kohdeosaid_kaista21 = (SELECT id FROM yllapitokohdeosa WHERE nimi = 'Tärkeä kohdeosa kaista 12');
    massa1_id = (SELECT id from pot2_mk_urakan_massa WHERE dop_nro = '1234567' and urakka_id = urakkaid);
    massa2_id = (SELECT id from pot2_mk_urakan_massa WHERE dop_nro = '987654331-2' and urakka_id = urakkaid);
    murskeen_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Murske');
    verkon_toimenpide_id = (SELECT koodi from pot2_mk_alusta_toimenpide WHERE nimi = 'Verkko');
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
INSERT INTO public.pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id) VALUES (kohdeosaid_kaista11, 22, massa1_id, 3, 333, 15000, 5000, true, null, paallystysilmoituksen_id);
INSERT INTO public.pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id) VALUES (kohdeosaid_kaista21, 23, massa2_id, 3, 333, 15000, 5000, false, null, paallystysilmoituksen_id);

-- alempi päällystekerros (= järj.nro 2)
INSERT INTO public.pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki, pinta_ala, kokonaismassamaara, piennar, lisatieto, jarjestysnro, pot2_id) VALUES (kohdeosaid_kaista21, 23, massa2_id, 3, 333, 15000, 5000, false, null, 2, paallystysilmoituksen_id);


INSERT INTO pot2_alusta (tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, toimenpide, murske, lisatty_paksuus, massamaara, verkon_tyyppi, verkon_sijainti, verkon_tarkoitus, pot2_id)
VALUES (20, 1, 1066, 1, 3827, 1, 11, murskeen_toimenpide_id, murske1_id, 10, 100, null, null, null, paallystysilmoituksen_id),
       (20, 1, 1066, 1, 3827, 1, 12, verkon_toimenpide_id, null, 10, 100, 1, 1, 1, paallystysilmoituksen_id);


END;
$$ LANGUAGE plpgsql;