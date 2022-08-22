Nämä tiedostot on muodostettu hakemalla Varusterekisteristä tai Varusterekisteristä kohteita, jotka kuuluvat
tietolajeihin. Tietolajin tunnus näkyy toistaiseksi OID:ssa. 

Testit käyttävät oidissa näkyvää tietolajin tunnusta varmistamaan, että funktio `paattele-tietolaji` toimii oikein.
Testit löytyvät modulista `test/clj/harja/palvelin/integraatiot/velho/velho_komponentti_test.clj`.

Näitä tiedostoja käyttävien testien nimet ovat muotoa `paattele-kohteet-*-test`.