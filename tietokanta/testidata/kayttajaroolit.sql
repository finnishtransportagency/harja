-- Erillisoikeudet urakoihin
INSERT INTO kayttajan_lisaoikeudet_urakkaan (kayttaja, urakka, luoja, luotu)
VALUES ((SELECT id
           FROM kayttaja
          WHERE kayttajanimi = 'carement'),
        (SELECT id
           FROM urakka
          WHERE nimi = 'Oulun alueurakka 2014-2019'),
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'),
        NOW());
