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
