import * as ks from '../support/kustannussuunnitelmaFns.js';
import {avaaHarjaTimeoutilla} from '../support/apurit.js';

function valitseVesivaylaurakka(urakkaNimi) {
    avaaHarjaTimeoutilla();

    // Valitse urakkatyyppi
    cy.get('[data-cy="murupolku-urakkatyyppi"]')
        .find('button')
        .click();

    cy.get('[data-cy="murupolku-urakkatyyppi"]')
        .contains('ul li a', 'Vesiväylät ja kanavat')
        .should('be.visible')
        .click({force: true});

    // Valitse elinvoimakeskus
    cy.get('#alasveto-hallintayksikko')
        .find('button')
        .click();

    cy.get('#alasveto-hallintayksikko')
        .contains('ul li a', 'Sisävesiväylät')
        .should('be.visible')
        .click({force: true});

    cy.get('.ajax-loader', {timeout: 10000})
        .should('not.exist');

    cy.contains('Näytä päättyneet')
        .click();

    cy.contains(
        '[data-cy=urakat-valitse-urakka] li',
        urakkaNimi,
        {timeout: 10000}
    ).click();
}

function alustaRentoselanUrakka() {
    ks.alustaKanta('Rentoselän urakka');
}

describe('Vesiväyläurakan Kulut-näkymä', function () {
    before(function () {
        alustaRentoselanUrakka();

        // Valitsee urakkatyypin, hallintayksikön ja urakan
        valitseVesivaylaurakka('Rentoselän urakka');

        cy.get('[data-cy="tabs-taso1-Kulut"]', {timeout: 20000})
            .should('be.visible')
            .click();

        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000})
            .should('not.exist');
    });

    it('Kulut-näkymässä urakkavuosi-teksti', function () {
        cy.contains('h1', 'Kulujen kohdistus')
            .should('be.visible');

        cy.get('[data-cy="kulut-vuositeksti"]')
            .should('be.visible')
            .and('contain.text', 'Urakkavuosi')
            .and('not.contain.text', 'Hoitovuosi');

        cy.get('[data-cy=hoitokausi-valinta]').click();
        cy.wait(250);
        cy.get('[data-cy=hoitokausi-valinta]')
            .find('ul.livi-alasvetolista li')
            .contains('urakkavuosi')
            .should('exist');
    });

});
