-- Lisätään vesiväylä-hoito-urakkatyypille tehtäväryhmäotsikot, tehtäväryhmät ja tehtävät
DO
$$
    DECLARE

        toimenpideid           INTEGER; -- tehtävän emo
        kayttajaid             INTEGER; -- luoja
        tehtavaryhmaid1        INTEGER;
        tehtavaryhmaid2        INTEGER;
        tehtavaryhmaid3        INTEGER;
        tehtavaryhmaid4        INTEGER;
        tehtavaryhmaid5        INTEGER;
        tehtavaryhmaid6        INTEGER;
        tehtavaryhmaid7        INTEGER;
        tehtavaryhmaid8        INTEGER;
        tehtavaryhmaid9        INTEGER;
        tehtavaryhmaid10       INTEGER;
        tehtavaryhmaid11       INTEGER;
        tehtavaryhmaid12       INTEGER;
        tehtavaryhmaid13       INTEGER;
        tehtavaryhmaotsikkoid1 INTEGER; -- tehtäväryhmäotsikon id, jotka luodaan myös
        tehtavaryhmaotsikkoid2 INTEGER;
        tehtavaryhmaotsikkoid3 INTEGER;
        tehtavaryhmaotsikkoid4 INTEGER;
        tehtavaryhmaotsikkoid5 INTEGER;
        tehtavaryhmaotsikkoid6 INTEGER;


    BEGIN
        RAISE NOTICE 'Lisätään tehtäväryhmiä ja tehtäviä Vesiväylä-hoito-urakkatyypille';
        kayttajaid := (select id from kayttaja where kayttajanimi = 'Integraatio');
        toimenpideid := (select id from toimenpide where nimi = 'Hoito, Vesiväylät');
        -- Oletus tässä vaiheessa

        -- Luodaan uusi tehtäväryhmäotsikko
        INSERT INTO tehtavaryhmaotsikko (otsikko, urakkatyyppi, luotu, luoja)
        VALUES ('VUOSITARKASTUKSET', 'vesivayla-hoito', current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaotsikkoid1;
        -- Jonka alle luodaan uusi tehtäväryhmä
        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Kelluvien turvalaitteiden vuositarkastustyöt ajomatkoineen', 1, tehtavaryhmaotsikkoid1, toimenpideid,
                current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid1;

        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Poijujen sijainnin ja asennon tarkastukset ml. vedenpäällisen osan silmämääräinen tarkastaminen ja heijastinkalvojen tarkastus ja tarvittaessa uusiminen',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 1, '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2026, NULL, true, false, NULL),

               ('Suurviittojen (ø > 225 mm) sijainnin ja asennon tarkastukset ml. vedenpäällisen osan silmämääräinen tarkastaminen ja heijastinkalvojen tarkastus ja tarvittaessa uusiminen',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 2, '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2026, NULL, true, false, NULL),

               ('Viittojen (ø ≤ 225 mm) sijainnin ja asennon tarkastukset ml. vedenpäällisen osan silmämääräinen tarkastaminen ja heijastinkalvojen tarkastus ja tarvittaessa uusiminen',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 3, '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2026, NULL, true, false, NULL),

               ('Poijun tai suurviitan siirto', toimenpideid, current_timestamp, kayttajaid, 'kpl', 4,
                '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2026, NULL, true, false, NULL),

               ('Suurviitan siirto', toimenpideid, current_timestamp, kayttajaid, 'kpl', 5, '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2026, NULL, true, true, NULL),

               ('Viitan siirto (tavoitehinta ehdollinen)', toimenpideid, current_timestamp, kayttajaid, 'kpl', 6,
                '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2026, NULL, true, true, 10),

               ('Viitan siirto', toimenpideid, current_timestamp, kayttajaid, 'kpl', 6, '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2026, NULL, true, true, NULL),

               ('Viitan asennus- ja vaihtotyö', toimenpideid, current_timestamp, kayttajaid, 'kpl', 8,
                '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2026, NULL, true, true, NULL);


        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Kiinteiden turvalaitteiden vuositarkastustyöt ajomatkoineen', 2, tehtavaryhmaotsikkoid1, toimenpideid,
                current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid2;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Kiinteiden valaistujen turvalaitteiden vuositarkastustyöt', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 9,
                '{kokonaishintainen}', tehtavaryhmaid2, false, true, 2026, NULL, true, false, NULL),
               ('Kiinteiden valaisemattomien turvalaitteiden vuositarkastustyöt', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 10,
                '{kokonaishintainen}', tehtavaryhmaid2, false, true, 2026, NULL, true, false, NULL);


        -- HUOLTO
        INSERT INTO tehtavaryhmaotsikko (otsikko, urakkatyyppi, luotu, luoja)
        VALUES ('HUOLTO', 'vesivayla-hoito', current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaotsikkoid2;
        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Kelluvien turvalaitteiden huoltotyöt ajomatkoineen', 3, tehtavaryhmaotsikkoid2, toimenpideid,
                current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid3;

        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Poijujen ja suurviittojen ankkuroinnin tarkastus', toimenpideid, current_timestamp, kayttajaid, 'kpl',
                9,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2026, NULL, false, false, NULL),
               ('Esijännitettyjen suurviittojen ankkuroinnin tarkastus', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 10,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2026, NULL, false, false, NULL),

               ('Poijujen ja suurviittojen vaihtotyöt', toimenpideid, current_timestamp, kayttajaid, 'kpl', 11,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2026, NULL, true, true, NULL),
               ('Suurviittojen vaihtotyöt', toimenpideid, current_timestamp, kayttajaid, 'kpl', 12,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2026, NULL, true, true, NULL),

               ('Poijujen kunnostaminen maalauksineen sis. kuljetuksen', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 13,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2026, NULL, true, true, NULL),
               ('Poijujen ja suurviittojen ankkuroinnin korjaus- ja vaihtotyöt sisältäen materiaalit ilman painoa',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 14,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2026, NULL, true, true, NULL),

               ('Suurviittojen ankkuroinnin korjaus- ja vaihtotyöt sisältäen materiaalit ilman painoa', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 15,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2026, NULL, true, true, NULL),
               ('Poijujen ja suurviittojen ankkuroinnin painon vaihtotyö', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 16,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2026, NULL, true, true, NULL),

               ('Suurviittojen ankkuroinnin painojen vaihtotyö', toimenpideid, current_timestamp, kayttajaid, 'kpl', 17,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2026, NULL, true, true, NULL),
               ('Poijujen (kesäpoiju) poisto talveksi ja asennus keväällä', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 18,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2026, NULL, true, true, NULL),
               ('Poijujen ja suurviittojen (kesäpoiju) poisto talveksi ja asennus keväällä', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 19,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2026, NULL, true, true, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Kiinteiden turvalaitteiden huoltotyöt ajomatkoineen', 4, tehtavaryhmaotsikkoid2, toimenpideid,
                current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid4;

        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Rakenteiden tarkastus, huolto ja korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 20,
                '{kokonaishintainen}', tehtavaryhmaid4, false, true, 2026, NULL, true, false, NULL),
               ('Näkemäalueiden raivaukset, urakkavuodet 1.-2.', toimenpideid, current_timestamp, kayttajaid, 'm2', 21,
                '{kokonaishintainen}', tehtavaryhmaid4, false, true, 2026, NULL, true, true, NULL),
               ('Näkemäalueiden raivaukset, urakkavuodet 3. - 5.', toimenpideid, current_timestamp, kayttajaid, NULL,
                22,
                '{kokonaishintainen}', tehtavaryhmaid4, false, true, 2026, NULL, false, false, NULL),
               ('Kummeleiden maalaus (sis. Materiaalit)', toimenpideid, current_timestamp, kayttajaid, 'kpl', 23,
                '{kokonaishintainen}', tehtavaryhmaid4, false, true, 2026, NULL, true, false, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Vesiliikennemerkkien huoltotyöt ajomatkoineen', 5, tehtavaryhmaotsikkoid2, toimenpideid,
                current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid5;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Näkemäalueiden raivaukset', toimenpideid, current_timestamp, kayttajaid, 'm2', 24,
                '{kokonaishintainen}', tehtavaryhmaid5, false, true, 2026, NULL, true, true, NULL);


        INSERT INTO tehtavaryhmaotsikko (otsikko, urakkatyyppi, luotu, luoja)
        VALUES ('VIANKORJAUS', 'vesivayla-hoito', current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaotsikkoid3;
        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Viankorjaus ajomatkoineen', 6, tehtavaryhmaotsikkoid3, toimenpideid, current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid6;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Rikkoutuneiden, tuhoutuneiden ja kadonneiden poijujen/suurviittojen uusiminen jääpeitteisenä aikana: työ ja ankkurointitarvikkeet',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 25,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Rikkoutuneiden, tuhoutuneiden ja kadonneiden poijujen/suurviittojen uusiminen avovesikaudella: työ ja ankkurointitarvikkeet ilman painoa',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 26,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Rikkoutuneiden, tuhoutuneiden ja kadonneiden suurviittojen uusiminen avovesikaudella: työ ja ankkurointitarvikkeet',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 27,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Rikkoutuneiden, tuhoutuneiden ja kadonneiden suurviittojen uusiminen avovesikaudella: työ ja ankkurointitarvikkeet ilman painoa',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 28,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Siirtyneiden poijujen ja suurviittojen siirto jääpeitteisenä aikana: työ', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 29,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Siirtyneiden poijujen ja suurviittojen siirto avovesikaudella: työ', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 30,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Siirtyneiden suurviittojen siirto jääpeitteisenä aikana: työ', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 31,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Siirtyneiden suurviittojen siirto avovesikaudella: työ', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 32,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Vikailmoitukseen perustuva viankorjauskäynti kelluvalla turvalaitteella: valovika (tavoitehinta ehdollinen)',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 33,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, 10),
               ('Vikailmoitukseen perustuva viankorjauskäynti kelluvalla turvalaitteella: valovika', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 34,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Vikailmoitukseen perustuva viankorjauskäynti kelluvalla turvalaitteella: viitan (ø ≤ 225 mm) siirto/vaihto/asennus',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 35,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Vikailmoitukseen perustuva viankorjauskäynti kelluvalla turvalaitteella: viitan (ø ≤ 225 mm) siirto/vaihto/asennus (tavoitehinta ehdollinen)',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 36,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, 10),
               ('Vikailmoitukseen perustuva viankorjauskäynti kiinteällä turvalaitteella sisältäen tutkamajakoiden energialähteiden viat',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 37,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Vikailmoitukseen perustuva viankorjauskäynti kiinteällä turvalaitteella sisältäen tutkamajakoiden energialähteiden viat (tavoitehinta ehdollinen)',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 38,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, 10),
               ('Vikailmoitukseen perustuva viankorjauskäynti kiinteällä turvalaitteella', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 39,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, 10),
               ('Jään alle ajautuneen kelluvan turvalaitteen ylösajo', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 40,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Irronneiden viittojen noutaminen asiakasilmoitusten perusteella', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 41,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, NULL),
               ('Irronneiden viittojen noutaminen asiakasilmoitusten perusteella (tavoitehinta ehdollinen)',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 42,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2026, NULL, true, true, 10);


        INSERT INTO tehtavaryhmaotsikko (otsikko, urakkatyyppi, luotu, luoja)
        VALUES ('AVOKANAVIEN HOITO', 'vesivayla-hoito', current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaotsikkoid4;
        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Avokanavien hoito', 7, tehtavaryhmaotsikkoid4, toimenpideid, current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid7;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Avokanavien reunavalaistuksien tarkastus ja huolto', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 43,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2026, NULL, true, true, NULL),
               ('Vikailmoitukseen perustuva vikakäynti avokanavilla: valovika', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 44,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2026, NULL, true, true, 10),
               ('Avokanavien luiskien vesakonraivaus', toimenpideid, current_timestamp, kayttajaid, 'm2', 45,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2026, NULL, true, true, NULL),
               ('Avokanavien reuna-alueiden käsiniitto', toimenpideid, current_timestamp, kayttajaid, 'm2', 46,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2026, NULL, true, true, NULL),
               ('Avokanavien reunateiden kunnossapito', toimenpideid, current_timestamp, kayttajaid, 'jm', 47,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2026, NULL, true, true, NULL),
               ('Muut avokanavien ympäristön hoitoon liittyvät tehtävät', toimenpideid, current_timestamp, kayttajaid,
                NULL, 48,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2026, NULL, false, false, NULL);


        INSERT INTO tehtavaryhmaotsikko (otsikko, urakkatyyppi, luotu, luoja)
        VALUES ('MUUT ASENNUSTYÖT', 'vesivayla-hoito', current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaotsikkoid5;
        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Muut asennustyöt ajomatkoineen', 8, tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp,
                kayttajaid)
        RETURNING id INTO tehtavaryhmaid8;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Uuden poijun asennustyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 49,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2026, NULL, true, true, NULL),
               ('Uuden viitan asennustyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 50,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2026, NULL, true, true, NULL),
               ('Uuden suurviitan asennustyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 51,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2026, NULL, true, true, NULL),
               ('Poijun poistotyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 52,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2026, NULL, true, true, NULL),
               ('Viitan poistotyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 53,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2026, NULL, true, true, NULL),
               ('Suurviitan poistotyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 54,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2026, NULL, true, true, NULL);


        INSERT INTO tehtavaryhmaotsikko (otsikko, urakkatyyppi, luotu, luoja)
        VALUES ('MATERIAALIT', 'vesivayla-hoito', current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaotsikkoid6;
        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Materiaalit', 9, tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid9;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Uudet viitat mukaan lukien ankkurointitarvikkeet esim. kettingit, sakkelit ja painot', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 55,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2026, NULL, true, true, NULL),
               ('Jääpoiju, materiaali (PE)', toimenpideid, current_timestamp, kayttajaid, 'kpl', 56,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2026, NULL, true, true, NULL),
               ('Poijuviitta, materiaali (PE)', toimenpideid, current_timestamp, kayttajaid, 'kpl', 57,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2026, NULL, true, true, NULL),
               ('Jääpoiju, materiaali (PE), 5 v. paristomalli', toimenpideid, current_timestamp, kayttajaid, 'kpl', 58,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2026, NULL, true, true, NULL),
               ('Suurviitta (ø = 500 mm), materiaali', toimenpideid, current_timestamp, kayttajaid, 'kpl', 59,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2026, NULL, true, true, NULL),
               ('Suurviitta (ø = 400 mm), materiaali', toimenpideid, current_timestamp, kayttajaid, 'kpl', 60,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2026, NULL, true, true, NULL),
               ('6t paino', toimenpideid, current_timestamp, kayttajaid, 'kpl', 61,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2026, NULL, true, true, NULL),
               ('10t paino', toimenpideid, current_timestamp, kayttajaid, 'kpl', 62,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2026, NULL, true, true, NULL),
               ('20t lohkoankkuri', toimenpideid, current_timestamp, kayttajaid, 'kpl', 63,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2026, NULL, true, true, NULL),
               ('1200 kg paino', toimenpideid, current_timestamp, kayttajaid, 'kpl', 64,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2026, NULL, true, true, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Aurinkoenergialaitteet asennustarvikkeineen esim. telineet, kaapelit ja kaapelikiinnikkeet', 10,
                tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid10;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Aurinkopaneeli', toimenpideid, current_timestamp, kayttajaid, 'kpl', 65,
                '{kokonaishintainen}', tehtavaryhmaid10, false, true, 2026, NULL, true, true, NULL),
               ('NiCd-akku 50Ah', toimenpideid, current_timestamp, kayttajaid, 'kpl', 66,
                '{kokonaishintainen}', tehtavaryhmaid10, false, true, 2026, NULL, true, true, NULL),
               ('NiCd-akku 100Ah', toimenpideid, current_timestamp, kayttajaid, 'kpl', 67,
                '{kokonaishintainen}', tehtavaryhmaid10, false, true, 2026, NULL, true, true, NULL),
               ('NiCd-akku 150Ah', toimenpideid, current_timestamp, kayttajaid, 'kpl', 68,
                '{kokonaishintainen}', tehtavaryhmaid10, false, true, 2026, NULL, true, true, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Valolaitteet asennustarvikkeineen', 11, tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp,
                kayttajaid)
        RETURNING id INTO tehtavaryhmaid11;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('LED 155', toimenpideid, current_timestamp, kayttajaid, 'kpl', 69,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('LED 155 tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 70,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('LED 155, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 71,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('LED 155 tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 72,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('LED 160 tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 73,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('LED 160 tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 74,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('LO 200 M', toimenpideid, current_timestamp, kayttajaid, 'kpl', 75,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('LO 200 M tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 76,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('LO 200 M, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 77,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('LO 200 M tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 78,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('MPV- led', toimenpideid, current_timestamp, kayttajaid, 'kpl', 79,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('MPV- led, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 80,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('MPV 2- led tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 81,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('MPV 2- led tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 82,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('MVP 2- led, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 83,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('VP-led', toimenpideid, current_timestamp, kayttajaid, 'kpl', 84,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('VP- led, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 85,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('VP 2- led tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 86,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('VP 2- led', toimenpideid, current_timestamp, kayttajaid, 'kpl', 87,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('VP 2- led tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 88,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('ODSL 200', toimenpideid, current_timestamp, kayttajaid, 'kpl', 89,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('ODSL 200 tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 90,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('ODSL 200, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 91,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('ODSL 200 tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 92,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL),
               ('UPS 12 verkkovirtamuuntaja', toimenpideid, current_timestamp, kayttajaid, 'kpl', 93,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2026, NULL, true, true, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Kelluvien turvalaitteiden paristot', 12, tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp,
                kayttajaid)
        RETURNING id INTO tehtavaryhmaid12;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Isot pyöreät kelluviin turvalaitteisiin', toimenpideid, current_timestamp, kayttajaid, 'kpl', 94,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2026, NULL, true, true, 10),
               ('Isot pyöreät paristot', toimenpideid, current_timestamp, kayttajaid, 'kpl', 94,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2026, NULL, true, true, NULL),
               ('Pienet pyöreät kelluviin turvalaitteisiin', toimenpideid, current_timestamp, kayttajaid, 'kpl', 94,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2026, NULL, true, true, 10),
               ('Nelikulmainen poijuparisto', toimenpideid, current_timestamp, kayttajaid, 'kpl', 94,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2026, NULL, true, true, NULL),
               ('Nelikulmainen poijuparisto (tavoitehinta ehdollinen)', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 94,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2026, NULL, true, true, 10),
               ('5 vuoden paristot', toimenpideid, current_timestamp, kayttajaid, 'kpl', 94,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2026, NULL, true, true, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Turvalaitteiden paristot', 13, tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid13;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Isot pyöreät turvalaitteisiin', toimenpideid, current_timestamp, kayttajaid, 'kpl', 95,
                '{kokonaishintainen}', tehtavaryhmaid13, false, true, 2026, NULL, true, true, 10),
               ('Pienet pyöreät turvalaitteisiin', toimenpideid, current_timestamp, kayttajaid, 'kpl', 96,
                '{kokonaishintainen}', tehtavaryhmaid13, false, true, 2026, NULL, true, true, 10),
               ('Nelikulmainen poijuparisto turvalaitteisiin', toimenpideid, current_timestamp, kayttajaid, 'kpl', 97,
                '{kokonaishintainen}', tehtavaryhmaid13, false, true, 2026, NULL, true, true, 10);

        RAISE NOTICE 'Tehtäväryhmäotsikot , tehtäväryhmät ja tehtävät lisätty onnistuneesti.';
    END
$$;
