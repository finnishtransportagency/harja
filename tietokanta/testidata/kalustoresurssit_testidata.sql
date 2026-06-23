-- Kalustoresurssit-alasivun testaamista varten tarvittava testidata.
-- Luodaan MHU26-urakka (alkuvuosi 2026) Suunnittelu/Kalustoresurssit-alasivun testaamista varten.
-- Kopioidaan alue, hallintayksikkö, elinvoimakeskus ja urakoitsija Kittilän MHU 2025-2030 -urakalta.
INSERT INTO urakka (sampoid,        hallintayksikko, elinvoimakeskus_id, nimi,                     alkupvm,      loppupvm,     tyyppi, urakkanro, urakoitsija, alue)
SELECT              '1242141-KITT5', hallintayksikko, elinvoimakeskus_id, 'Kittilän MHU 2026-2031', '2026-10-01', '2031-09-30', tyyppi, '1446',    urakoitsija, alue
  FROM urakka
 WHERE nimi = 'Kittilän MHU 2025-2030';

INSERT INTO sopimus (nimi, alkupvm, loppupvm, sampoid, urakka)
VALUES ('Kittilän MHU sopimus 26', '2026-10-01', '2031-09-30', '11333379-LAP1',
        (SELECT id FROM urakka WHERE nimi = 'Kittilän MHU 2026-2031'));
