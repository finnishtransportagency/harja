-- 11.8.2023 ei näy integraatiolokilla yhtään viestiä näistä integraatioista, joten pitäisi olla turvallista.
-- Lisäksi kaikki koodi, joka näitä rivejä loisi, on poistettu.
DELETE
FROM integraatio
WHERE (jarjestelma = 'api'
    AND nimi IN (
                 'hae-tietue',
                 'hae-tietueet',
                 'lisaa-tietue',
                 'paivita-tietue',
                 'poista-tietue'))
   OR (jarjestelma = 'tierekisteri'
    AND nimi IN (
                 'hae-tietue',
                 'hae-tietueet',
                 'hae-urakan-tietueet',
                 'lisaa-tietue',
                 'paivita-tietue',
                 'poista-tietue'));
