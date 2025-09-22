import * as ks from '../support/kustannussuunnitelmaFns.js';
import transit from "transit-js";
import {avaaKustannussuunnittelu, avaaUusiKustannussuunnittelu} from "../support/kustannussuunnitelmaFns.js";

// Täytetään ajax kutsun vastauksen perusteella
const indeksit = [];

function alustaIvalonUrakka() {
    ks.alustaKanta('Ivalon MHU testiurakka (uusi)');
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
        alustaIvalonUrakka();
        alustaIinUrakka();
        alustaKajaanin25Urakka();
    })

    describe('Testaa kilpailutetttavat hankinnat 2024', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-kilpailutettavat-hankinnat').as('tallenna-kilpailutettavat-hankinnat');
            avaaUusiKustannussuunnittelu('Ivalon MHU testiurakka (uusi)', 'Lappi');
        });

        it('Taulukon arvot alussa oikein', function () {

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('th').contains('Toimenpide');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('th').contains('Pysyvät muutokset (€)');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('th').contains('Loka-joulukuu 2024 (€)');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('th').contains('Tammi-syyskuu 2025 (€)');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('th').contains('Yhteensä (€)');

            // Varmistetaan, että taulukosta löytyy toimenpiteitä
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('td').contains('Talvihoito');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('td').contains('Liikenneympäristön hoito');
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid').find('td').contains('Sorateiden hoito');

            // Löytyyhän lukuja
            tarkistaToimenpideLuvut('Talvihoito', '0,00', '0,00');
            tarkistaToimenpideLuvut('Liikenneympäristön hoito', '0,00', '0,00');
            tarkistaToimenpideLuvut('Sorateiden hoito', '0,00', '0,00');
        });

        it('Muokkaa kilpailutettavat hankinnat', function () {
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

                // Yhteensä ja Kirjaamatta teksti näkyy ja summat on oikein
                cy.get('#kilpailutettavat-hankinnat-elementti table.grid tbody')
                    .contains('Yhteensä').next().next().contains('4 500,00');
                cy.get('#kilpailutettavat-hankinnat-elementti table.grid tbody')
                    .contains('Yhteensä').next().next().next().contains('7 500,00');
                cy.get('#kilpailutettavat-hankinnat-elementti table.grid tbody')
                    .contains('Yhteensä').next().next().next().next().contains('12 000,00');
                cy.get('#kilpailutettavat-hankinnat-elementti table.grid tbody')
                    .contains('Kirjaamatta').should('not.exist');

            });

            // Tallennetaan muutokset
            cy.get('#kilpailutettavat-hankinnat-elementti').contains('Tallenna tiedot').click();
            cy.wait('@tallenna-kilpailutettavat-hankinnat')
              .its('response.statusCode')
              .should('equal', 200);

            // Viesti onnistumisesta pitäisi näkyä
            cy.contains('Kilpailutettavat hankinnat tallennettiin.', {timeout: 4000}).should('be.visible');

        });

    });

    describe('Testaa rahavaraukset 2024', function () {

        beforeEach(function () {
            avaaUusiKustannussuunnittelu('Ivalon MHU testiurakka (uusi)', 'Lappi');
        });

        it('Taulukon arvot alussa oikein', function () {

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#rahavaraukset-elementti table.grid').find('th').contains('Rahavaraus');
            cy.get('#rahavaraukset-elementti table.grid').find('th').contains('Suunniteltu kustannus (€)');
            cy.get('#rahavaraukset-elementti table.grid').find('th').contains('Indeksikorjattu (€)');

            // Varmistetaan, että taulukosta löytyy toimenpiteitä
            cy.get('#rahavaraukset-elementti table.grid').find('td').contains('Tilaajan rahavaraus kannustinjärjestelmään');
            cy.get('#rahavaraukset-elementti table.grid').find('td').contains('Äkilliset hoitotyöt');
            cy.get('#rahavaraukset-elementti table.grid').find('td').contains('Vahinkojen korjaukset');

            // Löytyyhän lukuja - Olisi hyvä, jos tarjouspuolella käytäisiin lisäämässä jotain, mitä tässä verrata,
            // mutta tätä tehtäessä tarjouspuoli on vielä niin kesken, ettei kannata vielä tehdä sinne cypress-testejä
            tarkistaRahavarausLuvut('Tilaajan rahavaraus kannustinjärjestelmään', '0,00', '', '-');
            tarkistaRahavarausLuvut('Äkilliset hoitotyöt', '0,00', '', '-');
            tarkistaRahavarausLuvut('Vahinkojen korjaukset', '0,00', '', '-');
        });
    });

    describe('Testaa erillishankinnat', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-erillishankinnat').as('tallenna-erillishankinnat');
            avaaUusiKustannussuunnittelu('Ivalon MHU testiurakka (uusi)', 'Lappi');
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

                // Yhteensä ja Kirjaamatta teksti ja summat täsmää
                cy.get('#erillishankinnat-elementti table.grid tbody')
                    .contains('Yhteensä').next().contains('4 500,00');
                cy.get('#erillishankinnat-elementti table.grid tbody')
                    .contains('Kirjaamatta').should('not.exist');

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

    describe('Testaa Johto- ja hallintokorvaus 2019', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset-2019').as('tallenna-toimenkuvat-2022');
            avaaUusiKustannussuunnittelu('Iin MHU 2021-2026', 'Pohjois-Pohjanmaa');
        });

        it('Taulukon arvot alussa oikein', function () {

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Toimenkuva');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Tarjouksen määrä (€ / vuosi)');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Tunnit (h/kk)');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Tuntipalkka (€/h)');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Yhteensä (€/vuosi)');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Kk/v');

            // Varmistetaan, että taulukosta löytyy toimenpiteitä
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Sopimusvastaava');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Vastuunalainen työnjohtaja');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Päätoiminen apulainen (talvikausi)');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Päätoiminen apulainen (kesäkausi)');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Apulainen/työnjohtaja (talvikausi)');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Apulainen/työnjohtaja (kesäkausi)');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Viherhoidosta vastaava henkilö');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Hankintavastaava');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Muut kulut');

            // Löytyyhän lukuja
            tarkistaToimenkuva2019Luvut('Sopimusvastaava', '0,00', '', '0,00', '0,00', '12');
            tarkistaToimenkuva2019Luvut('Vastuunalainen työnjohtaja', '0,00', '', '0,00', '0,00', '12');
            tarkistaToimenkuva2019Luvut('Päätoiminen apulainen (talvikausi)', '0,00', '', '0,00', '0,00', '5');
        });

        it('Muokkaa Johto- ja hallintokorvaus 2019', function () {
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').gridOtsikot().then(() => {

                let asetaToimenkuvalleArvo = function (toimenkuva, tunnit, tuntipalkka) {

                    cy.get('#johto-ja-hallintokorvaus-elementti table.grid').contains('td', toimenkuva).closest('tr')
                        .find('input').eq(0).clear().type(tunnit).blur()
                    cy.get('#johto-ja-hallintokorvaus-elementti table.grid').contains('td', toimenkuva).closest('tr')
                        .find('input').eq(1).clear().type(tuntipalkka).blur()
                };

                let asetaMuutKulutArvo = function (toimenkuva, arvo) {
                    cy.get('#johto-ja-hallintokorvaus-elementti table.grid tbody')
                        .contains(toimenkuva).next().next().next().next().find('input').clear().type(arvo).blur()
                };

                asetaToimenkuvalleArvo('Sopimusvastaava', '1','1000');
                asetaToimenkuvalleArvo('Vastuunalainen työnjohtaja', '1','1000');
                asetaToimenkuvalleArvo('Päätoiminen apulainen (talvikausi)', '1','1000');
                asetaToimenkuvalleArvo('Päätoiminen apulainen (kesäkausi)', '1','1000');
                asetaToimenkuvalleArvo('Apulainen/työnjohtaja (talvikausi)', '1','1000');
                asetaToimenkuvalleArvo('Apulainen/työnjohtaja (kesäkausi)', '1','1000');
                asetaToimenkuvalleArvo('Viherhoidosta vastaava henkilö', '1','1000');
                asetaToimenkuvalleArvo('Hankintavastaava', '1','1000');
                asetaMuutKulutArvo('Muut kulut', '1000');

                cy.wait(1000);
                // Yhteensä ja Kirjaamatta
                cy.get('#johto-ja-hallintokorvaus-elementti table.grid tbody')
                    .contains('Yhteensä').next().next().next().next().contains('66 000,00');
                cy.get('#johto-ja-hallintokorvaus-elementti table.grid tbody')
                    .contains('Kirjaamatta').should('not.exist');

            });

            // Tallennetaan muutokset
            cy.get('#johto-ja-hallintokorvaus-elementti').contains('Tallenna tiedot').click();
            cy.wait('@tallenna-toimenkuvat-2022')
                .its('response.statusCode')
                .should('equal', 200);

            // Viesti onnistumisesta pitäisi näkyä
            cy.contains('Johto- ja Hallintokorvaukset tallennettiin.', {timeout: 4000}).should('be.visible');
        });

    });

    describe('Testaa Johto- ja hallintokorvaus 2024', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset-2019').as('tallenna-toimenkuvat-2022');
            avaaUusiKustannussuunnittelu('Ivalon MHU testiurakka (uusi)', 'Lappi');
        });

        it('Taulukon arvot alussa oikein', function () {

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Toimenkuva');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Tarjouksen määrä (€)');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('th').contains('Suunniteltu määrä (€)');

            // Varmistetaan, että taulukosta löytyy toimenpiteitä
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Valmistelukausi ennen urakka-ajan alkua');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('Vastuunalainen työnjohtaja');
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').find('td').contains('2. työnjohtaja');

            // Löytyyhän lukuja
            tarkistaToimenkuva2022Luvut('Valmistelukausi ennen urakka-ajan alkua', '0,00', '0,00');
            tarkistaToimenkuva2022Luvut('Vastuunalainen työnjohtaja', '0,00', '0,00');
            tarkistaToimenkuva2022Luvut('2. työnjohtaja', '0,00', '0,00');
        });

        it('Muokkaa Johto- ja hallintokorvaus 2022', function () {
            cy.get('#johto-ja-hallintokorvaus-elementti table.grid').gridOtsikot().then(() => {

                let asetaToimenkuvalleArvo = function (toimenkuva, arvo) {
                    cy.get('#johto-ja-hallintokorvaus-elementti table.grid tbody')
                        .contains(toimenkuva).next().next().find('input').clear().type(arvo).blur()
                };

                asetaToimenkuvalleArvo('Valmistelukausi ennen urakka-ajan alkua', '1000');
                asetaToimenkuvalleArvo('Vastuunalainen työnjohtaja', '1000');
                asetaToimenkuvalleArvo('2. työnjohtaja', '1000');
                asetaToimenkuvalleArvo('3. työnjohtaja', '1000');
                asetaToimenkuvalleArvo('Viherhoidosta vastaava henkilö', '1000');
                asetaToimenkuvalleArvo('Harjoittelija', '1000');
                asetaToimenkuvalleArvo('Muut kulut', '1000');
                cy.wait(1000);
                // Yhteensä ja Kirjaamatta
                cy.get('#johto-ja-hallintokorvaus-elementti table.grid tbody')
                    .contains('Yhteensä').next().next().contains('7 000,00');
                cy.get('#johto-ja-hallintokorvaus-elementti table.grid tbody')
                    .contains('Kirjaamatta').should('not.exist');

            });

            // Tallennetaan muutokset
            cy.get('#johto-ja-hallintokorvaus-elementti').contains('Tallenna tiedot').click();
            cy.wait('@tallenna-toimenkuvat-2022')
                .its('response.statusCode')
                .should('equal', 200);

            // Viesti onnistumisesta pitäisi näkyä
            cy.contains('Johto- ja Hallintokorvaukset tallennettiin.', {timeout: 4000}).should('be.visible');
        });

    });

    describe('Testaa Johto- ja hallintokorvaus 2025', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset-2025').as('tallenna-toimenkuvat-2025');
            avaaUusiKustannussuunnittelu('POP MHU Kajaani 2025-2030', 'Pohjois-Pohjanmaa');
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

    describe('Testaa Hoidonjohtopalkkiot 2022 vuoden urakalle', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-hoidonjohtopalkkiot').as('tallenna-hoidonjohtopalkkiot');
            avaaUusiKustannussuunnittelu('Ivalon MHU testiurakka (uusi)', 'Lappi');
        });

        it('Taulukon arvot alussa oikein', function () {

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('th').contains('Kalenterikuukausi');
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('th').contains('Suunniteltu kustannus (€)');
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('th').contains('Indeksikorjattu (€)');

            // Varmistetaan, että taulukosta löytyy toimenpiteitä
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('td').contains('Lokakuu 2024');
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('td').contains('Marraskuu 2024');
            cy.get('#hoidonjohtopalkkio-elementti table.grid').find('td').contains('Joulukuu 2024');

            // Löytyyhän lukuja
            tarkistaHoidonjotopalkkioLuvut('Lokakuu 2024', '0,00', '-');
            tarkistaHoidonjotopalkkioLuvut('Marraskuu 2024', '0,00', '-');
            tarkistaHoidonjotopalkkioLuvut('Joulukuu 2024', '0,00', '-');
        });

        it('Muokkaa Hoidonjohtopalkkiot 2022 vuoden urakalle', function () {
            cy.get('#hoidonjohtopalkkio-elementti table.grid').gridOtsikot().then(() => {

                let valitseInput = function (rivi) {
                    return `#hoidonjohtopalkkio-elementti table.grid tbody tr:nth-child(${rivi + 1}) td input`;
                };

                // Muokataan Lokakuu 2024
                cy.get(valitseInput(1)).eq(0).clear().type('1000');

                // Muokataan Marraskuu 2024
                cy.get(valitseInput(2)).eq(0).clear().type('1500');

                // Muokataan Joulukuu 2024
                cy.get(valitseInput(3)).eq(0).clear().type('2000');

                // Yhteensä teksti ja summat täsmää
                cy.get('#hoidonjohtopalkkio-elementti table.grid tbody')
                    .contains('Yhteensä').next().contains('4 500,00');
                cy.get('#hoidonjohtopalkkio-elementti table.grid tbody').
                contains('Kirjaamatta').should('not.exist')

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

    describe('Testaa Hoidonjohtopalkkiot 2025 vuoden urakalle', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-hoidonjohtopalkkiot').as('tallenna-hoidonjohtopalkkiot');
            avaaUusiKustannussuunnittelu('POP MHU Kajaani 2025-2030', 'Pohjois-Pohjanmaa');
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
                    .contains('Kirjaamatta').next().contains('−4 500,00');

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
