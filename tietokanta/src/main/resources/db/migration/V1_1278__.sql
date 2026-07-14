-- Kopioidaan tallennettu tieosoite myös uuden, laajan tieosoitteen tiedoksi
UPDATE paikkaus
SET tieosoite_laaja = ROW ((tierekisteriosoite).tie,
    (tierekisteriosoite).aosa,
    (tierekisteriosoite).aet,
    (tierekisteriosoite).losa,
    (tierekisteriosoite).let, NULL, NULL, NULL, NULL,
    (tierekisteriosoite).geometria)::tr_osoite_laajennettu
WHERE "paikkaus-tyyppi" IN ('paikkaus', 'reikapaikkaus');
