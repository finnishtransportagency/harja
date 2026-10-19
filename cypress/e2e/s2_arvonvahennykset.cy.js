// Arvonvähennysten E2E-testit
//
// Testataan arvonvähennysten lisäämistä MHU25-urakalle (Kajaani) ja sen
// näyttämistä eri näkymissä:
// 1. Välikatselmuksen yhteenveto
// 2. Laskutusyhteenveto (Työmaakokous ja Tuote-versiot)
// 3. Kustannusten Seuranta

import {
    clickTimeout,
    siivoaTietokannastaSanktiot,
    avaaSanktiotJaBonukset,
    avaaUusiArvonvahennys,
    kirjoitaSivupaneelissaTekstikenttaan,
    kirjoitaSivupaneelissaInputkenttaan,
    valitseSivupaneelissaEnsimmainenAlasvetoarvo,
    valitseLaskutusyhteenvedollaEnsimmainenAlasvetoarvo,
    valitseSivupaneelissaRadio,
    valitseSivupaneelissaPvm,
    tallennaArvonvahennyslomake
} from '../support/apurit';

// Asetuksia
let testiArvonvahennysKuvaus = "CY-arvonvahennys-talvihoidolle-1000";
let testiArvonvahennysPerustelu = "CY-arvonvähennys-talvihoidolle-testi";

let testiurakka = "POP MHU Kajaani 2025-2030"; // mhu25 urakka
let evk = "Pohjois-Suomi";

// Päivämäärät - Kajaanin urakan ensimmäisen hoitovuoden sisällä
let havaittuPvm = "01.03.2026";
let maarattyPvm = "15.03.2026";

// Kajaanin testidatassa on ennestään 3400 euron arvonvähennykset.
let odotettuArvonvahennystenSumma = "-4 400,00";

function valitseHoitovuosi(hoitovuosi) {
    cy.get('div.label-ja-alasveto.hoitokausi div.dropdown').eq(0).within(() => {
        cy.get('button').click({force: true});
    });
    cy.contains('li', `${hoitovuosi}. hoitovuosi`).click();
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist');
}

function valitseRaportinHoitovuosi() {
    cy.get('[data-cy=hoitokausi-valinta]').click();
    cy.contains('li', '1. hoitovuosi').click();
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist');
}


// --- Testit ---

