-- Replikoidaan olemassa oleva potti vanhasta tietomallista
-- Kenties päästään tekemään niiden pohjalta aikanaan myös
-- hyviä testejä, esim. että YHA:an lähtevä hyötykuorma on identtinen jne.

INSERT INTO paallystysilmoitus(
    paallystyskohde,
    takuupvm,
    tila,
    paatos_tekninen_osa,
    poistettu,
    muokkaaja,
    muokattu,
    luoja,
    luotu)
VALUES
((SELECT id FROM yllapitokohde WHERE nimi = 'Tärkeä kohde mt20'),
 '2024-12-31',
 'aloitettu' ::paallystystila,
 NULL,
 FALSE,
 NULL,
 NULL,
 (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'),
 NOW());