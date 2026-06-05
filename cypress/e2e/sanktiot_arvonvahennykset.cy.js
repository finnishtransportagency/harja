// Arvonvähennysten E2E-testit (Sanktiot ja bonukset -näkymä)
//
// Testataan arvonvähennyslomakkeen toiminta kolmella eri urakkatyypillä:
//  1) MHU25-urakka (Rovaniemi): "Vaikuttaa tavoitehintaan" + tehtäväryhmä & tehtävä
//  2) MHU25-urakka (Rovaniemi): "Ei vaikuta tavoitehintaan" + Kulun kohdistus
//  3) MHU24-urakka (Suomussalmi): tavoitehinta-radioiden näyttäminen asetuksen avulla,
//     ei tehtäväryhmä/tehtävä-valikoita, aina Kulun kohdistus + Laskutuskuukausi
//
// HUOM! Lomakkeen logiikka: src/cljs/harja/views/urakka/laadunseuranta/arvonvahennys_lomake.cljs

// Asetuksia
let clickTimeout = 12000;
let pageloadTimeout = 30000;

let testiArvonvahennysKuvaus1 = "CY-mhu25-tavoitehinta";    // MHU25, vaikuttaa tavoitehintaan
let testiArvonvahennysKuvaus2 = "CY-mhu25-ei-tavoitehinta"; // MHU25, ei vaikuta tavoitehintaan
let testiArvonvahennysKuvaus3 = "CY-mhu24-suomussalmi";     // MHU24, 2026

let testiArvonvahennysPerustelu1 = "CY-perustelu1";
let testiArvonvahennysPerustelu2 = "CY-perustelu2";
let testiArvonvahennysPerustelu3 = "CY-perustelu3";

let testiurakka1 = "Rovaniemen MHU testiurakka (1. hoitovuosi)"; // mhu25 urakka
let testiurakka2 = "POP MHU Suomussalmi 2024-2029";              // mhu24 urakka
let evk = "Lappi";
let evk2 = "Pohjois-Suomi";

// Havaittu- ja Määrätty/Käsitelty-päivämäärät.
// Sekä Rovaniemen (käynnissä 2025-10-01–2030-10-01) että Suomussalmen
// (käynnissä 2024-10-01–2029-09-30) urakat ovat kuluvana vuonna (2026) käynnissä,
// joten käytetään kuluvan hoitokauden (1.10.2025–30.9.2026) sisällä olevia päiviä.
// HUOM: päivämäärän on oltava urakan voimassaolon sisällä, muuten pvm-valitsin hylkää sen.
let havaittuPvm = "01.03.2026";
let maarattyPvm = "15.03.2026";

// Sivupaneelin juuri, jonka sisällä lomake renderöidään (ks. sanktiot_ja_bonukset_nakyma.cljs)
const SP = '.ei-sulje-sivupaneelia';

// Helper: siivoa testidatan sanktiot kannasta
function siivoaKanta(kohde) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM sanktio WHERE suorasanktio = true AND id IN (SELECT s.id FROM sanktio s JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id WHERE lp.kohde = '" + kohde + "');\"");
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM laatupoikkeama WHERE kohde = '" + kohde + "';\"");
    });
}

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

