-- Otetaan soratiehoitoluokkien päivitys pois käytöstä hetkeksi. Saa laittaa käyttöön 2023 alkaville urakoille syksyllä
update geometriapaivitys set kaytossa = false WHERE nimi = 'soratieluokat';
