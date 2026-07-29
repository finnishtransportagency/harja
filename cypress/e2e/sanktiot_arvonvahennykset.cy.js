// Arvonvähennysten E2E-testit (Sanktiot ja bonukset -näkymä)
//
// Testataan arvonvähennyslomakkeen toiminta kolmella eri urakkatyypillä:
//  1) MHU25-urakka (Rovaniemi): "Vaikuttaa tavoitehintaan" + tehtäväryhmä & tehtävä
//  2) MHU25-urakka (Rovaniemi): "Ei vaikuta tavoitehintaan" + Kulun kohdistus
//  3) MHU24-urakka (Suomussalmi): tavoitehinta-radioiden näyttäminen asetuksen avulla,
//     ei tehtäväryhmä/tehtävä-valikoita, aina Kulun kohdistus + Laskutuskuukausi
//
// HUOM! Lomakkeen logiikka: src/cljs/harja/views/urakka/laadunseuranta/arvonvahennys_lomake.cljs

import {
    clickTimeout,
    SP,
    siivoaTietokannastaSanktiot,
    avaaSanktiotJaBonukset,
    avaaUusiArvonvahennys,
    kirjoitaSivupaneelissaTekstikenttaan,
    kirjoitaSivupaneelissaInputkenttaan,
    valitseSivupaneelissaAlasvetoarvo,
    valitseSivupaneelissaEnsimmainenAlasvetoarvo,
    valitseSivupaneelissaRadio,
    valitseSivupaneelissaPvm,
    tallennaSuorasanktiolomake,
    avaaSivupaneelissaTallennettu,
    siirrySivupaneelissaMuokkaustilaan
} from '../support/apurit';

// Asetuksia
let testiArvonvahennysKuvaus1 = "CY-mhu25-tavoitehinta";    // MHU25, vaikuttaa tavoitehintaan
let testiArvonvahennysKuvaus2 = "CY-mhu25-ei-tavoitehinta"; // MHU25, ei vaikuta tavoitehintaan
let testiArvonvahennysKuvaus3 = "CY-mhu24-raahe";     // MHU24, 2026
let testiArvonvahennysKuvaus4 = "CY-mhu19-oulu";     // MHU19, 2021

let testiArvonvahennysPerustelu1 = "CY-perustelu1-vaikuttaa-tavoitehintaan";
let testiArvonvahennysPerustelu3 = "CY-perustelu3";

let testiurakka1 = "Rovaniemen MHU testiurakka (1. hoitovuosi)"; // mhu25 urakka
let testiurakka2 = "Raahen MHU 2023-2028";              // mhu24 urakka
let testiurakka3 = "Oulun MHU 2019-2024";              // mhu19 urakka
let evk = "Lappi";
let evk2 = "Pohjois-Suomi";

// Havaittu- ja Määrätty/Käsitelty-päivämäärät.
// Sekä Rovaniemen (käynnissä 2025-10-01–2030-10-01) että Raahen
// (käynnissä 2024-10-01–2029-09-30) urakat ovat kuluvana vuonna (2026) käynnissä,
// joten käytetään kuluvan hoitokauden (1.10.2025–30.9.2026) sisällä olevia päiviä.
// HUOM: päivämäärän on oltava urakan voimassaolon sisällä, muuten pvm-valitsin hylkää sen.
let havaittuPvm = "01.03.2026";
let maarattyPvm = "15.03.2026";


// Helper: hallitse arvonvähennyslomakkeen MHU24-tarkistusta tietokanta-asetuksella.
// true  = validointi käytössä, eli MHU24-urakalla tavoitehinnan valintaa ei näytetä vielä 2026
// false = validointi pois käytöstä, eli MHU24-urakalla tavoitehinnan valinta näytetään jo nyt
function asetaArvonvahennysValidointiKayttoon(kaytossa) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"UPDATE jarjestelman_asetukset " +
            "SET arvonvahennys_validoinnit_kaytossa = " + kaytossa + ", " +
            "    muokattu = CURRENT_TIMESTAMP, " +
            "    muokkaaja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio');\"");
    });
}


// --- Testit: MHU25-urakka (Rovaniemi) ---