// Helper: navigoi sanktiot ja bonukset -näkymään
let avaaSanktiotJaBonukset = function (urakkaNimi, urakkaEvk) {
    cy.intercept('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('sanktiot')

    cy.viewport(1400, 1400)
    cy.visit("/")

    cy.contains('.haku-lista-item', urakkaEvk).click()
    cy.get('.ajax-loader', {timeout: pageloadTimeout}).should('not.exist')
    cy.get('[data-cy=murupolku-urakkatyyppi]').find('button').click()
    cy.wait(250); // Pudotusvalikko re-renderöityy avattaessa
    cy.get('[data-cy=murupolku-urakkatyyppi]').contains('ul li a', 'Hoito').click({force: true})
    cy.contains('Näytä päättyneet').click();
    cy.wait(250); // Toimii varmemmin, kun ei ole niin kiire
    cy.contains('[data-cy=urakat-valitse-urakka] li', urakkaNimi, {timeout: pageloadTimeout}).click()
    cy.get('[data-cy=tabs-taso1-Laadunseuranta]').click()
    cy.get('[data-cy="tabs-taso2-Sanktiot ja bonukset"]').click()
    cy.wait('@sanktiot', {timeout: clickTimeout})
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
}

// --- Lomakkeen apufunktiot ---

// Avaa uusi arvonvähennyslomake sivupaneeliin
function avaaUusiArvonvahennys() {
    cy.contains('.lisaa-nappi', 'Lisää uusi').click();
    cy.get(SP, {timeout: clickTimeout}).should('be.visible');
    // Sivupaneeli avautuu oletuksena sanktiolomakkeelle -> valitaan "Arvonvähennys"
    cy.get(SP).contains('label', 'Arvonvähennys').click();
}

// Kirjoita :text-tyyppiseen kenttään (textarea) otsikon perusteella
function kirjoitaTekstikenttaan(otsikko, teksti) {
    cy.get(SP).contains('.form-group', otsikko).find('textarea').first().clear();
    cy.get(SP).contains('.form-group', otsikko).find('textarea').first().type(teksti);
}

// Kirjoita :euro-/:string-tyyppiseen input-kenttään otsikon perusteella
function kirjoitaInputkenttaan(otsikko, arvo) {
    cy.get(SP).contains('.form-group', otsikko).find('input').first().clear();
    cy.get(SP).contains('.form-group', otsikko).find('input').first().type(arvo);
}

// Valitse alasvetovalikosta arvo näkyvän tekstin perusteella (otsikon perusteella löydetty kenttä)
function valitseAlasvetoarvo(otsikko, arvoTeksti) {
    cy.get(SP).contains('.form-group', otsikko).within(() => {
        cy.get('button').first().click();
    });
    cy.wait(250); // Lista re-renderöityy, annetaan sen asettua
    cy.get(SP).contains('.form-group', otsikko).contains('ul li a', arvoTeksti).click({force: true});
}

// Valitse alasvetovalikon ensimmäinen oikea vaihtoehto (kun arvo on datariippuvainen)
function valitseEnsimmainenAlasvetoarvo(otsikko) {
    cy.get(SP).contains('.form-group', otsikko).within(() => {
        cy.get('button').first().click();
    });
    cy.wait(250);
    cy.get(SP).contains('.form-group', otsikko).find('ul li a').first().click({force: true});
}

// Valitse alasvetovalikon ensimmäinen vaihtoehto data-cy:n perusteella (esim. laskutuskuukausi)
function valitseEnsimmainenDataCyAlasveto(dataCy) {
    cy.get('[data-cy=' + dataCy + ']').find('button').click();
    cy.wait(250);
    cy.get('[data-cy=' + dataCy + ']').find('ul li a').first().click({force: true});
}

// Valitse radio-painike sen näkyvän tekstin perusteella
function valitseRadio(teksti) {
    // Vayla-radio renderöityy muodossa: input.vayla-radio + label[for=input-id].
    // Valitaan varsinainen radio-input labelin for-attribuutin kautta, koska pelkkä
    // labelin klikkaus ei ole tässä komponentissa aina luotettava Cypressissä.
    cy.get(SP).contains('label', teksti).then(($label) => {
        let inputId = $label.attr('for');
        expect(inputId, 'radio-labelilla pitää olla for-attribuutti').to.exist;

        cy.get(SP).find('input[type="radio"][id="' + inputId + '"]').as('radioKentta');
        cy.get('@radioKentta').check({force: true});
        cy.get('@radioKentta').should('be.checked');
    });
}

// Valitse päivämäärä :pvm-kenttään otsikon perusteella
function valitsePvm(otsikko, pvm) {
    // Päivämääräkentät ovat lomakkeella ryhmässä, joten haetaan input nimenomaan labelin kautta.
    // Kenttä on :pvm, joten arvo annetaan muodossa "p.k.vvvv" ilman kellonaikaa.
    cy.get(SP).find('label').contains(otsikko)
        .parents('.form-group').first()
        .find('input').first()
        .as('pvmKentta');

    cy.get('@pvmKentta').clear().type(pvm);
    cy.get('@pvmKentta').should('have.value', pvm);

    // Päivämääräpopup jää muuten helposti auki seuraavien kenttien päälle.
    // Suljetaan se siirtämällä fokus ja klikkaamalla labelia
    cy.get(SP).find('label').contains(otsikko).click({force: true});
}

// Tallenna lomake ja odota tallennuskutsua
function tallennaLomake() {
    cy.intercept('POST', '_/tallenna-suorasanktio').as('tallennaSanktio');
    cy.get(SP).contains('button', 'Tallenna').click();
    cy.wait('@tallennaSanktio', {timeout: clickTimeout});
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist');
}

// Avaa listalta tallennettu arvonvähennys (lukutilaan) kuvauksen perusteella
function avaaTallennettu(kuvaus) {
    cy.get('.sanktiot').contains('td', kuvaus, {timeout: clickTimeout}).click();
    cy.get(SP, {timeout: clickTimeout}).should('be.visible');
}

// Siirry lukutilasta muokkaustilaan
function siirryMuokkaustilaan() {
    cy.get(SP).contains('button', 'Muokkaa').click();
}

// --- Testit: MHU25-urakka (Rovaniemi) ---

describe('Arvonvähennykset - MHU25-urakka (Rovaniemi)', () => {

    before(() => {
        siivoaKanta(testiArvonvahennysKuvaus1);
        siivoaKanta(testiArvonvahennysKuvaus2);
    });

    after(() => {
        siivoaKanta(testiArvonvahennysKuvaus1);
        siivoaKanta(testiArvonvahennysKuvaus2);
    });

    it('Arvonvähennys, joka vaikuttaa tavoitehintaan (tehtäväryhmä + tehtävä)', () => {
        avaaSanktiotJaBonukset(testiurakka1, evk);
        avaaUusiArvonvahennys();

        // MHU25-lomakkeella näkyy tavoitehinta-radiot
        cy.get(SP).contains('label', 'Vaikuttaa tavoitehintaan').should('exist');
        cy.get(SP).contains('label', 'Ei vaikuta tavoitehintaan').should('exist');

        // Perustiedot
        kirjoitaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus1);
        kirjoitaTekstikenttaan('Perustelu', testiArvonvahennysPerustelu1);

        // Valitaan "Vaikuttaa tavoitehintaan" -> näkyviin tulevat tavoitehinnan alennus, tehtäväryhmä ja tehtävä
        valitseRadio('Vaikuttaa tavoitehintaan');
        cy.get(SP).contains('.form-group', 'Tavoitehinnan alennus').should('exist');
        // Kulun kohdistus EI näy, kun arvonvähennys vaikuttaa tavoitehintaan
        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('not.exist');

        kirjoitaInputkenttaan('Tavoitehinnan alennus', '200');
        kirjoitaInputkenttaan('Vähennyksen määrä', '150');

        // Tehtäväryhmän valinta laukaisee tehtävien haun
        cy.intercept('POST', '_/hae-tehtavaryhman-tehtavat-urakalle').as('haeTehtavat');
        valitseEnsimmainenAlasvetoarvo('Tehtäväryhmä');
        cy.wait('@haeTehtavat', {timeout: clickTimeout});
        // HUOM: testidatan tehtäväryhmällä tulee olla tehtäviä, jotta tehtävä on valittavissa
        valitseEnsimmainenAlasvetoarvo('Tehtävä');

        // Päivämäärät
        valitsePvm('Havaittu', havaittuPvm);
        valitsePvm('Määrätty', maarattyPvm);

        // Määräystapa ja käsittelytapa
        valitseRadio('Työmaakokous');
        valitseAlasvetoarvo('Käsittelytapa', 'Välikatselmus');

        tallennaLomake();

        // Tallennus näkyy listalla
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus1).should('exist');

        // Avataan tallennettu lukutilassa ja tarkistetaan tiedot
        avaaTallennettu(testiArvonvahennysKuvaus1);
        cy.get(SP).contains(testiArvonvahennysKuvaus1).should('exist');
        cy.get(SP).contains('Vaikuttaa tavoitehintaan').should('exist');

        // Muokataan kuvausta
        siirryMuokkaustilaan();
        kirjoitaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus1 + ' muokattu');
        tallennaLomake();

        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus1 + ' muokattu').should('exist');
    });

    it('Arvonvähennys, joka ei vaikuta tavoitehintaan (Kulun kohdistus)', () => {
        avaaSanktiotJaBonukset(testiurakka1, evk);
        avaaUusiArvonvahennys();

        kirjoitaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus2);
        kirjoitaTekstikenttaan('Perustelu', testiArvonvahennysPerustelu2);

        // Oletuksena "Ei vaikuta tavoitehintaan" on valittuna (uusi-arvonvahennys -> :false)
        valitseRadio('Ei vaikuta tavoitehintaan');

        // Kun ei vaikuta tavoitehintaan: ei tavoitehinnan alennusta, ei tehtäväryhmää/tehtävää
        cy.get(SP).contains('.form-group', 'Tavoitehinnan alennus').should('not.exist');
        cy.get(SP).contains('.form-group', 'Tehtäväryhmä').should('not.exist');
        cy.get(SP).contains('.form-group', 'Tehtävä').should('not.exist');
        // Näkyviin tulee Kulun kohdistus
        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('exist');

        kirjoitaInputkenttaan('Vähennyksen määrä', '120');
        valitseEnsimmainenAlasvetoarvo('Kulun kohdistus');

        valitsePvm('Havaittu', havaittuPvm);
        valitsePvm('Määrätty', maarattyPvm);

        valitseRadio('Työmaakokous');
        valitseAlasvetoarvo('Käsittelytapa', 'Välikatselmus');

        tallennaLomake();

        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus2).should('exist');

        // Avaa ja muokkaa
        avaaTallennettu(testiArvonvahennysKuvaus2);
        cy.get(SP).contains(testiArvonvahennysKuvaus2).should('exist');
        cy.get(SP).contains('Ei vaikuta tavoitehintaan').should('exist');

        siirryMuokkaustilaan();
        kirjoitaInputkenttaan('Vähennyksen määrä', '99');
        tallennaLomake();

        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus2).should('exist');
    });
});

