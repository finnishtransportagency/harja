-- Muutetaan tehtävä "Porttaalien tarkastus ja huolto" näkymään Toteumat/Tehtävät-näkymän listassa.
UPDATE tehtava SET kasin_lisattava_maara = true
WHERE nimi = 'Porttaalien tarkastus ja huolto' AND kasin_lisattava_maara = false;