describe('Arvonvähennysten näyttäminen eri näkymissä', () => {

    before(() => {
        siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus);
    });

    after(() => {
        siivoaTietokannastaSanktiot(testiArvonvahennysKuvaus);
    });

    it('Lisää arvonvähennys 1000€ talvihoitoon MHU25-urakalle', () => {
        avaaSanktiotJaBonukset(testiurakka, evk);
        valitseHoitovuosi(1);
        avaaUusiArvonvahennys();

        // Perustiedot
        kirjoitaSivupaneelissaTekstikenttaan('Tapahtumapaikka/kuvaus', testiArvonvahennysKuvaus);
        kirjoitaSivupaneelissaTekstikenttaan('Perustelu', testiArvonvahennysPerustelu);

        // Arvonvähennys: 1000 €
        kirjoitaSivupaneelissaInputkenttaan('Arvonvähennys', '1000');

        // Tehtäväryhmän valinta laukaisee tehtävien haun
        cy.intercept('POST', '_/hae-tehtavaryhman-tehtavat-urakalle').as('haeTehtavat');
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Tehtäväryhmä');
        cy.wait('@haeTehtavat', {timeout: clickTimeout});

        // Valitaan tehtävä. Tämän pitää olla talvihoidosta liittyvä,
        // joten varmistetaan että valitaan oikea tehtävä
        // Ensimmäinen tehtävä on oletettavasti talvihoito-tehtävä tässä urakan testidatassa
        valitseSivupaneelissaEnsimmainenAlasvetoarvo('Tehtävä');

        // Päivämäärät
        valitseSivupaneelissaPvm('Havaittu', havaittuPvm);
        valitseSivupaneelissaPvm('Määrätty', maarattyPvm);

        // Määräystapa ja käsittelytapa
        valitseSivupaneelissaRadio('Työmaakokous');

        tallennaArvonvahennyslomake();

        // Tallennus näkyy listalla
        cy.get('.sanktiot').contains('td', testiArvonvahennysKuvaus).should('exist');
        cy.get('table.grid').should('contain', testiArvonvahennysPerustelu);
        cy.get('table.grid').should('contain', '-1000');
    });

    it('Varmista, että arvonvähennys näkyy välikatselmuksen yhteevedossa', () => {
        avaaSanktiotJaBonukset(testiurakka, evk);
        valitseHoitovuosi(1);

        // Avaa Välikatselmus
        cy.intercept('POST', 'hae-valikatselmuksen-tiedot-hoitovuodelle').as('hae-valikatselmus')
        cy.get('[data-cy=tabs-taso1-Valikatselmus]').click()
        cy.wait('@hae-valikatselmus', {timeout: clickTimeout})
        cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
        valitseRaportinHoitovuosi();

        // Tarkista että arvonvähennys näkyy yhteevedossa
        // Yhteenveto sisältää sanktiot/arvonvähennykset summassa.
        // Välikatselmuksessa näytetään teksti "Arvonvähennykset" johon niputetaan kaikki arvonvähennykset,
        // joten haetaan "Arvonvähennykset"-teksti ja varmistetaan summa on sen yhteydessä.
        cy.contains('Arvonvähennykset').should('exist');
        cy.contains('Arvonvähennykset')
            .parent()
            .invoke('text')
            .then((teksti) => {
                let normalisoitu = teksti
                    .replace(/\u00a0/g, ' ')
                    .replace(/\u2212/g, '-');
                expect(normalisoitu).to.contain(odotettuArvonvahennystenSumma);
            });
    });

    it('Varmista, että arvonvähennys näkyy laskutusyhteevedossa (Työmaakokous-versio)', () => {
        avaaSanktiotJaBonukset(testiurakka, evk);
        valitseHoitovuosi(1);

        // Avaa Laskutus -> Laskutusyhteenveto
        cy.get('[data-cy=tabs-taso1-Kulut]').click()
        cy.get('[data-cy="tabs-taso2-Laskutusyhteenveto"]').click()
        cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')

        // Valitse Työmaakokous-versio jos saatavilla
        cy.get('body').then(($body) => {
            if ($body.find('[data-cy="laskutusyhteenveto-versio-tyomaa"]').length > 0) {
                cy.get('[data-cy="laskutusyhteenveto-versio-tyomaa"]').click()
                cy.wait(500)
            }
        })

        // Valitse koko hoitokausi
        valitseLaskutusyhteenvedollaEnsimmainenAlasvetoarvo('Kuukausi');

        // Tarkista että arvonvähennys näkyy
        // Laskutusyhteevedossa näytetään "Arvonvähennykset"-teksti ja summa samassa solussa
        cy.contains('td', 'Arvonvähennykset').should('exist');
        cy.contains('td', 'Arvonvähennykset')
            .parent()
            .invoke('text')
            .then((teksti) => {
                let normalisoitu = teksti
                    .replace(/\u00a0/g, ' ')
                    .replace(/\u2212/g, '-');
                expect(normalisoitu).to.contain(odotettuArvonvahennystenSumma);
            });
    });

    it('Varmista, että arvonvähennys näkyy laskutusyhteevedossa (Tuote-versio)', () => {
        avaaSanktiotJaBonukset(testiurakka, evk);
        valitseHoitovuosi(1);

        // Avaa Laskutus -> Laskutusyhteenveto
        cy.get('[data-cy=tabs-taso1-Kulut]').click()
        cy.get('[data-cy="tabs-taso2-Laskutusyhteenveto"]').click()
        cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')

        // Valitse Tuote-versio jos saatavilla
        cy.get('body').then(($body) => {
            if ($body.find('[data-cy="laskutusyhteenveto-versio-tuote"]').length > 0) {
                cy.get('[data-cy="laskutusyhteenveto-versio-tuote"]').click()
                cy.wait(500)
            }
        })

        // Valitse koko hoitokausi
        valitseLaskutusyhteenvedollaEnsimmainenAlasvetoarvo('Kuukausi');

        // Tarkista että arvonvähennys näkyy
        // Laskutusyhteevedossa näytetään "Arvonvähennykset"-teksti ja summa samassa solussa
        cy.contains('td', 'Arvonvähennykset').should('exist');
        cy.contains('td', 'Arvonvähennykset')
            .parent()
            .invoke('text')
            .then((teksti) => {
                let normalisoitu = teksti
                    .replace(/\u00a0/g, ' ')
                    .replace(/\u2212/g, '-');
                expect(normalisoitu).to.contain(odotettuArvonvahennystenSumma);
            });
    });

    it('Varmista, että arvonvähennys näkyy Kustannusten Seuranta -sivulla', () => {
        avaaSanktiotJaBonukset(testiurakka, evk);
        valitseHoitovuosi(1);

        // Avaa Kulut -> Kustannusten seuranta
        cy.intercept('POST', 'urakan-kustannusten-seuranta-paaryhmittain').as('hae-kustannukset')
        cy.get('[data-cy=tabs-taso1-Kulut]').click()
        cy.get('[data-cy="tabs-taso2-Kustannusten seuranta"]').click()
        cy.wait('@hae-kustannukset', {timeout: clickTimeout})
        cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
        valitseRaportinHoitovuosi();

        // Tarkista että arvonvähennys näkyy (voi näkyä sanktiot-taulukossa tai yhtevedossa)
        cy.contains('tr', 'Arvonvähennykset')
            .should('exist')
            .invoke('text')
            .then((teksti) => {
                let normalisoitu = teksti
                    .replace(/\u00a0/g, ' ')
                    .replace(/\u2212/g, '-');
                expect(normalisoitu).to.contain(odotettuArvonvahennystenSumma);
            });
    });
});
