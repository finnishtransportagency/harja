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

function valitseRentoselanVesivaylaurakka() {
    alustaRentoselanUrakka();
    valitseVesivaylaurakka('Rentoselän urakka');
}

describe('Vesiväyläurakan Yleiset-näkymä', function () {
    before(function () {
        valitseRentoselanVesivaylaurakka();

        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000})
            .should('not.exist');
    });

    it('Yleiset-näkymä aukeaa vesiväyläurakalle', function () {
        cy.contains('h1', 'Yleiset tiedot')
            .should('be.visible');
    });
    it('Yleiset-näkymässä ei näytetä urakan kesäaikaa', function () {
        cy.contains('Urakan kesäaika:')
            .should('not.exist');
    });
    it('Yleiset-näkymässä ei näytetä Urakoitsijan alukset-otsikkoa', function () {
        cy.contains('h6', /^Urakoitsijan alukset$/)
            .should('not.exist');
    });
});

describe('Vesiväyläurakan Tarjouksen tiedot -näkymä', function () {
    before(function () {
        valitseRentoselanVesivaylaurakka();

        cy.get('[data-cy="tabs-taso1-Suunnittelu"]', {timeout: 20000})
            .should('be.visible')
            .click();

        cy.get('[data-cy="tabs-taso2-Tarjouksen tiedot"]', {timeout: 20000})
            .should('be.visible')
            .click();

        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000})
            .should('not.exist');
    });

    it('Tarjouksen tiedot -näkymä näyttää vesiväyläurakan hankinnat', function () {
        cy.contains('h1', 'Tarjouksen tiedot')
            .should('be.visible');

        cy.get('[data-cy="tarjous-hankinnat-grid"]')
            .should('be.visible')
            .within(() => {
                cy.contains('tbody tr', 'Urakan hankinnat')
                    .should('be.visible');

                cy.contains('tbody tr', 'Kilpailutettavat hankinnat')
                    .should('not.exist');
            });
    });

    it('Vesiväyläurakalla on kuusi johto- ja hallintokorvausriviä', function () {
        cy.get('[data-cy="tarjous-toimenkuvat-grid"]')
            .should('be.visible')
            .find('tbody tr')
            .then((rivit) => {
                const toimenkuvarivit = [...rivit].filter((rivi) =>
                    !rivi.innerText.includes('Johto- ja hallintokorvaus yhteensä')
                );

                expect(toimenkuvarivit).to.have.length(6);
            });
    });
});

describe('Vesiväyläurakan Kulut-näkymä', function () {
    before(function () {
        valitseRentoselanVesivaylaurakka();

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
