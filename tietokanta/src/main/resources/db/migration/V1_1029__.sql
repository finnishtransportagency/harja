-- Lupaus -taulussa on ollut vuosina 21-23 virhe järjestyksessään 7:ssä lupauksessa. Laadunseuranta oli virheellisesti yli 6 kertaa. Tässä korjataan kuuten tai enemmän
UPDATE lupaus
SET sisalto = 'Teemme urakassa muuttuvissa keliolosuhteissa laadunseurantaa myös pistokokeina ≥ 6 kertaa
 talvessa (esim. toimenpideajassa pysyminen, työn jälki, työmenetelmä, reagointikyky ja
 liukkaudentorjuntamateriaalien annosmäärät), joista kolme tehdään klo 20–06 välillä ja/tai
 viikonloppuisin. Laadimme jokaisesta pistokokeesta erillisen raportin ja luovutamme sen tilaajalle
 viimeistään seuraavassa työmaakokouksessa.'
WHERE jarjestys = 7 AND "urakan-alkuvuosi" IN (2021, 2022);
