ALTER TABLE tehtava
ADD COLUMN linkkitunniste UUID;

COMMENT ON column tehtava.linkkitunniste IS
E'Joskus samalle reaalimaailman tehtävälle on Harjassa enemmän kuin yksi rivi TEHTAVA-taulussa. Alueurakoissa tämä oli yleistä, mutta tilanteeseen voidaan joutua myös MH-urakoissa,
jos eri vuosina toteumaa seurataan eri mittarilla (tehtävätoteuma raportoidaan eri yksiköllä) tai jos tehtävän linkitykset tehtäväryhmään tai toimenpiteeseen muuttuvat eri urakkavuosikerroissa.
Tämän kentän avulla voit linkittää saman työtehtävän eri ilmentymät yhteen. Huom. Sarakkeen lisäämisen yhteydessä ei käydä läpi olemassa olevia tehtäviä ja niiden linkitystarpeita, joten kaikkia linkityksiä ei ole tietokannassa.
Miksi voidaan haluta linkittää? Jotta osataan yhdistää tiedot raporteilla oikein, informaatioksi tietoa hyödyntäville kehitystiimeille.';
