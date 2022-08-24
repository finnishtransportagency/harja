/**
  Poistetaan "Tavoitehinnan ulkopuolisista rahavarauksista", eli "tilaajan varauksista" vahingossa aiemmissa migraatioissa
  automaattisesti lisätty indeksikorjattu summa. Tälle rahavaraukselle ei ole tarkoitus laskea indeksikorjauksia ollenkaan.
  Tavoitehinnan ulkopuoliset rahavaraukset on osoitettu "johto ja hallintokorvaus" tehtäväryhmälle tunnisteella
  a6614475-1950-4a61-82c6-fda0fd19bb54.
 */

  WITH tilaajan_rahavaraukset AS
           (SELECT kat.id
              FROM kustannusarvioitu_tyo kat
                       LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
                       LEFT JOIN tehtavaryhma tr ON kat.tehtavaryhma = tr.id
                       LEFT JOIN urakka u ON tpi.urakka = u.id
             WHERE u.tyyppi = 'teiden-hoito'
               AND tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54' -- Tilaajan rahavaraus
               AND kat.summa_indeksikorjattu IS NOT NULL
           )
UPDATE kustannusarvioitu_tyo
   SET summa_indeksikorjattu = NULL,
       muokkaaja             = (select id from kayttaja where kayttajanimi = 'Integraatio'),
       muokattu              = NOW()
 WHERE id IN (SELECT id FROM tilaajan_rahavaraukset);