describe('Arvonvähennykset - MHU25-urakka (Rovaniemi)', () => {

    before(() => {
        siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus1);
        siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus2);
    });

    it('Arvonvähennys mhu25 urakalle', () => {
        avaaSanktiotJaBonukset(testiurakka1, evk);
        avaaUusiArvonvahennys();

        // Perustiedot
        kirjoitaSivupaneelissaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus1);
        kirjoitaSivupaneelissaTekstikenttaan('Perustelu', testiArvonvahennysPerustelu1);

        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('not.exist');

        kirjoitaSivupaneelissaInputkenttaan('Arvonvähennys', '200');

        // Tehtäväryhmän valinta laukaisee tehtävien haun
        cy.intercept('POST', '_/hae-tehtavaryhman-tehtavat-urakalle').as('haeTehtavat');
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Tehtäväryhmä');
        cy.wait('@haeTehtavat', {timeout: clickTimeout});
        // HUOM: testidatan tehtäväryhmällä tulee olla tehtäviä, jotta tehtävä on valittavissa
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Tehtävä');

        // Päivämäärät
        valitseSivupaneelissaPvm('Havaittu', havaittuPvm);
        valitseSivupaneelissaPvm('Määrätty', maarattyPvm);

        // Määräystapa ja käsittelytapa
        valitseSivupaneelissaRadio('Työmaakokous');

        tallennaSuorasanktiolomake();

        // Tallennus näkyy listalla
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus1).should('exist');
        cy.get('table.grid').should('contain', testiArvonvahennysPerustelu1);
        cy.get('table.grid').should('contain', '-200');


        // Avataan tallennettu lukutilassa ja tarkistetaan tiedot
        avaaSivupaneelissaTallennettu(testiArvonvahennysKuvaus1);
        cy.get(SP).contains(testiArvonvahennysKuvaus1).should('exist');

        // Muokataan kuvausta
        siirrySivupaneelissaMuokkaustilaan();
        kirjoitaSivupaneelissaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus1 + ' muokattu');
        // Vaihda tehtäväryhmä ja tehtävä
        valitseSivupaneelissaAlasvetoarvo('Tehtäväryhmä', 'L - Liikennemerkit ja liikenteenohjauslaitteet');
        cy.wait('@haeTehtavat', {timeout: clickTimeout});
        valitseSivupaneelissaAlasvetoarvo('Tehtävä', 'Opastustaulun/-viitan uusiminen');

        tallennaLomake();

        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus1 + ' muokattu').should('exist');
    });

});

// --- Testit: MHU24-urakka (Suomussalmi) vuonna 2026 ---

describe('Arvonvähennykset - Raahen MHU24-urakka, validointi pois käytöstä - eli uusi lomake', () => {

    before(() => {
        siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus3);
        // Otetaan MHU24-tarkistus pois käytöstä.
        asetaArvonvahennysValidointiKayttoon(false);
    });

    after(() => {
        // Palautetaan asetus testin jälkeen oletustilaan, ettei testi jätä ympäristöä muutettuun tilaan.
        asetaArvonvahennysValidointiKayttoon(true);
    });

    it('Tarkista lomakkeen tiedot', () => {
        avaaSanktiotJaBonukset(testiurakka2, evk2);
        avaaUusiArvonvahennys();

        // MHU24-urakalla ei silti näytetä tehtäväryhmää eikä tehtävää, eikä mhu24 näytetä indeksiä
        // vaan Kulun kohdistus ja Laskutuskuukausi ovat näkyvissä.
        cy.get(SP).contains('.form-group', 'Indeksi').should('not.exist');
        cy.get(SP).contains('.form-group', 'Tehtäväryhmä').should('not.exist');
        cy.get(SP).contains('.form-group', 'Tehtävä').should('not.exist');

        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('exist');
        cy.get(SP).contains('.form-group', 'Laskutuskuukausi').should('exist');
        cy.get(SP).contains('.form-group', 'Tavoitehinnan alennus').should('exist');
        cy.get(SP).contains('.form-group', 'Arvonvähennys').should('exist');
        cy.get(SP).contains('.form-group', 'Käsittelytapa').should('exist');
        cy.get(SP).contains('.form-group', 'Havaittu').should('exist');
        cy.get(SP).contains('.form-group', 'Käsitelty').should('exist');

        // Täytetään lomake
        kirjoitaSivupaneelissaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus3);
        kirjoitaSivupaneelissaTekstikenttaan('Perustelu', testiArvonvahennysPerustelu3);
        kirjoitaSivupaneelissaInputkenttaan('Arvonvähennys', '80');
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Kulun kohdistus');

        valitseSivupaneelissaPvm('Havaittu', havaittuPvm);
        valitseSivupaneelissaPvm('Käsitelty', maarattyPvm);

        // Käsittelytapa on MHU24-urakalla alasvetovalikko
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Käsittelytapa');

        tallennaSuorasanktiolomake();
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus3).should('exist');

        // Avaa ja muokkaa
        avaaSivupaneelissaTallennettu(testiArvonvahennysKuvaus3);
        cy.get(SP).contains(testiArvonvahennysKuvaus3).should('exist');

        siirrySivupaneelissaMuokkaustilaan();

        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('exist');
        cy.get(SP).contains('.form-group', 'Laskutuskuukausi').should('exist');

        kirjoitaSivupaneelissaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus3 + ' muokattu');
        tallennaSuorasanktiolomake();
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus3 + ' muokattu').should('exist');
    });


});

