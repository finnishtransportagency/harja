-- Lisää vesiväyläurakoiden toimenpideinstansseilla sopivat toimenpidekoodit (Kaukon "MERIVÄYLIEN URAKKARAKENNE HARJASSA - EHDOTUS - 24.4.2017" Excelin pohjalta)

INSERT INTO toimenpidekoodi (taso, emo, nimi) VALUES (3, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Väylänhoito'), 'Kauppamerenkulun kustannukset');
INSERT INTO toimenpidekoodi (taso, emo, nimi) VALUES (3, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Väylänhoito'), 'Muun vesiliikenteen kustannukset');
INSERT INTO toimenpidekoodi (taso, emo, nimi) VALUES (3, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Väylänhoito'), 'Urakan yhteiset kustannukset');

-- Lisää olemassa oleville VV-urakoille toimenpideinstanssit
