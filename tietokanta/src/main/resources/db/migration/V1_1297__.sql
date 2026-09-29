-- Muutetaan suunnitteluyksikkö tehtävälle Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali).
-- Toteuman yksikkö on tilan säästämiseksi tonni. Toteumat raportoidaan myös liuostonnina.
UPDATE tehtava
SET suunnitteluyksikko = 'liuostonnia',
    muokattu = current_timestamp,
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE tehtavaryhma = (select id from tehtavaryhma where yksiloiva_tunniste = 'cbb5f9c5-7a06-4cad-bce1-dbcf067d2fa1');
