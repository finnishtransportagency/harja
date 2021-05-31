-- Muutetaan tehtäväryhmien nimiä. Nimet on muutettu myös R_toimenpidekoodit_tehtavaryhmat-migraatioon. Siellä niitä käytetään konfliktin ehtona, minkä takia päivitys tarvitaan myös täällä.
UPDATE tehtavaryhma SET nimi = 'Puhallus-SIP (Y5)' WHERE nimi = 'Puhallus-SIP (Y3)';
UPDATE tehtavaryhma SET nimi = 'Saumojen juottaminen bitumilla (Y6)' WHERE nimi = 'Saumojen juottaminen bitumilla (Y4)';
UPDATE tehtavaryhma SET nimi = 'KT-Valu (Y3)' WHERE nimi = 'Massasaumaus (Y5)';
UPDATE tehtavaryhma SET poistettu = TRUE WHERE nimi = 'KT-valu (Y6)';

