import {avaaKustannussuunnittelu} from "../support/kustannussuunnitelmaFns.js";
import * as ks from "../support/kustannussuunnitelmaFns.js";
import {kuluvaHoitokausiAlkuvuosi} from "../support/apurit";

const indeksit = [];

function alustaSuomussalmenUrakka() {
    ks.alustaKanta('POP MHU Suomussalmi 2024-2029');
}

describe('Johto- & Hallintokorvaukset, 2024->', () => {
    before(function () {
        alustaSuomussalmenUrakka();
        avaaKustannussuunnittelu('POP MHU Suomussalmi 2024-2029', 'Pohjois-Pohjanmaa', indeksit);
    })

    describe('Vakiotoimenkuvat', () => {
        it('Vakiotoimenkuvat ovat oikein', () => {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset').as('tallenna-jhk')

            // Varmistetaan, että vuonna 2024 alkavilla urakoilla on kaikki niille määritellyt toimenkuvat
            cy.get('#toimenkuvat-taulukko').contains('Valmistelukausi ennen urakka-ajan alkua');
            cy.get('#toimenkuvat-taulukko').contains('Vastuunalainen työnjohtaja');
            cy.get('#toimenkuvat-taulukko').contains('2. työnjohtaja');
            cy.get('#toimenkuvat-taulukko').contains('3. työnjohtaja');
            cy.get('#toimenkuvat-taulukko').contains('Viherhoidosta vastaava henkilö');
            cy.get('#toimenkuvat-taulukko').contains('Harjoittelija');
        })

        it('Vakiotoimenkuvien summia voi syöttää kuukausille erikseen', () => {
            cy.get('#toimenkuvat-taulukko')
                .contains('2. työnjohtaja')
                .next().click()
            cy.get('#toimenkuvat-taulukko')
                .contains('2. työnjohtaja')
                .parent()
                .next()
                .contains('Suunnittele maksuerät kuukausittain')
                .click()
            cy.get('#toimenkuvat-taulukko')
                .contains('2. työnjohtaja')
                .parent()
                .next()
                .contains('Lokakuu 2024')
                .next()
                .find('input')
                .clear()
                .type('100')
                .blur()
            cy.intercept('POST', '_/tallenna-budjettitavoite')
                .as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset')
                .as('tallenna-jhk')
            cy.wait('@tallenna-jhk')
                .its('response.statusCode')
                .should('equal', 200)
            cy.wait('@tallenna-budjettitavoite')
                .its('response.statusCode')
                .should('equal', 200)
        })
    })

    describe('Omat toimenkuvat', () => {
        it('Omien toimenkuvien nimet voi vaihtaa', () => {
            cy.intercept('POST', '_/tallenna-toimenkuva').as('tallenna-toimenkuva');

            cy.get('#toimenkuvat-taulukko')
                .contains('Harjoittelija')
                .parent()
                .next().next()
                .find('input')
                .first()
                .clear()
                .type('4. työnjohtaja')
                .blur()
            cy.get('#toimenkuvat-taulukko')
                .contains('Harjoittelija')
                .parent()
                .next().next()
                .next().next()
                .find('input')
                .first()
                .clear()
                .type('5. työnjohtaja')
                .blur()

            cy.wait('@tallenna-toimenkuva')
                .its('response.statusCode').should('equal', 200)
        })
        it('Omien toimenkuvien vuosisummat voi syöttää', () => {
            cy.intercept('POST', '_/tallenna-toimenkuva').as('tallenna-toimenkuva');
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset').as('tallenna-jhk')

            cy.get('#toimenkuvat-taulukko')
                .contains('Harjoittelija')
                .parent()
                .next().next()
                .find('td')
                .first()
                .next().next()
                .find('input')
                .clear()
                .type('1200')
                .blur()
            cy.get('#toimenkuvat-taulukko')
                .contains('Harjoittelija')
                .parent()
                .next().next()
                .next().next()
                .find('td')
                .first()
                .next().next()
                .find('input')
                .clear()
                .type('1200')
                .blur()

            cy.wait('@tallenna-toimenkuva')
                .its('response.statusCode').should('equal', 200)
            cy.wait('@tallenna-jhk')
                .its('response.statusCode').should('equal', 200)
            cy.wait('@tallenna-budjettitavoite')
                .its('response.statusCode').should('equal', 200)
        })
        it('Omien toimenkuvien vuosisummia voi muokata', () => {
            cy.intercept('POST', '_/tallenna-toimenkuva').as('tallenna-toimenkuva');
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset').as('tallenna-jhk')

            cy.get('#toimenkuvat-taulukko')
                .contains('Harjoittelija')
                .parent()
                .next().next()
                .find('td')
                .first()
                .next().next()
                .find('input')
                .clear()
                .type('1400')
                .blur()
            cy.get('#toimenkuvat-taulukko')
                .contains('Harjoittelija')
                .parent()
                .next().next()
                .next().next()
                .find('td')
                .first()
                .next().next()
                .find('input')
                .clear()
                .type('1400')
                .blur()

            cy.wait('@tallenna-toimenkuva')
                .its('response.statusCode').should('equal', 200)
            cy.wait('@tallenna-jhk')
                .its('response.statusCode').should('equal', 200)
            cy.wait('@tallenna-budjettitavoite')
                .its('response.statusCode').should('equal', 200)
        })
    })

})