// --- Testit: MHU24-urakka (Suomussalmi) vuonna 2026 ---

describe('Arvonvähennykset - MHU24-urakka (Suomussalmi), validointi pois käytöstä', () => {

    before(() => {
        siivoaKanta(testiArvonvahennysKuvaus3);
        // Otetaan MHU24-tarkistus pois käytöstä.
        asetaArvonvahennysValidointiKayttoon(false);
    });

    after(() => {
        siivoaKanta(testiArvonvahennysKuvaus3);
        // Palautetaan asetus testin jälkeen oletustilaan, ettei testi jätä ympäristöä muutettuun tilaan.
        asetaArvonvahennysValidointiKayttoon(true);
    });

    it('Lomakkeella näkyy tavoitehinta-valinta, mutta ei tehtäväryhmä/tehtävä -valikoita', () => {
        avaaSanktiotJaBonukset(testiurakka2, evk2);
        avaaUusiArvonvahennys();

        // Kun arvonvähennyslomakkeen MHU24-tarkistus on otettu pois käytöstä,
        // tavoitehinnan valinta näkyy myös MHU24-urakalla.
        cy.get(SP).contains('label', 'Vaikuttaa tavoitehintaan').should('exist');
        cy.get(SP).contains('label', 'Ei vaikuta tavoitehintaan').should('exist');
        valitseRadio('Vaikuttaa tavoitehintaan');

        // MHU24-urakalla ei silti näytetä tehtäväryhmää eikä tehtävää,
        // vaan Kulun kohdistus ja Laskutuskuukausi ovat näkyvissä.
        cy.get(SP).contains('.form-group', 'Tehtäväryhmä').should('not.exist');
        cy.get(SP).contains('.form-group', 'Tehtävä').should('not.exist');
        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('exist');
        cy.get(SP).contains('.form-group', 'Laskutuskuukausi').should('exist');
        cy.get(SP).contains('.form-group', 'Tavoitehinnan alennus').should('exist');

        // Täytetään lomake
        kirjoitaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus3);
        kirjoitaTekstikenttaan('Perustelu', testiArvonvahennysPerustelu3);
        kirjoitaInputkenttaan('Tavoitehinnan alennus', '80');
        kirjoitaInputkenttaan('Vähennyksen määrä', '110');
        valitseEnsimmainenAlasvetoarvo('Kulun kohdistus');

        valitsePvm('Havaittu', havaittuPvm);
        valitsePvm('Käsitelty', maarattyPvm);

        // Määräystapa on MHU24-urakalla alasvetovalikko
        valitseEnsimmainenAlasvetoarvo('Määräystapa');

        tallennaLomake();
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus3).should('exist');

        // Avaa ja muokkaa
        avaaTallennettu(testiArvonvahennysKuvaus3);
        cy.get(SP).contains(testiArvonvahennysKuvaus3).should('exist');

        siirryMuokkaustilaan();
        // Myös muokkaustilassa tavoitehinta-radio näkyy, kun MHU24-tarkistus on pois käytöstä.
        cy.get(SP).contains('label', 'Vaikuttaa tavoitehintaan').should('exist');
        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('exist');
        cy.get(SP).contains('.form-group', 'Laskutuskuukausi').should('exist');

        kirjoitaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus3 + ' muokattu');
        tallennaLomake();
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus3 + ' muokattu').should('exist');
    });


});

