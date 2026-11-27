-- Estetään useampi kustannusrivi samalle ylläpitokohteelle, jotta samanaikaiset tallennukset
-- voidaan tehdä atomisena upsertina.
-- Poistetaan ensin mahdolliset duplikaatit: säilytetään viimeksi muokattu rivi kohdetta kohden.
DELETE FROM tiemerkinta_yllapitokohteen_kustannus t
 USING tiemerkinta_yllapitokohteen_kustannus uudempi
 WHERE t.yllapitokohde = uudempi.yllapitokohde
   AND (COALESCE(t.muokattu, t.luotu), t.id) < (COALESCE(uudempi.muokattu, uudempi.luotu), uudempi.id);

ALTER TABLE tiemerkinta_yllapitokohteen_kustannus
    ADD CONSTRAINT uniikki_tiemerkinta_yllapitokohteen_kustannus UNIQUE (yllapitokohde);
