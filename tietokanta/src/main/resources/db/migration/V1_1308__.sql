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
        VALUES ('Kelluvien turvalaitteiden vuositarkastustyöt ajomatkoineen', 10, tehtavaryhmaotsikkoid1, toimenpideid,
                current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid1;

        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Poijujen sijainnin ja asennon tarkastukset ml. vedenpäällisen osan silmämääräinen tarkastaminen ja heijastinkalvojen tarkastus ja tarvittaessa uusiminen',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 10, '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2023, NULL, true, false, NULL),

               ('Suurviittojen (ø > 225 mm) sijainnin ja asennon tarkastukset ml. vedenpäällisen osan silmämääräinen tarkastaminen ja heijastinkalvojen tarkastus ja tarvittaessa uusiminen',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 20, '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2023, NULL, true, false, NULL),

               ('Viittojen (ø ≤ 225 mm) sijainnin ja asennon tarkastukset ml. vedenpäällisen osan silmämääräinen tarkastaminen ja heijastinkalvojen tarkastus ja tarvittaessa uusiminen',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 30, '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2023, NULL, true, false, NULL),

               ('Poijun tai suurviitan siirto', toimenpideid, current_timestamp, kayttajaid, 'kpl', 40,
                '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2023, NULL, true, false, NULL),

               ('Suurviitan siirto', toimenpideid, current_timestamp, kayttajaid, 'kpl', 50, '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2023, NULL, true, true, NULL),

               ('Viitan siirto (tavoitehinta ehdollinen)', toimenpideid, current_timestamp, kayttajaid, 'kpl', 60,
                '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2023, NULL, true, true, 10),

               ('Viitan siirto', toimenpideid, current_timestamp, kayttajaid, 'kpl', 60, '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2023, NULL, true, true, NULL),

               ('Viitan asennus- ja vaihtotyö', toimenpideid, current_timestamp, kayttajaid, 'kpl', 70,
                '{kokonaishintainen}',
                tehtavaryhmaid1, false, true, 2023, NULL, true, true, NULL);


        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Kiinteiden turvalaitteiden vuositarkastustyöt ajomatkoineen', 20, tehtavaryhmaotsikkoid1, toimenpideid,
                current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid2;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Kiinteiden valaistujen turvalaitteiden vuositarkastustyöt', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 80,
                '{kokonaishintainen}', tehtavaryhmaid2, false, true, 2023, NULL, true, false, NULL),
               ('Kiinteiden valaisemattomien turvalaitteiden vuositarkastustyöt', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 90,
                '{kokonaishintainen}', tehtavaryhmaid2, false, true, 2023, NULL, true, false, NULL);


        -- HUOLTO
        INSERT INTO tehtavaryhmaotsikko (otsikko, urakkatyyppi, luotu, luoja)
        VALUES ('HUOLTO', 'vesivayla-hoito', current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaotsikkoid2;
        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Kelluvien turvalaitteiden huoltotyöt ajomatkoineen', 30, tehtavaryhmaotsikkoid2, toimenpideid,
                current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid3;

        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Poijujen ja suurviittojen ankkuroinnin tarkastus', toimenpideid, current_timestamp, kayttajaid, 'kpl',
                100,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2023, NULL, false, false, NULL),
               ('Esijännitettyjen suurviittojen ankkuroinnin tarkastus', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 110,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2023, NULL, false, false, NULL),

               ('Poijujen ja suurviittojen vaihtotyöt', toimenpideid, current_timestamp, kayttajaid, 'kpl', 120,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2023, NULL, true, true, NULL),
               ('Suurviittojen vaihtotyöt', toimenpideid, current_timestamp, kayttajaid, 'kpl', 130,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2023, NULL, true, true, NULL),

               ('Poijujen kunnostaminen maalauksineen sis. kuljetuksen', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 140,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2023, NULL, true, true, NULL),
               ('Poijujen ja suurviittojen ankkuroinnin korjaus- ja vaihtotyöt sisältäen materiaalit ilman painoa',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 150,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2023, NULL, true, true, NULL),

               ('Suurviittojen ankkuroinnin korjaus- ja vaihtotyöt sisältäen materiaalit ilman painoa', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 160,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2023, NULL, true, true, NULL),
               ('Poijujen ja suurviittojen ankkuroinnin painon vaihtotyö', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 170,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2023, NULL, true, true, NULL),

               ('Suurviittojen ankkuroinnin painojen vaihtotyö', toimenpideid, current_timestamp, kayttajaid, 'kpl', 180,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2023, NULL, true, true, NULL),
               ('Poijujen (kesäpoiju) poisto talveksi ja asennus keväällä', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 190,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2023, NULL, true, true, NULL),
               ('Poijujen ja suurviittojen (kesäpoiju) poisto talveksi ja asennus keväällä', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 200,
                '{kokonaishintainen}', tehtavaryhmaid3, false, true, 2023, NULL, true, true, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Kiinteiden turvalaitteiden huoltotyöt ajomatkoineen', 40, tehtavaryhmaotsikkoid2, toimenpideid,
                current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid4;

        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Rakenteiden tarkastus, huolto ja korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 210,
                '{kokonaishintainen}', tehtavaryhmaid4, false, true, 2023, NULL, true, false, NULL),
               ('Näkemäalueiden raivaukset, urakkavuodet 1.-2.', toimenpideid, current_timestamp, kayttajaid, 'm2', 220,
                '{kokonaishintainen}', tehtavaryhmaid4, false, true, 2023, NULL, true, true, NULL),
               ('Näkemäalueiden raivaukset, urakkavuodet 3. - 5.', toimenpideid, current_timestamp, kayttajaid, NULL,
                230,
                '{kokonaishintainen}', tehtavaryhmaid4, false, true, 2023, NULL, false, false, NULL),
               ('Kummeleiden maalaus (sis. Materiaalit)', toimenpideid, current_timestamp, kayttajaid, 'kpl', 240,
                '{kokonaishintainen}', tehtavaryhmaid4, false, true, 2023, NULL, true, false, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Vesiliikennemerkkien huoltotyöt ajomatkoineen', 50, tehtavaryhmaotsikkoid2, toimenpideid,
                current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid5;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Näkemäalueiden raivaukset', toimenpideid, current_timestamp, kayttajaid, 'm2', 250,
                '{kokonaishintainen}', tehtavaryhmaid5, false, true, 2023, NULL, true, true, NULL);


        INSERT INTO tehtavaryhmaotsikko (otsikko, urakkatyyppi, luotu, luoja)
        VALUES ('VIANKORJAUS', 'vesivayla-hoito', current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaotsikkoid3;
        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Viankorjaus ajomatkoineen', 60, tehtavaryhmaotsikkoid3, toimenpideid, current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid6;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Rikkoutuneiden, tuhoutuneiden ja kadonneiden poijujen/suurviittojen uusiminen jääpeitteisenä aikana: työ ja ankkurointitarvikkeet',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 260,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Rikkoutuneiden, tuhoutuneiden ja kadonneiden poijujen/suurviittojen uusiminen avovesikaudella: työ ja ankkurointitarvikkeet ilman painoa',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 270,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Rikkoutuneiden, tuhoutuneiden ja kadonneiden suurviittojen uusiminen avovesikaudella: työ ja ankkurointitarvikkeet',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 280,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Rikkoutuneiden, tuhoutuneiden ja kadonneiden suurviittojen uusiminen avovesikaudella: työ ja ankkurointitarvikkeet ilman painoa',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 290,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Siirtyneiden poijujen ja suurviittojen siirto jääpeitteisenä aikana: työ', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 300,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Siirtyneiden poijujen ja suurviittojen siirto avovesikaudella: työ', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 310,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Siirtyneiden suurviittojen siirto jääpeitteisenä aikana: työ', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 320,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Siirtyneiden suurviittojen siirto avovesikaudella: työ', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 330,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Vikailmoitukseen perustuva viankorjauskäynti kelluvalla turvalaitteella: valovika (tavoitehinta ehdollinen)',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 340,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, 10),
               ('Vikailmoitukseen perustuva viankorjauskäynti kelluvalla turvalaitteella: valovika', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 350,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Vikailmoitukseen perustuva viankorjauskäynti kelluvalla turvalaitteella: viitan (ø ≤ 225 mm) siirto/vaihto/asennus',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 360,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Vikailmoitukseen perustuva viankorjauskäynti kelluvalla turvalaitteella: viitan (ø ≤ 225 mm) siirto/vaihto/asennus (tavoitehinta ehdollinen)',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 370,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, 10),
               ('Vikailmoitukseen perustuva viankorjauskäynti kiinteällä turvalaitteella sisältäen tutkamajakoiden energialähteiden viat',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 380,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Vikailmoitukseen perustuva viankorjauskäynti kiinteällä turvalaitteella sisältäen tutkamajakoiden energialähteiden viat (tavoitehinta ehdollinen)',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 390,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, 10),
               ('Vikailmoitukseen perustuva viankorjauskäynti kiinteällä turvalaitteella', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 400,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, 10),
               ('Jään alle ajautuneen kelluvan turvalaitteen ylösajo', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 410,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Irronneiden viittojen noutaminen asiakasilmoitusten perusteella', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 420,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, NULL),
               ('Irronneiden viittojen noutaminen asiakasilmoitusten perusteella (tavoitehinta ehdollinen)',
                toimenpideid, current_timestamp, kayttajaid, 'kpl', 430,
                '{kokonaishintainen}', tehtavaryhmaid6, false, true, 2023, NULL, true, true, 10);


        INSERT INTO tehtavaryhmaotsikko (otsikko, urakkatyyppi, luotu, luoja)
        VALUES ('AVOKANAVIEN HOITO', 'vesivayla-hoito', current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaotsikkoid4;
        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Avokanavien hoito', 70, tehtavaryhmaotsikkoid4, toimenpideid, current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid7;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Avokanavien reunavalaistuksien tarkastus ja huolto', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 440,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2023, NULL, true, true, NULL),
               ('Vikailmoitukseen perustuva vikakäynti avokanavilla: valovika', toimenpideid, current_timestamp,
                kayttajaid, 'kpl', 450,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2023, NULL, true, true, 10),
               ('Avokanavien luiskien vesakonraivaus', toimenpideid, current_timestamp, kayttajaid, 'm2', 460,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2023, NULL, true, true, NULL),
               ('Avokanavien reuna-alueiden käsiniitto', toimenpideid, current_timestamp, kayttajaid, 'm2', 470,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2023, NULL, true, true, NULL),
               ('Avokanavien reunateiden kunnossapito', toimenpideid, current_timestamp, kayttajaid, 'jm', 480,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2023, NULL, true, true, NULL),
               ('Muut avokanavien ympäristön hoitoon liittyvät tehtävät', toimenpideid, current_timestamp, kayttajaid,
                NULL, 490,
                '{kokonaishintainen}', tehtavaryhmaid7, false, true, 2023, NULL, false, false, NULL);


        INSERT INTO tehtavaryhmaotsikko (otsikko, urakkatyyppi, luotu, luoja)
        VALUES ('MUUT ASENNUSTYÖT', 'vesivayla-hoito', current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaotsikkoid5;
        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Muut asennustyöt ajomatkoineen', 80, tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp,
                kayttajaid)
        RETURNING id INTO tehtavaryhmaid8;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Uuden poijun asennustyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 500,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2023, NULL, true, true, NULL),
               ('Uuden viitan asennustyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 510,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2023, NULL, true, true, NULL),
               ('Uuden suurviitan asennustyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 520,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2023, NULL, true, true, NULL),
               ('Poijun poistotyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 530,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2023, NULL, true, true, NULL),
               ('Viitan poistotyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 540,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2023, NULL, true, true, NULL),
               ('Suurviitan poistotyö avovesikaudella', toimenpideid, current_timestamp, kayttajaid, 'kpl', 550,
                '{kokonaishintainen}', tehtavaryhmaid8, false, true, 2023, NULL, true, true, NULL);


        INSERT INTO tehtavaryhmaotsikko (otsikko, urakkatyyppi, luotu, luoja)
        VALUES ('MATERIAALIT', 'vesivayla-hoito', current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaotsikkoid6;
        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Materiaalit', 90, tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid9;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Uudet viitat mukaan lukien ankkurointitarvikkeet esim. kettingit, sakkelit ja painot', toimenpideid,
                current_timestamp, kayttajaid, 'kpl', 560,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2023, NULL, true, true, NULL),
               ('Jääpoiju, materiaali (PE)', toimenpideid, current_timestamp, kayttajaid, 'kpl', 570,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2023, NULL, true, true, NULL),
               ('Poijuviitta, materiaali (PE)', toimenpideid, current_timestamp, kayttajaid, 'kpl', 580,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2023, NULL, true, true, NULL),
               ('Jääpoiju, materiaali (PE), 5 v. paristomalli', toimenpideid, current_timestamp, kayttajaid, 'kpl', 590,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2023, NULL, true, true, NULL),
               ('Suurviitta (ø = 500 mm), materiaali', toimenpideid, current_timestamp, kayttajaid, 'kpl', 600,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2023, NULL, true, true, NULL),
               ('Suurviitta (ø = 400 mm), materiaali', toimenpideid, current_timestamp, kayttajaid, 'kpl', 610,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2023, NULL, true, true, NULL),
               ('6t paino', toimenpideid, current_timestamp, kayttajaid, 'kpl', 620,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2023, NULL, true, true, NULL),
               ('10t paino', toimenpideid, current_timestamp, kayttajaid, 'kpl', 630,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2023, NULL, true, true, NULL),
               ('20t lohkoankkuri', toimenpideid, current_timestamp, kayttajaid, 'kpl', 640,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2023, NULL, true, true, NULL),
               ('1200 kg paino', toimenpideid, current_timestamp, kayttajaid, 'kpl', 650,
                '{kokonaishintainen}', tehtavaryhmaid9, false, true, 2023, NULL, true, true, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Aurinkoenergialaitteet asennustarvikkeineen esim. telineet, kaapelit ja kaapelikiinnikkeet', 100,
                tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid10;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Aurinkopaneeli', toimenpideid, current_timestamp, kayttajaid, 'kpl', 660,
                '{kokonaishintainen}', tehtavaryhmaid10, false, true, 2023, NULL, true, true, NULL),
               ('NiCd-akku 50Ah', toimenpideid, current_timestamp, kayttajaid, 'kpl', 670,
                '{kokonaishintainen}', tehtavaryhmaid10, false, true, 2023, NULL, true, true, NULL),
               ('NiCd-akku 100Ah', toimenpideid, current_timestamp, kayttajaid, 'kpl', 680,
                '{kokonaishintainen}', tehtavaryhmaid10, false, true, 2023, NULL, true, true, NULL),
               ('NiCd-akku 150Ah', toimenpideid, current_timestamp, kayttajaid, 'kpl', 690,
                '{kokonaishintainen}', tehtavaryhmaid10, false, true, 2023, NULL, true, true, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Valolaitteet asennustarvikkeineen', 110, tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp,
                kayttajaid)
        RETURNING id INTO tehtavaryhmaid11;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('LED 155', toimenpideid, current_timestamp, kayttajaid, 'kpl', 700,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('LED 155 tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 710,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('LED 155, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 720,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('LED 155 tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 730,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('LED 160 tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 740,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('LED 160 tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 750,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('LO 200 M', toimenpideid, current_timestamp, kayttajaid, 'kpl', 760,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('LO 200 M tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 770,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('LO 200 M, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 780,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('LO 200 M tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 790,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('MPV- led', toimenpideid, current_timestamp, kayttajaid, 'kpl', 800,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('MPV- led, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 810,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('MPV 2- led tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 820,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('MPV 2- led tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 830,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('MVP 2- led, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 840,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('VP-led', toimenpideid, current_timestamp, kayttajaid, 'kpl', 850,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('VP- led, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 860,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('VP 2- led tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 870,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('VP 2- led', toimenpideid, current_timestamp, kayttajaid, 'kpl', 880,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('VP 2- led tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 890,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('ODSL 200', toimenpideid, current_timestamp, kayttajaid, 'kpl', 900,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('ODSL 200 tai vastaava', toimenpideid, current_timestamp, kayttajaid, 'kpl', 910,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('ODSL 200, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 920,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('ODSL 200 tai vastaava, huolto/korjaus', toimenpideid, current_timestamp, kayttajaid, 'kpl', 930,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL),
               ('UPS 12 verkkovirtamuuntaja', toimenpideid, current_timestamp, kayttajaid, 'kpl', 940,
                '{kokonaishintainen}', tehtavaryhmaid11, false, true, 2023, NULL, true, true, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Kelluvien turvalaitteiden paristot', 120, tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp,
                kayttajaid)
        RETURNING id INTO tehtavaryhmaid12;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Isot pyöreät kelluviin turvalaitteisiin', toimenpideid, current_timestamp, kayttajaid, 'kpl', 950,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2023, NULL, true, true, 10),
               ('Isot pyöreät paristot', toimenpideid, current_timestamp, kayttajaid, 'kpl', 960,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2023, NULL, true, true, NULL),
               ('Pienet pyöreät kelluviin turvalaitteisiin', toimenpideid, current_timestamp, kayttajaid, 'kpl', 970,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2023, NULL, true, true, 10),
               ('Nelikulmainen poijuparisto', toimenpideid, current_timestamp, kayttajaid, 'kpl', 980,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2023, NULL, true, true, NULL),
               ('Nelikulmainen poijuparisto (tavoitehinta ehdollinen)', toimenpideid, current_timestamp, kayttajaid,
                'kpl', 990,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2023, NULL, true, true, 10),
               ('5 vuoden paristot', toimenpideid, current_timestamp, kayttajaid, 'kpl', 100,
                '{kokonaishintainen}', tehtavaryhmaid12, false, true, 2023, NULL, true, true, NULL);

        INSERT INTO tehtavaryhma (nimi, jarjestys, tehtavaryhmaotsikko_id, toimenpide_id, luotu, luoja)
        VALUES ('Turvalaitteiden paristot', 130, tehtavaryhmaotsikkoid5, toimenpideid, current_timestamp, kayttajaid)
        RETURNING id INTO tehtavaryhmaid13;
        INSERT INTO tehtava (nimi, emo, luotu, luoja, yksikko, jarjestys, hinnoittelu, tehtavaryhma, "mhu-tehtava?",
                             "vhu-tehtava?",
                             voimassaolo_alkuvuosi, voimassaolo_loppuvuosi,
                             "maaramitattava?", tavoitehintavaikutus_aina, tavoitehintavaikutus_ehdollinen)
        VALUES ('Isot pyöreät turvalaitteisiin', toimenpideid, current_timestamp, kayttajaid, 'kpl', 110,
                '{kokonaishintainen}', tehtavaryhmaid13, false, true, 2023, NULL, true, true, 10),
               ('Pienet pyöreät turvalaitteisiin', toimenpideid, current_timestamp, kayttajaid, 'kpl', 120,
                '{kokonaishintainen}', tehtavaryhmaid13, false, true, 2023, NULL, true, true, 10),
               ('Nelikulmainen poijuparisto turvalaitteisiin', toimenpideid, current_timestamp, kayttajaid, 'kpl', 130,
                '{kokonaishintainen}', tehtavaryhmaid13, false, true, 2023, NULL, true, true, 10);

        RAISE NOTICE 'Tehtäväryhmäotsikot , tehtäväryhmät ja tehtävät lisätty onnistuneesti.';
    END
$$;