describe('Arvonvähennykset - MHU24-urakka (Suomussalmi), validointi käytössä', () => {

    before(() => {
        siivoaKanta(testiArvonvahennysKuvaus3);
        // Otetaan MHU24-tarkistus käyttöön
        asetaArvonvahennysValidointiKayttoon(true);
    });

    after(() => {
        siivoaKanta(testiArvonvahennysKuvaus3);
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
        cy.get(SP).contains('label', 'Vaikuttaa tavoitehintaan').should('exist');
        cy.get(SP).contains('label', 'Ei vaikuta tavoitehintaan').should('exist');
        valitseRadio('Vaikuttaa tavoitehintaan');

        // MHU24-urakalla ei silti näytetä tehtäväryhmää eikä tehtävää,
        // vaan Kulun kohdistus ja Laskutuskuukausi ovat näkyvissä.
        cy.get(SP).contains('.form-group', 'Tehtäväryhmä').should('not.exist');
        cy.get(SP).contains('.form-group', 'Tehtävä').should('not.exist');
        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('exist');
        cy.get(SP).contains('.form-group', 'Laskutuskuukausi').should('exist');
        cy.get(SP).contains('.form-group', 'Tavoitehinnan alennus').should('exist');

        // Täytetään lomake
        kirjoitaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus3);
        kirjoitaTekstikenttaan('Perustelu', testiArvonvahennysPerustelu3);
        kirjoitaInputkenttaan('Tavoitehinnan alennus', '80');
        kirjoitaInputkenttaan('Vähennyksen määrä', '110');
        valitseEnsimmainenAlasvetoarvo('Kulun kohdistus');

        valitsePvm('Havaittu', havaittuPvm);
        valitsePvm('Käsitelty', maarattyPvm);

        // Määräystapa on MHU24-urakalla alasvetovalikko
        valitseEnsimmainenAlasvetoarvo('Määräystapa');

        tallennaLomake();
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus3).should('exist');

        // Avaa ja muokkaa
        avaaTallennettu(testiArvonvahennysKuvaus3);
        cy.get(SP).contains(testiArvonvahennysKuvaus3).should('exist');

        siirryMuokkaustilaan();
        // Myös muokkaustilassa tavoitehinta-radio näkyy, kun MHU24-tarkistus on pois käytöstä.
        cy.get(SP).contains('label', 'Vaikuttaa tavoitehintaan').should('exist');
        cy.get(SP).contains('.form-group', 'Kulun kohdistus').should('exist');
        cy.get(SP).contains('.form-group', 'Laskutuskuukausi').should('exist');

        kirjoitaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus3 + ' muokattu');
        tallennaLomake();
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus3 + ' muokattu').should('exist');
    });


});



