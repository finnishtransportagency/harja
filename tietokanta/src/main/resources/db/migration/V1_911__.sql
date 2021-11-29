-- Korjaa kustannusarvioitujen töiden data "Rahavaraus lupaukseen 1"-rahavarausten osalta. VHAR-5483

-- Aiemmin kyseisen tyypin rahavaraus edusti yleisesti "muita rahavarauksia" ja rahavaraukset osoitettiin suoraan
-- tehtäväryhmälle "Tilaajan rahavaraus (T3)"
-- Nyt on haluttu seurata "muita rahavarauksia" tarkemmin, joten "Rahavaraus lupaukseen 1" rahavaraukset on siirretty osoittamaan
-- tehtävään jo olemassa olevaan 'Tilaajan rahavaraus lupaukseen 1' (joka on tehtäväryhmän "Tilaajan rahavaraus (T3)" lapsi).
-- (Lisäksi on luotu täysin uusi tehtävä, joka on myös "Tilaajan rahavaraus (T3)" lapsi, mutta tieto ei ole tärkeä tämän migraation kannalta.)

-- Kustannussuunnitelman logiikka kykenee käsittelemään oikein ainoastaan rivejä, joissa on arvo joko tehtäväryhmä- TAI tehtävä-sarakkeessa.
-- Lisäksi, nykyisin kustannussuunnitelman logiikka viittaa "Rahavaraus lupaukseen 1" kohdalla tehtävään 'Tilaajan rahavaraus lupaukseen 1'
-- tehtäväryhmän sijasta.

-- Jotta, vanhat tuotantokannassa olevat "Rahavaraus lupaukseen 1" arvot tulisivat näkyviin kustannussuunnitelmassa ja
-- jotta uusien arvojen tallentamisessa ei tulisi ongelmia, niin täytyy poistaa vanhoilta "Rahavaraus lupaukseen 1" -tyyppisiltä
-- riveiltä tehtäväryhmä ja lisätä tarvittaessa puuttuva tehtävä 'Tilaajan rahavaraus lupaukseen 1'.

  WITH tyot AS
           (SELECT kat.id
              FROM kustannusarvioitu_tyo kat
                       LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
                       LEFT JOIN urakka u ON tpi.urakka = u.id
                       LEFT JOIN tehtavaryhma tr ON kat.tehtavaryhma = tr.id
             WHERE u.tyyppi = 'teiden-hoito'
               -- Tilaajan rahavaraus (T3)
               AND tr.yksiloiva_tunniste = '0e78b556-74ee-437f-ac67-7a03381c64f6'
               AND kat.tyyppi = 'muut-rahavaraukset'
           )
UPDATE kustannusarvioitu_tyo
    -- Aseta tehtäväksi 'Tilaajan rahavaraus lupaukseen 1'
   SET tehtava      = (SELECT id
                         FROM toimenpidekoodi
                              -- Migraation ajohetkellä "Tilaajan rahavaraus lupaukseen 1"-tehtävällä ei pitäisi vielä olla yksilöivää tunnistetta,
                              -- koska R-migraatiot ajetaan viimeisenä. Tunniste lisätään vasta R-migraatiossa uutena muutoksena,
                              -- mutta tehtävä itsessään pitäisi jo olla olemassa tuotantokannassa.
                              -- Siksi tehtävään viitataan tässä nimellä ja tehtäväryhmällä, eikä tunnisteella.
                        WHERE nimi = 'Tilaajan rahavaraus lupaukseen 1'
                          AND tehtavaryhma = (SELECT id
                                                FROM tehtavaryhma
                                                     -- Tilaajan rahavaraus (T3)
                                               WHERE yksiloiva_tunniste = '0e78b556-74ee-437f-ac67-7a03381c64f6')),
       -- Poista vanha tehtäväryhmä
       tehtavaryhma = NULL
 WHERE id IN (SELECT id FROM tyot);
