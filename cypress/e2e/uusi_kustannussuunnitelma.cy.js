import * as ks from '../support/kustannussuunnitelmaFns.js';
import transit from "transit-js";
import {avaaKustannussuunnittelu, avaaUusiKustannussuunnittelu} from "../support/kustannussuunnitelmaFns.js";

// Täytetään ajax kutsun vastauksen perusteella
const indeksit = [];

function alustaIvalonUrakka() {
    ks.alustaKanta('Ivalon MHU testiurakka (uusi)');
}

function tarkistaToimenpideLuvut(toimenpide, luku1, luku2) {
    cy.get('#kilpailutettavat-hankinnat-elementti table.grid')
        .contains('td', toimenpide)
        .closest('tr')
        .find('input').eq(0)
        .should('have.value', luku1);
    cy.get('#kilpailutettavat-hankinnat-elementti table.grid')
        .contains('td', toimenpide)
        .closest('tr')
        .find('input').eq(1)
        .should('have.value', luku2);
}

function tarkistaRahavarausLuvut(rahavaraus, luku1, luku2) {
    cy.get('#rahavaraukset-elementti table.grid')
        .contains('td', rahavaraus)
        .closest('tr')
        .find('td').eq(1)
        .contains(luku1);

    // Indeksikorjattu luku
    cy.get('#rahavaraukset-elementti table.grid')
        .contains('td', rahavaraus)
        .closest('tr')
        .find('td').eq(2)
        .contains(luku2);
}

function tarkistaErillishankinnanLuvut(kuukausi, luku1, luku2) {
    cy.get('#erillishankinnat-elementti table.grid')
        .contains('td', kuukausi)
        .closest('tr')
        .find('input').eq(0)
        .should('have.value', luku1);
    cy.get('#erillishankinnat-elementti table.grid')
        .contains('td', kuukausi)
        .closest('tr')
        .find('td').eq(2)
        .contains(luku2);
}

