CREATE TABLE tavoitehinnan_oikaisu
(
    id             SERIAL PRIMARY KEY,
    "urakka-id"    INTEGER NOT NULL REFERENCES urakka (id),
    "luoja-id"     INTEGER REFERENCES kayttaja (id),
    luotu          TIMESTAMP,
    "muokkaaja-id" INTEGER NOT NULL REFERENCES kayttaja (id),
    muokattu       TIMESTAMP,
    otsikko        TEXT NOT NULL,
    selite         TEXT NOT NULL,
    summa          NUMERIC NOT NULL,
    hoitokausi     INT NOT NULL,
    poistettu      BOOLEAN
);

COMMENT ON table tavoitehinnan_oikaisu IS
    E'Urakan tavoitehinnan oikaisut tehdään vuoden lopussa tehdävän välikatselmuksen aikana. Esimerkiksi
     odotettua suuremmat syystulvat saattavat venyttää urakkaa, jolloin tavoitehintaa nostetaan.
     Tavoitehinnan oikaisu voi olla myös miinusmerkkinen, eli urakka saatiin tehtyä odotettua halvemmalla.';
