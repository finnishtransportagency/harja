INSERT INTO koodisto_konversio (id, nimi, kuvaus)
VALUES ('v/vtykl', 'velho/yleinen-kuntoluokka',
        'Varusteiden yhteiset ominaisuustiedot, kunto ja vauriotiedot, yleinen-kuntoluokka');

INSERT INTO koodisto_konversio_koodit (koodisto_konversio_id, lahde, tulos)
VALUES ('v/vtykl', 'kuntoluokka/kl01', 'Erittäin huono'),
       ('v/vtykl', 'kuntoluokka/kl02', 'Huono'),
       ('v/vtykl', 'kuntoluokka/kl03', 'Tyydyttävä'),
       ('v/vtykl', 'kuntoluokka/kl04', 'Hyvä'),
       ('v/vtykl', 'kuntoluokka/kl05', 'Erittäin hyvä'),
       ('v/vtykl', 'kuntoluokka/kl09', 'Ei voitu tarkastaa');
