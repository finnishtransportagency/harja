import * as ks from '../support/kustannussuunnitelmaFns.js';
import transit from "transit-js";
import {avaaKustannussuunnittelu} from "../support/kustannussuunnitelmaFns.js";

// Täytetään ajax kutsun vastauksen perusteella
const indeksit = [];

function alustaIvalonUrakka() {
    ks.alustaKanta('Ivalon MHU testiurakka (uusi)');
}

// ------------------------------------
// --- Tavoitehinnan ulkopuoliset rahavaraukset osio ---
// ------------------------------------

// Varaukset mm. bonuksien laskemista varten. Näitä varauksia ei lasketa mukaan tavoitehintaan.
// Tämän blokin testien rahasummia ei siis testata tavoitehinnan yhteenvedosta testipatterin viimeisessä testissä!

describe('Tavoitehintaiset rahavaraukset osio', function () {

    before(function () {
        alustaIvalonUrakka();
        avaaKustannussuunnittelu('Ivalon MHU testiurakka (uusi)', 'Lappi', indeksit);
    })

    describe('Testaa tavoitehintaiset rahavaraukset taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-tavoitehintainen-rahavaraus').as('tallenna-tavoitehintainen-rahavaraus');
            cy.intercept('POST', '_/tallenna-tavoitehinnan-ulkopuolinen-rahavaraus').as('tallenna-tavoitehinnan-ulkopuolinen-rahavaraus');
        });

        it('Taulukon arvot alussa oikein', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="rahavaraukset-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')

            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" ei ole aktiivinen.
            cy.get('div[data-cy="rahavaraukset-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked');

            // Varmistetaan, että taulukon otsikkorivillä on kaikki kunnossa
            cy.get('#tavoitehintaiset-rahavaraukset-grid').find('th').contains('Rahavaraus');
            cy.get('#tavoitehintaiset-rahavaraukset-grid').find('th').contains('Yhteensä, €/vuosi');
            cy.get('#tavoitehintaiset-rahavaraukset-grid').find('th').contains('Indeksikorjattu');

            // Varmistetaan, että kaikki kolme default rahavarausta esiintyy taulukossa
            cy.get('#tavoitehintaiset-rahavaraukset-grid').find('td').contains('Äkilliset hoitotyöt');
            cy.get('#tavoitehintaiset-rahavaraukset-grid').find('td').contains('Vahinkojen korjaukset');
            cy.get('#tavoitehintaiset-rahavaraukset-grid').find('td').contains('Tilaajan rahavaraus kannustinjärjestelmään');
        });

        it('Muokkaa tavoitehintaiset rahavaraukset (Ilman kopiointia)', function () {

            cy.get('#tavoitehintaiset-rahavaraukset-grid').gridOtsikot().then(($gridOtsikot) => {
                let $rivit = $gridOtsikot.grid.find('tbody tr');
                let valitseInput = function (rivi) {
                    let rr = $rivit.eq(rivi).find('td').find('input');
                    return rr;
                }
                cy.get(valitseInput(0)).type('10').blur().wait(500); // 'Äkilliset hoitotyöt'
                cy.get(valitseInput(1)).type('11').blur().wait(500); // 'Vahinkojen korjaukset'
                cy.get(valitseInput(2)).type('12').blur().wait(500); // 'Tilaajan rahavaraus kannustinjärjestelmään'
            });

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-tavoitehintainen-rahavaraus')

            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetokomponentista
            cy.log('Tarkastetaan yhteenvetorivi...');
            ks.tarkastaHintalaskurinArvo('tavoitehintaiset-rahavaraukset-hintalaskuri', 1, 33);

        });

        it('Muokkaa tavoitehinnan ulkopuoliset rahavaraukset (Kopioinnin kanssa)', function () {
            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" on aktiivinen.
            cy.get('div[data-cy="rahavaraukset-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked').check();

            // Aseta hoitovuodelle 1 arvo 10
            cy.get('#tavoitehinnan-ulkopuoliset-rahavaraukset-grid').gridOtsikot().then(($gridOtsikot) => {
                let $rivit = $gridOtsikot.grid.find('tbody tr');
                let valitseInput = function (rivi) {
                    let rr = $rivit.eq(rivi).find('td').find('input');
                    return rr;
                }
                cy.get(valitseInput(0)).type('10').blur().wait(500); // 'Tavoitehinnan ulkopuoliset rahavaraukset'
            });

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-tavoitehinnan-ulkopuolinen-rahavaraus')

            // Vaihda hoitokausi 1. -> 2.
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('2.')
                .click();

            // Varmista, että hoitovuodella 2. on arvo 10
            cy.get('#tavoitehinnan-ulkopuoliset-rahavaraukset-grid').gridOtsikot().then(($gridOtsikot) => {
                let $rivit = $gridOtsikot.grid.find('tbody tr');
                let valitseInput = function (rivi) {
                    let rr = $rivit.eq(rivi).find('td').find('input');
                    return rr;
                }
                cy.get(valitseInput(0)).should('have.value','10,00'); // 'Tavoitehinnan ulkopuoliset rahavaraukset'
            });

            // Vaihda hoitokausi 2. -> 1.
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('1.')
                .click();

            // Varmista, että hoitovuodella 1. on arvo 10
            cy.get('#tavoitehinnan-ulkopuoliset-rahavaraukset-grid').gridOtsikot().then(($gridOtsikot) => {
                let $rivit = $gridOtsikot.grid.find('tbody tr');
                let valitseInput = function (rivi) {
                    let rr = $rivit.eq(rivi).find('td').find('input');
                    return rr;
                }
                cy.get(valitseInput(0)).should('have.value','10,00'); // 'Tavoitehinnan ulkopuoliset rahavaraukset'
            });

        });
    });
});
