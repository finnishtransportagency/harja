-- Mahdollistetaan rajoitusalueen muokkaus ilman, että se generoi koko suolatoteumien uudelleen laskennan
ALTER TABLE rajoitusalue ADD tierekisteri_muokattu BOOLEAN DEFAULT FALSE;
