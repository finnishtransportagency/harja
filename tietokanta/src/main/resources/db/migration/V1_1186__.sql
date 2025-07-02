-- Lisätään uusi liukkauden torjuntaan liittyvä tehtäväryhmä Talvihoito-toimenpiteen alle
INSERT INTO tehtavaryhma (nimi, jarjestys, nakyva, poistettu, luotu, luoja, yksiloiva_tunniste, tehtavaryhmaotsikko_id,
                          voimassaolo_alkuvuosi, toimenpide_id)
VALUES ('B4 - Ennalta arvaamattomien kuljetusten avustaminen hiekoituksella', 25, false, false, current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'),
        '3a5cb840-11a7-438f-bdae-a87da64bf98a', 1, 2025, (select id from toimenpide where koodi = '23104'));

-- Lisätään tehtäväryhmään tehtävä
-- Olemassa oleva vai uusi?