describe('Tavoitehintaiset rahavaraukset osio', function () {

    before(function () {
        alustaIvalonUrakka();
        avaaUusiKustannussuunnittelu('Ivalon MHU testiurakka (uusi)', 'Lappi');
    })

    describe('Testaa kilpailutetttavat hankinnat', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-kilpailutettavat-hankinnat').as('tallenna-kilpailutettavat-hankinnat');
        });

        xit('Taulukon arvot alussa oikein', function () {

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('th').contains('Toimenpide');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('th').contains('Pysyvät muutokset (€)');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('th').contains('Loka-joulukuu 2024 (€)');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('th').contains('Tammi-syyskuu 2025 (€)');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('th').contains('Yhteensä (€)');

            // Varmistetaan, että taulukosta löytyy toimenpiteitä
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('td').contains('Talvihoito laaja TPI');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('td').contains('Liikenneympäristön hoito laaja TPI');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('td').contains('Soratien hoito laaja TPI');

            // Löytyyhän lukuja
            tarkistaToimenpideLuvut('Talvihoito laaja TPI', '0,00', '0,00');
            tarkistaToimenpideLuvut('Liikenneympäristön hoito laaja TPI', '0,00', '0,00');
            tarkistaToimenpideLuvut('Soratien hoito laaja TPI', '0,00', '0,00');
        });

        xit('Muokkaa kilpailutettavat hankinnat', function () {
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').gridOtsikot().then(() => {

                let valitseInput = function (rivi) {
                    return `#kilpailutettavat-hankinnat-elementti table.grid tbody tr:nth-child(${rivi + 1}) td input`;
                };

                // Muokataan Talvihoito laaja TPI
                cy.get(valitseInput(1)).eq(0).clear().type('1000');
                cy.get(valitseInput(1)).eq(1).clear().type('2000');
                cy.get('#kilpailutettavat-hankinnat-elementti table.grid tr:nth-child(2) td').eq(4).contains('3 000,00');

                // Muokataan Liikenneympäristön hoito laaja TPI
                cy.get(valitseInput(2)).eq(0).clear().type('1500');
                cy.get(valitseInput(2)).eq(1).clear().type('2500');
                cy.get('#kilpailutettavat-hankinnat-elementti table.grid tr:nth-child(3) td').eq(4).contains('4 000,00');

                // Muokataan Soratien hoito laaja TPI
                cy.get(valitseInput(3)).eq(0).clear().type('2000');
                cy.get(valitseInput(3)).eq(1).clear().type('3000');
                cy.get('#kilpailutettavat-hankinnat-elementti table.grid tr:nth-child(4) td').eq(4).contains('5 000,00');

                // Kirjaamatta teksti näkyy vielä
                cy.get('#kilpailutettavat-hankinnat-elementti table.grid tr:nth-child(9) td').contains('Kirjaamatta');

            });

            // Tallennetaan muutokset
            cy.get('#kilpailutettavat-hankinnat-elementti').contains('Tallenna tiedot').click();
            cy.wait('@tallenna-kilpailutettavat-hankinnat')
              .its('response.statusCode')
              .should('equal', 200);

        });

    });

    describe('Testaa rahavaraukset', function () {
        xit('Taulukon arvot alussa oikein', function () {

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#rahavaraukset-elementti table.grid').find('th').contains('Rahavaraus');
            cy.get('#rahavaraukset-elementti table.grid').find('th').contains('Suunniteltu kustannus (€)');
            cy.get('#rahavaraukset-elementti table.grid').find('th').contains('Indeksikorjattu (€)');

            // Varmistetaan, että taulukosta löytyy toimenpiteitä
            cy.get('#rahavaraukset-elementti table.grid').find('td').contains('Tilaajan rahavaraus kannustinjärjestelmään');
            cy.get('#rahavaraukset-elementti table.grid').find('td').contains('Äkilliset hoitotyöt');
            cy.get('#rahavaraukset-elementti table.grid').find('td').contains('Vahinkojen korjaukset');

            // Löytyyhän lukuja
            tarkistaRahavarausLuvut('Tilaajan rahavaraus kannustinjärjestelmään', '0,00', '-');
            tarkistaRahavarausLuvut('Äkilliset hoitotyöt', '0,00', '-');
            tarkistaRahavarausLuvut('Vahinkojen korjaukset', '0,00', '-');
        });
    });

    describe('Testaa erillishankinnat', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-erillishankinnat').as('tallenna-erillishankinnat');
        });

        it('Taulukon arvot alussa oikein', function () {

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#erillishankinnat-elementti table.grid').find('th').contains('Kalenterikuukausi');
            cy.get('#erillishankinnat-elementti table.grid').find('th').contains('Suunniteltu kustannus (€)');
            cy.get('#erillishankinnat-elementti table.grid').find('th').contains('Indeksikorjattu (€)');

            // Varmistetaan, että taulukosta löytyy toimenpiteitä
            cy.get('#erillishankinnat-elementti table.grid').find('td').contains('Lokakuu 2024');
            cy.get('#erillishankinnat-elementti table.grid').find('td').contains('Marraskuu 2024');
            cy.get('#erillishankinnat-elementti table.grid').find('td').contains('Joulukuu 2024');

            // Löytyyhän lukuja
            tarkistaErillishankinnanLuvut('Lokakuu 2024', '0,00', '-');
            tarkistaErillishankinnanLuvut('Marraskuu 2024', '0,00', '-');
            tarkistaErillishankinnanLuvut('Joulukuu 2024', '0,00', '-');
        });

        it('Muokkaa erillishankinnat', function () {
            cy.get('#erillishankinnat-elementti table.grid').gridOtsikot().then(() => {

                let valitseInput = function (rivi) {
                    return `#erillishankinnat-elementti table.grid tbody tr:nth-child(${rivi + 1}) td input`;
                };

                // Muokataan Lokakuu 2024
                cy.get(valitseInput(1)).eq(0).clear().type('1000');

                // Muokataan Marraskuu 2024
                cy.get(valitseInput(2)).eq(0).clear().type('1500');

                // Muokataan Joulukuu 2024
                cy.get(valitseInput(3)).eq(0).clear().type('2000');

                // Kirjaamatta teksti näkyy vielä
                cy.get('#erillishankinnat-elementti table.grid tr:nth-child(14) td').contains('Kirjaamatta');

            });

            // Tallennetaan muutokset
            cy.get('#erillishankinnat-elementti').contains('Tallenna tiedot').click();
            cy.wait('@tallenna-erillishankinnat')
                .its('response.statusCode')
                .should('equal', 200);

        });

    });

});
