import {avaaHarjaTimeoutilla} from "../support/apurit.js";

// Suunnittelu / Kalustoresurssit -alasivun Cypress-testit.
//
// Kalustoresurssit-välilehti näkyy vain MHU26-urakoille (alkupvm vuosi >= 2026).
// Positiivisessa polussa käytetään testidataan lisättyä "Kittilän MHU 2026-2031" -urakkaa.
// Negatiivisessa polussa käytetään vanhempaa "Kittilän MHU 2019-2024" -urakkaa.

const MHU26_URAKKA = 'Kittilän MHU 2026-2031';
const VANHA_URAKKA = 'Kittilän MHU 2019-2024';
const ALUE = 'Lappi';

function tyhjennaKalustoresurssit(urakkaNimi) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM suunnittelu_kalustoresurssi " +
            `WHERE urakka_id = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Tyhjennä kalustoresurssit - tulos:", tulos);
            });
    });
}

function avaaUrakanSuunnittelu(alue, urakkaNimi) {
    avaaHarjaTimeoutilla();

    cy.contains('.haku-lista-item', alue, {timeout: 30000}).click();
    cy.get('.ajax-loader', {timeout: 10000}).should('not.exist');
    cy.contains('Näytä päättyneet').click();

    cy.contains('[data-cy=urakat-valitse-urakka] li', urakkaNimi, {timeout: 10000}).click();
    cy.get('[data-cy=tabs-taso1-Suunnittelu]', {timeout: 20000}).click();
    cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');
}

describe('Suunnittelu / Kalustoresurssit', function () {

    it('Kalustoresurssit-välilehti ei näy vanhemmalle MHU-urakalle', function () {
        avaaUrakanSuunnittelu(ALUE, VANHA_URAKKA);

        // Vanhemmalla MHU-urakalla näkyy suunnittelun perustabuja,
        // mutta Tarjouksen tiedot (>= 2025) ja Kalustoresurssit (>= 2026) eivät näy.
        cy.get('[data-cy=tabs-taso2-Suolarajoitukset]', {timeout: 20000}).should('exist');
        cy.get('[data-cy="tabs-taso2-Tarjouksen tiedot"]').should('not.exist');
        cy.get('[data-cy=tabs-taso2-Kalustoresurssit]').should('not.exist');
    });

    it('MHU26-urakka voi tallentaa, tarkastella ja peruuttaa kalustoresurssit', function () {
        tyhjennaKalustoresurssit(MHU26_URAKKA);

        avaaUrakanSuunnittelu(ALUE, MHU26_URAKKA);

        // Avaa Kalustoresurssit-välilehti
        cy.get('[data-cy=tabs-taso2-Kalustoresurssit]', {timeout: 20000}).click();
        cy.get('[data-cy=kalustoresurssit]', {timeout: 20000}).should('be.visible');

        // Syötä määrät hoitoluokkaryhmille (ensimmäinen tallennus, editointitila heti auki)
        cy.get('[data-cy="kalustoresurssi-maara-ise-ib"]').clear().type('5');
        cy.get('[data-cy="kalustoresurssi-maara-ic-iii"]').clear().type('10');
        cy.get('[data-cy="kalustoresurssi-maara-k1-k2-l"]').clear().type('3');

        // Tallenna - tallennuksen jälkeen siirrytään luku-tilaan ja Muokkaa-nappi ilmestyy
        cy.get('[data-cy=kalustoresurssit-tallenna]').click();
        cy.get('[data-cy=kalustoresurssit-muokkaa]', {timeout: 20000}).should('be.visible');

        // Luku-tilassa kentät näkyvät tekstinä taulukossa ja Tallenna/Peruuta piilotettu
        cy.get('[data-cy=kalustoresurssit-taulukko]').contains('5');
        cy.get('[data-cy=kalustoresurssit-taulukko]').contains('10');
        cy.get('[data-cy=kalustoresurssit-taulukko]').contains('3');
        cy.get('[data-cy=kalustoresurssit-tallenna]').should('not.exist');
        cy.get('[data-cy=kalustoresurssit-peruuta]').should('not.exist');

        // Muokkaa-nappia klikkaamalla siirrytään takaisin editointitilaan
        cy.get('[data-cy=kalustoresurssit-muokkaa]').click();
        cy.get('[data-cy="kalustoresurssi-maara-ise-ib"]').should('not.be.disabled');
        cy.get('[data-cy=kalustoresurssit-tallenna]').should('exist');

        // Muuta arvoa ja peruuta - palaa tallennettuun tilaan
        cy.get('[data-cy="kalustoresurssi-maara-ise-ib"]').clear().type('99');
        cy.get('[data-cy=kalustoresurssit-peruuta]').click();

        // Peruutuksen jälkeen palataan luku-tilaan ja Muokkaa-nappi on taas näkyvissä
        cy.get('[data-cy=kalustoresurssit-muokkaa]', {timeout: 20000}).should('be.visible');
        cy.get('[data-cy=kalustoresurssit-taulukko]').contains('5');
    });
});