describe('Arvonvähennykset - Oulun MHU19-urakka, validointi pois käytöstä - eli uusi lomake', () => {

    before(() => {
        siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus4);
        // Otetaan MHU24-tarkistus pois käytöstä.
        asetaArvonvahennysValidointiKayttoon(false);
    });

    after(() => {
        // Palautetaan asetus testin jälkeen oletustilaan, ettei testi jätä ympäristöä muutettuun tilaan.
        asetaArvonvahennysValidointiKayttoon(true);
    });

    it('Tarkista lomakkeen tiedot', () => {
        avaaSanktiotJaBonukset(testiurakka3, evk2);
        avaaUusiArvonvahennys();

        // MHU24-urakalla ei silti näytetä tehtäväryhmää eikä tehtävää, eikä tavoitehinnan alennus
        // vaan Kulun kohdistus, Laskutuskuukausi ja indeksi ovat näkyvissä.
        cy.get(SP).contains('.form-group', 'Tehtäväryhmä').should('not.exist');
        cy.get(SP).contains('.form-group', 'Tehtävä').should('not.exist');
        cy.get(SP).contains('.form-group', 'Tavoitehinnan alennus').should('not.exist');

        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('exist');
        cy.get(SP).contains('.form-group', 'Laskutuskuukausi').should('exist');
        cy.get(SP).contains('.form-group', 'Indeksi').should('exist');
        cy.get(SP).contains('.form-group', 'Arvonvähennys').should('exist');
        cy.get(SP).contains('.form-group', 'Käsittelytapa').should('exist');
        cy.get(SP).contains('.form-group', 'Havaittu').should('exist');
        cy.get(SP).contains('.form-group', 'Käsitelty').should('exist');

        // Täytetään lomake
        kirjoitaSivupaneelissaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus3);
        kirjoitaSivupaneelissaTekstikenttaan('Perustelu', testiArvonvahennysPerustelu3);
        kirjoitaSivupaneelissaInputkenttaan('Arvonvähennys', '80');
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Kulun kohdistus');
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Indeksi');

        valitseSivupaneelissaPvm('Havaittu', havaittuPvm);
        valitseSivupaneelissaPvm('Käsitelty', maarattyPvm);

        // Käsittelytapa on MHU24-urakalla alasvetovalikko
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Käsittelytapa');

        tallennaSuorasanktiolomake();
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus3).should('exist');

        // Avaa ja muokkaa
        avaaSivupaneelissaTallennettu(testiArvonvahennysKuvaus3);
        cy.get(SP).contains(testiArvonvahennysKuvaus3).should('exist');

        siirrySivupaneelissaMuokkaustilaan();

        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('exist');
        cy.get(SP).contains('.form-group', 'Laskutuskuukausi').should('exist');

        kirjoitaSivupaneelissaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus3 + ' muokattu');
        tallennaSuorasanktiolomake();
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus3 + ' muokattu').should('exist');
    });


});

