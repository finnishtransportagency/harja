-- Annetaan mahdollisuus tallentaa myös soratiehoitoluokka välimuistitauluun
ALTER TABLE urakan_materiaalin_kaytto_hoitoluokittain
    ADD COLUMN soratiehoitoluokka INTEGER;
