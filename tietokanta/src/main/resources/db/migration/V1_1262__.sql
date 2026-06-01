-- Poistetaan hoitokauden_alkuvuosi-triggerit ja -funktiot, joita ei enää tarvita.

DROP TRIGGER IF EXISTS tg_toteuma_tehtava_hoitokauden_alkuvuosi ON toteuma_tehtava;
DROP TRIGGER IF EXISTS tg_toteuma_materiaali_hoitokauden_alkuvuosi ON toteuma_materiaali;
DROP TRIGGER IF EXISTS tg_toteuma_alkanut_paivitta_tehtavamateriaali_hoitokausi ON toteuma;

DROP FUNCTION IF EXISTS paivita_hoitokauden_alkuvuosi_tehtavalle();
DROP FUNCTION IF EXISTS paivita_hoitokauden_alkuvuosi_materiaalille();
DROP FUNCTION IF EXISTS paivita_hoitokauden_alkuvuosi_tehtava_ja_materiaalitauluista();

-- Poistetaan toteumittain-hoitokauden_alkuvuosi-funktio, jolla täytettiin hoitokauden alkuvuosi toteuma_tehtava- ja toteuma_materiaali-tauluihin,
-- koska tätä tietoa ei enää tarvita erikseen, kun hoitokauden alkuvuosi päivitetään suoraan näihin tauluihin koodin avulla.
DROP PROCEDURE IF EXISTS paivita_hoitokauden_alkuvuosi_toteumittain(INTEGER, BIGINT, BIGINT);

-- Lisää uusi indeksi - Nopeuttaa Toteuma Tehtävä sivua
CREATE INDEX idx_toteuma_materiaali_toteuma_urakka_hk
    ON toteuma_materiaali (toteuma, urakka_id, hoitokauden_alkuvuosi)
    INCLUDE (maara)
    WHERE poistettu = FALSE;

-- Ja vielä pienen ajansäästön saavuttamiseksi kulu -taululle indeksi - Nopeutaa muutosten hakemista
CREATE INDEX idx_kulu_urakka_erapaiva
    ON kulu (urakka, erapaiva)
    WHERE poistettu IS NOT TRUE;
