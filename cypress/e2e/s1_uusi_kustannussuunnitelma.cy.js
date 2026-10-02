import * as ks from '../support/kustannussuunnitelmaFns.js';
import transit from "transit-js";
import {avaaKustannussuunnittelu, avaaUusiKustannussuunnittelu} from "../support/kustannussuunnitelmaFns.js";

// Täytetään ajax kutsun vastauksen perusteella
const indeksit = [];

function alustaSuomussalmenUrakka() {
    ks.alustaKanta('KOPIO POP MHU Suomussalmi 2024-2029');
}
function alustaIinUrakka() {
    ks.alustaKanta('Iin MHU 2021-2026');
}
function alustaKajaanin25Urakka() {
    ks.alustaKanta('POP MHU Kajaani 2025-2030');
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

function tarkistaRahavarausLuvut(rahavaraus, luku1, luku2, luku3,) {
    cy.get('#rahavaraukset-elementti table.grid')
        .contains('td', rahavaraus)
        .closest('tr')
        .find('td').eq(1)
        .contains(luku1);
    // Tyhjät arvot on parempi tarkistaa näin
    if(luku2 === '') {
       // Ei tarvitse tarkastaa mitään.
    } else {
        cy.get('#rahavaraukset-elementti table.grid')
            .contains('td', rahavaraus)
            .closest('tr')
            .find('td').eq(2)
            .contains(luku2);
    }
    // Indeksikorjattu luku
    cy.get('#rahavaraukset-elementti table.grid')
        .contains('td', rahavaraus)
        .closest('tr')
        .find('td').eq(3)
        .contains(luku3);
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

function tarkistaHoidonjotopalkkioLuvut(kuukausi, luku1, luku2) {
    cy.get('#hoidonjohtopalkkio-elementti table.grid')
        .contains('td', kuukausi)
        .closest('tr')
        .find('input').eq(0)
        .should('have.value', luku1);
    cy.get('#hoidonjohtopalkkio-elementti table.grid')
        .contains('td', kuukausi)
        .closest('tr')
        .find('td').eq(2)
        .contains(luku2);
}

function tarkistaToimenkuva2019Luvut(toimenkuva, tarjouksenmaara, tunnit, tuntipalkka, yhteensa, kkv) {
    if(tarjouksenmaara === '') {
        cy.get('#johto-ja-hallintokorvaus-elementti table.grid').contains('td', toimenkuva).closest('tr')
            .find('td').eq(2)
            .should('be.empty');
    } else {
        cy.get('#johto-ja-hallintokorvaus-elementti table.grid').contains('td', toimenkuva).closest('tr')
            .find('td').eq(2)
            .contains(tarjouksenmaara);
    }

    cy.get('#johto-ja-hallintokorvaus-elementti table.grid').contains('td', toimenkuva).closest('tr')
        .find('input').eq(0)
        .should('have.value', tunnit);
    cy.get('#johto-ja-hallintokorvaus-elementti table.grid').contains('td', toimenkuva).closest('tr')
        .find('input').eq(1)
        .should('have.value', tuntipalkka);
    cy.get('#johto-ja-hallintokorvaus-elementti table.grid').contains('td', toimenkuva).closest('tr')
        .find('td').eq(5)
        .contains(yhteensa);
    cy.get('#johto-ja-hallintokorvaus-elementti table.grid').contains('td', toimenkuva).closest('tr')
        .find('td').eq(6)
        .contains(kkv);
}

function tarkistaToimenkuva2022Luvut(toimenkuva, luku1, luku2) {
    cy.get('#johto-ja-hallintokorvaus-elementti table.grid')
        .contains('td', toimenkuva)
        .closest('tr')
        .find('input').eq(0)
        .should('have.value', luku1);
    cy.get('#johto-ja-hallintokorvaus-elementti table.grid')
        .contains('td', toimenkuva)
        .closest('tr')
        .find('td').eq(2)
        .contains(luku2);
}

function tarkistaToimenkuva2025Luvut(kuukausi, luku1, luku2) {
    cy.get('#johto-ja-hallintokorvaus-elementti table.grid')
        .contains('td', kuukausi)
        .closest('tr')
        .find('input').eq(0)
        .should('have.value', luku1);
    cy.get('#johto-ja-hallintokorvaus-elementti table.grid')
        .contains('td', kuukausi)
        .closest('tr')
        .find('td').eq(2)
        .contains(luku2);
}

describe('Tavoitehintaiset rahavaraukset osio', function () {

    before(function () {
        alustaSuomussalmenUrakka();
        alustaIinUrakka();
        alustaKajaanin25Urakka();
    })


    describe('Testaa erillishankinnat', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-erillishankinnat').as('tallenna-erillishankinnat');
            avaaUusiKustannussuunnittelu('POP MHU Kajaani 2025-2030', 'Pohjois-Suomi');

            // Valitse ensimmäinen hoitovuosi
            cy.get('div.label-ja-alasveto.hoitokausi div.dropdown').eq(0).within(() => {
                cy.get('button').click({force: true});
                cy.contains('1. hoitovuosi').click();
            });
        });

        it('Taulukon arvot alussa oikein', function () {

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#erillishankinnat-elementti table.grid').find('th').contains('Kalenterikuukausi');
            cy.get('#erillishankinnat-elementti table.grid').find('th').contains('Suunniteltu kustannus (€)');
            cy.get('#erillishankinnat-elementti table.grid').find('th').contains('Indeksikorjattu (€)');

            // Varmistetaan, että taulukosta löytyy toimenpiteitä
            cy.get('#erillishankinnat-elementti table.grid').find('td').contains('Lokakuu 2025');
            cy.get('#erillishankinnat-elementti table.grid').find('td').contains('Marraskuu 2025');
            cy.get('#erillishankinnat-elementti table.grid').find('td').contains('Joulukuu 2025');

            // Löytyyhän lukuja
            tarkistaErillishankinnanLuvut('Lokakuu 2025', '0,00', '-');
            tarkistaErillishankinnanLuvut('Marraskuu 2025', '0,00', '-');
            tarkistaErillishankinnanLuvut('Joulukuu 2025', '0,00', '-');
        });

        it('Muokkaa erillishankinnat', function () {
            cy.get('#erillishankinnat-elementti table.grid').gridOtsikot().then(() => {

                let valitseInput = function (rivi) {
                    return `#erillishankinnat-elementti table.grid tbody tr:nth-child(${rivi + 1}) td input`;
                };

                // Muokataan Lokakuu 2025
                cy.get(valitseInput(1)).eq(0).clear().type('1000');

                // Muokataan Marraskuu 2025
                cy.get(valitseInput(2)).eq(0).clear().type('1500');

                // Muokataan Joulukuu 2025
                cy.get(valitseInput(3)).eq(0).clear().type('2000');

                // Yhteensä ja Kirjaamatta teksti ja summat täsmää
                cy.get('#erillishankinnat-elementti table.grid tbody')
                    .contains('Yhteensä').next().contains('4 500,00');
                cy.get('#erillishankinnat-elementti table.grid tbody')
                    .contains('Kirjaamatta');

            });

            // Tallennetaan muutokset
            cy.get('#erillishankinnat-elementti').contains('Tallenna tiedot').click();
            cy.wait('@tallenna-erillishankinnat')
                .its('response.statusCode')
                .should('equal', 200);

            // Viesti onnistumisesta pitäisi näkyä
            cy.contains('Erillishankinnat tallennettiin onnistuneesti.', {timeout: 4000}).should('be.visible');

        });

    });


    describe('Testaa Johto- ja hallintokorvaus 2025', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset-2025').as('tallenna-toimenkuvat-2025');
            avaaUusiKustannussuunnittelu('POP MHU Kajaani 2025-2030', 'Pohjois-Suomi');
        });

        it('Taulukon arvot alussa oikein', function () {

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Kalenterikuukausi');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Suunniteltu kustannus (€)');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Indeksikorjattu (€)');

            // Varmistetaan, että taulukosta löytyy toimenpiteitä
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Lokakuu 2025');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Marraskuu 2025');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Joulukuu 2025');

            // Löytyyhän lukuja
            tarkistaToimenkuva2025Luvut('Lokakuu 2025', '0,00', '-');
            tarkistaToimenkuva2025Luvut('Marraskuu 2025', '0,00', '-');
            tarkistaToimenkuva2025Luvut('Joulukuu 2025', '0,00', '-');
        });

        it('Muokkaa Johto- ja hallintokorvaus 2025', function () {
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').gridOtsikot().then(() => {

                let valitseInput = function (rivi) {
                    return `#johto-ja-hallintokorvaus-elementti table.grid tbody tr:nth-child(${rivi + 1}) td input`;
                };

                // Muokataan Lokakuu 2024
                cy.get(valitseInput(1)).eq(0).clear().type('1000');

                // Muokataan Marraskuu 2024
                cy.get(valitseInput(2)).eq(0).clear().type('1500');

                // Muokataan Joulukuu 2024
                cy.get(valitseInput(3)).eq(0).clear().type('2000');

                // Kirjaamatta teksti näkyy vielä
                cy.get('#johto-ja-hallintokorvaus-elementti table.grid tr:nth-child(14) td').contains('Kirjaamatta');

            });

            // Tallennetaan muutokset
            cy.get('#johto-ja-hallintokorvaus-elementti').contains('Tallenna tiedot').click();
            cy.wait('@tallenna-toimenkuvat-2025')
                .its('response.statusCode')
                .should('equal', 200);
        });

    });
    

    describe('Testaa Hoidonjohtopalkkiot 2025 vuoden urakalle', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-hoidonjohtopalkkiot').as('tallenna-hoidonjohtopalkkiot');
            avaaUusiKustannussuunnittelu('POP MHU Kajaani 2025-2030', 'Pohjois-Suomi');
        });

        it('Taulukon arvot alussa oikein', function () {

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('th').contains('Kalenterikuukausi');
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('th').contains('Suunniteltu kustannus (€)');
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('th').contains('Indeksikorjattu (€)');

            // Varmistetaan, että taulukosta löytyy toimenpiteitä
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('td').contains('Lokakuu 2025');
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('td').contains('Marraskuu 2025');
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('td').contains('Joulukuu 2025');

            // Löytyyhän lukuja
            tarkistaHoidonjotopalkkioLuvut('Lokakuu 2025', '0,00', '-');
            tarkistaHoidonjotopalkkioLuvut('Marraskuu 2025', '0,00', '-');
            tarkistaHoidonjotopalkkioLuvut('Joulukuu 2025', '0,00', '-');
        });

        it('Muokkaa Hoidonjohtopalkkiot 2022 vuoden urakalle', function () {
            cy.get('#hoidonjohtopalkkio-elementti table.grid').gridOtsikot().then(() => {

                let valitseInput = function (rivi) {
                    return `#hoidonjohtopalkkio-elementti table.grid tbody tr:nth-child(${rivi + 1}) td input`;
                };

                // Muokataan Lokakuu 2025
                cy.get(valitseInput(1)).eq(0).clear().type('1000');

                // Muokataan Marraskuu 2025
                cy.get(valitseInput(2)).eq(0).clear().type('1500');

                // Muokataan Joulukuu 2025
                cy.get(valitseInput(3)).eq(0).clear().type('2000');

                // Yhteensä ja Kirjaamatta teksti ja summat täsmää
                cy.get('#hoidonjohtopalkkio-elementti table.grid tbody')
                    .contains('Yhteensä').next().contains('4 500,00');
                cy.get('#hoidonjohtopalkkio-elementti table.grid tbody')
                    .contains('Kirjaamatta').next().contains('-4 500,00');

            });

            // Tallennetaan muutokset
            cy.get('#hoidonjohtopalkkio-elementti').contains('Tallenna tiedot').click();
            cy.wait('@tallenna-hoidonjohtopalkkiot')
                .its('response.statusCode')
                .should('equal', 200);

            // Viesti onnistumisesta pitäisi näkyä
            cy.contains('Hoidonjohtopalkkiot tallennettiin.', {timeout: 4000}).should('be.visible');

        });

    });


});
