-- Lisätään Oulun MHU 2019-2024 urakalle eksoottisempi rahavaraus, että voidaan testata niiden huomioimista laskutusyhteenvedolla
INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, luotu, luoja)
VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'),
        (select id from rahavaraus where nimi = 'Varalaskupaikat'), current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'));
