INSERT INTO kayttaja_rooli (kayttaja, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'urakanvalvoja');
INSERT INTO kayttaja_rooli (kayttaja, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_pk2'), 'urakoitsijan paakayttaja');
INSERT INTO kayttaja_rooli (kayttaja, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_pk'), 'urakoitsijan paakayttaja');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_pk'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), 'urakoitsijan paakayttaja');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_pk'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 'urakoitsijan paakayttaja');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM urakka WHERE nimi='Muhoksen päällystysurakka'), 'urakanvalvoja');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_pk2'), (SELECT id FROM urakka WHERE nimi='Muhoksen päällystysurakka'), 'urakoitsijan paakayttaja');

INSERT INTO kayttaja_rooli (kayttaja, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_uuvh'), 'urakoitsijan urakan vastuuhenkilo');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_uuvh'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), 'urakoitsijan urakan vastuuhenkilo');
INSERT INTO kayttaja_urakka_rooli (kayttaja, urakka, rooli) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi='yit_uuvh'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 'urakoitsijan urakan vastuuhenkilo');



-- Erillisoikeudet urakoihin

INSERT INTO kayttajan_lisaoikeudet_urakkaan (kayttaja, urakka) VALUES ((SELECT id
                                                                        FROM kayttaja
                                                                        WHERE kayttajanimi = 'carement'),
                                                                       (SELECT id
                                                                        FROM urakka
                                                                        WHERE nimi = 'Oulun alueurakka 2014-2019'));