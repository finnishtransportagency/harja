-- Lisätän tehtäväryhmä-tauluun linkki toimenpide-tauluun, jotta yhteys on näkyvämpi eikä kulje ainoastaan tehtävän kautta.
-- Ideaalitapauksessa toimenpide olisi not null, mutta Alataso, lisätyöt dummy-tehtäväryhmän takia näin ei voida tehdä (se linkittyy tehtävien kautta kolmeen toimenpiteeseen).
-- Selvitetään erikseen voidaanko dummy-tehtäväryhmästä luopua (siihen kuuluvia tehtäviä käytetään lisätöiden toteumien mutta ei kulujen kirjaamiseen) ja päivitetään saraketta myöhemmin, jos voidaan.
ALTER TABLE tehtavaryhma
    ADD COLUMN toimenpide_id INTEGER references toimenpide (id);
