ALTER TABLE tehtava
    ADD COLUMN nopeusrajoitus INTEGER NOT NULL DEFAULT 108;

COMMENT ON COLUMN tehtava.nopeusrajoitus IS 'Tehtävän mahdollisen reittitoteuman muodostukseen käytetty nopeusrajoitus km/h. Mikäli reittipisteiden välinen nopeus ylittää tämän, ei piirretä viivaa. Default 108 tulee aiemmin kovakoodatusta arvosta.';

-- Valaistuksen tehtävissä saatetaan ajaa nopeampaa kuin vanha 108 km/h rajoitus sallii, nostetaan rajoitusta.
UPDATE tehtava
SET nopeusrajoitus = 140
WHERE id IN (SELECT t.id
             FROM tehtava t
                      JOIN toimenpide tp ON t.emo = tp.id AND tp.koodi = 'VALA_YKSHINT');