describe('Arvonvähennykset - MHU24-urakka (Suomussalmi), validointi käytössä', () => {

    before(() => {
        siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus3);
        // Otetaan MHU24-tarkistus käyttöön
        asetaArvonvahennysValidointiKayttoon(true);
    });

    it('Vanha lomake käytössä - varmistetaan toiminta', () => {
        avaaSanktiotJaBonukset(testiurakka2, evk2);

        cy.contains('.lisaa-nappi', 'Lisää uusi').click();
        cy.get(SP, {timeout: clickTimeout}).should('be.visible');
        // Sivupaneeli avautuu oletuksena sanktiolomakkeelle -> valitaan Sanktio ja varmistetaan, että Arvonvähennystä ei ole paikalla
        cy.get(SP).contains('label', 'Arvonvähennys').should('not.exist');
        cy.get(SP).contains('label', 'Sanktio').should('be.visible');
        cy.get(SP).contains('label', 'Sanktio').click();

        // Sanktion laji
        cy.get('label[for*=laji] + div').valinnatValitse({valinta: 'A-ryhmä (tehtäväkohtainen sanktio)'});


        // Kun arvonvähennyslomakkeen MHU24-tarkistus on otettu pois käytöstä,
        // tavoitehinnan valinta näkyy myös MHU24-urakalla.
        cy.get(SP).contains('label', 'Vaikuttaa tavoitehintaan').should('not.exist');

        // MHU24-urakalla ei silti näytetä tehtäväryhmää eikä tehtävää,
        // vaan Kulun kohdistus ja Laskutuskuukausi ovat näkyvissä.
        cy.get(SP).contains('.form-group', 'Tehtäväryhmä').should('not.exist');
        cy.get(SP).contains('.form-group', 'Tehtävä').should('not.exist');
        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('exist');
        cy.get(SP).contains('.form-group', 'Laskutuskuukausi').should('exist');
        cy.get(SP).contains('.form-group', 'Sanktion suuruus').should('exist');
        cy.get(SP).contains('.form-group', 'Käsittelytapa').should('exist');

        // Täytetään lomake
        valitseSivupaneelissaAlasvetoarvo('Sanktion laji', 'Arvonvähennys');
        kirjoitaSivupaneelissaTekstikenttaan('Perustelu', testiArvonvahennysPerustelu3);
        kirjoitaSivupaneelissaInputkenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus3);
        kirjoitaSivupaneelissaInputkenttaan('Sanktion suuruus', '80');
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Kulun kohdistus');

        valitseSivupaneelissaPvm('Havaittu', havaittuPvm);
        valitseSivupaneelissaPvm('Käsitelty', maarattyPvm);

        // Käsittelytapa on MHU24-urakalla alasvetovalikko
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Käsittelytapa');

        tallennaSuorasanktiolomake();
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus3).should('exist');

        // Avaa ja muokkaa
        avaaSivupaneelissaTallennettu(testiArvonvahennysKuvaus3);
        cy.get(SP).contains(testiArvonvahennysKuvaus3).should('exist');

        siirrySivupaneelissaMuokkaustilaan();
        // Myös muokkaustilassa tavoitehinta-radio näkyy, kun MHU24-tarkistus on pois käytöstä.
        cy.get(SP).contains('label', 'Sanktion suuruus').should('exist');
        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('exist');
        cy.get(SP).contains('.form-group', 'Laskutuskuukausi').should('exist');

        kirjoitaSivupaneelissaInputkenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus3 + ' muokattu');
        tallennaSuorasanktiolomake();
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus3 + ' muokattu').should('exist');
    });


    describe('Siivotaan lopuksi', function () {
        before(function () {
            siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus1);
            siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus1+ ' muokattu');
            siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus2);
            siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus3);
            siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus3+ ' muokattu');
        });

        it('Tarkista, että kanta on siivottu', function () {
            cy.viewport(1100, 1200);

            avaaSanktiotJaBonukset(testiurakka1, evk);
            cy.contains(testiArvonvahennysKuvaus1).should('not.exist');
            cy.contains(testiArvonvahennysKuvaus2).should('not.exist');

            avaaSanktiotJaBonukset(testiurakka2, evk2);
            cy.contains(testiArvonvahennysKuvaus3).should('not.exist');
        });
    });
});



