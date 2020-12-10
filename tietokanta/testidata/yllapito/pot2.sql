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

BEGIN
    urakkaid = (SELECT id FROM urakka where nimi = 'Utajärven päällystysurakka');
    kohdeid = (SELECT id FROM yllapitokohde WHERE nimi = 'Tärkeä kohde mt20');
    kohdeosaid_kaista11 = (SELECT id FROM yllapitokohdeosa WHERE nimi = 'Tärkeä kohdeosa kaista 11');
    kohdeosaid_kaista21 = (SELECT id FROM yllapitokohdeosa WHERE nimi = 'Tärkeä kohdeosa kaista 12');
    massa1_id = (SELECT id FROM pot2_massa WHERE dop_nro = '1234567' and urakka_id = urakkaid);
    massa2_id = (SELECT id FROM pot2_massa WHERE dop_nro = '987654331-2' and urakka_id = urakkaid);


INSERT INTO paallystysilmoitus(
    paallystyskohde,
    takuupvm,
    tila,
    paatos_tekninen_osa,
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
 FALSE,
 NULL,
 NULL,
 (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'),
 NOW(),
 2);

    paallystysilmoituksen_id = (SELECT id FROM paallystysilmoitus WHERE versio = 2 and paallystyskohde = kohdeid);

INSERT INTO public.pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id) VALUES (kohdeosaid_kaista11, 22, massa1_id, 3, 333, 15000, 5000, true, null, paallystysilmoituksen_id);
INSERT INTO public.pot2_paallystekerros (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki, pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id) VALUES (kohdeosaid_kaista21, 23, massa2_id, 3, 333, 15000, 5000, false, null, paallystysilmoituksen_id);


END;
$$ LANGUAGE plpgsql;