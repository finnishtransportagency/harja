import * as ks from "../support/kustannussuunnitelmaFns.js";
import {avaaHarjaTimeoutilla, muokkaaTarjousRiviaArvo} from "../support/apurit.js";

const clickTimeout = 6000;
const visibleTimeout = 30000;
const urakanNimiKajaani = 'POP MHU Kajaani 2025-2030';
const urakanNimiOulu = 'Oulun MHU 2019-2024';
const elinvoimakeskus = 'Pohjois-Suomi';
let laskutusraja_Kajaani_hoitovuosi1;

function alustaUrakkaKustannussuunnitteluun(nimi) {
    ks.alustaKanta(nimi);
}

function tarkistaLaskutusrajaOsio() {
    cy.contains('h2', 'Laskutusraja', {timeout: visibleTimeout}).should('be.visible');
    cy.get('div.laskutusraja div.lukema-label').contains('Laskutusrajan käyttö').should('be.visible');
    cy.get('div.laskutusraja div.lukema')
        .should('exist')
        .and('not.be.empty')
        .invoke('text')
        .then(function(lukema) {
            const laskutusraja = lukema.split('/')[1];
            expect(trimmaaArvo(laskutusraja)).to.equal(laskutusraja_Kajaani_hoitovuosi1);
        });
}

function trimmaaArvo(arvo) {
    return arvo.toString().replace(/\s+/g, ' ').replace('€', '').replace(' ', '').replace(',', '.').trim();
}

function avaaOulunKulujenKohdistus() {
    avaaHarjaTimeoutilla();

    cy.contains('.haku-lista-item', elinvoimakeskus, {timeout: visibleTimeout}).click();
    cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist');

    // Urakka on päättynyt, joten täytyy näyttää päättyneet urakat
    cy.contains('label', 'Näytä päättyneet', {timeout: visibleTimeout})
        .should('be.visible')
        .parent()
        .find('input[type="checkbox"]')
        .check()
        .should('be.checked');

    cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimiOulu, {timeout: visibleTimeout}).click();

    cy.get('[data-cy=tabs-taso1-Kulut]', {timeout: visibleTimeout}).click();
    cy.get('[data-cy="tabs-taso2-Kulujen kohdistus"]').click();
    cy.get('img[src="images/ajax-loader.gif"]', {timeout: visibleTimeout}).should('not.exist');
}

describe('Laskutusraja', function () {

    before(function () {
        alustaUrakkaKustannussuunnitteluun(urakanNimiKajaani);
        cy.viewport(1100, 2000);
        avaaHarjaTimeoutilla();

        cy.intercept('POST', '_/tallenna-kilpailutettavat-hankinnat').as('tallenna-kilpailutettavat-hankinnat');
        cy.intercept('POST', '_/tallenna-tarjouksen-tiedot').as('tallenna-tarjous');
        cy.intercept('POST', '_/tallenna-erillishankinnat').as('tallenna-erillishankinnat');
        cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset-2025').as('tallenna-toimenkuvat-2025');
        cy.intercept('POST', '_/tallenna-hoidonjohtopalkkiot').as('tallenna-hoidonjohtopalkkiot');
        cy.intercept('POST', '_/vahvista-tavoite-ja-kattohinta').as('vahvista-tavoite-ja-kattohinta');
        cy.intercept('POST', '_/hae-urakan-laskutusraja').as('hae-laskutusraja');

        // Navigoi urakkaan
        cy.contains('.haku-lista-item', elinvoimakeskus).click();
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist');
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'});
        cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimiKajaani, {timeout: clickTimeout}).click();

        cy.get('[data-cy=tabs-taso1-Suunnittelu]').click();

        // Tallenna ensimmäisenä tarjoukseen jotain, koska muuten tavoitehinnan vahvistus ei onnistu
        cy.get('[data-cy="tabs-taso2-Tarjouksen tiedot"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: visibleTimeout}).should('not.exist');

        // Tallenna jotain kilpailutettaviin hankintoihin, erillishankintoihin, toimenkuviin ja hoidonjohtopalkkioon
        muokkaaTarjousRiviaArvo('tarjous-hankinnat-grid', 'Kilpailutettavat hankinnat', 0, 5);
        muokkaaTarjousRiviaArvo('tarjous-erillishankinnat-grid', 'Erillishankinnat', 0, 2);
        muokkaaTarjousRiviaArvo('tarjous-toimenkuvat-grid', 'Vastuunalainen työnjohtaja', 0, 2);
        muokkaaTarjousRiviaArvo('tarjous-hoidonjohtopalkkio-grid', 'Hoidonjohtopalkkio', 0, 1);
        // Tallenna muutokset
        cy.contains('button', 'Tallenna muutokset').click();

        // Tarkista että tallennuskutsu tehdään
        cy.wait('@tallenna-tarjous')
            .its('response.statusCode')
            .should('equal', 200);

        cy.get('[data-cy="tabs-taso2-Hoitovuoden alun tavoitehinta"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: visibleTimeout}).should('not.exist');

        // Valitse 1. hoitovuosi
        cy.get('div.label-ja-alasveto.hoitokausi div.dropdown').eq(0).within(() => {
            cy.get('button').click({force: true});
            cy.contains('1. hoitovuosi').click();
        });

        // Tallenna jotain kilpailutettaviin hankintoihin
        cy.get('#kilpailutettavat-hankinnat-elementti table.grid tbody tr:nth-child(1) td input')
            .eq(0).clear().type('5');
        cy.get('#kilpailutettavat-hankinnat-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-kilpailutettavat-hankinnat').its('response.statusCode').should('equal', 200);

        // Tallenna erillishankinnat
        cy.get('#erillishankinnat-elementti table.grid tbody tr:nth-child(2) td input')
            .eq(0).clear().type('2');
        cy.get('#erillishankinnat-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-erillishankinnat').its('response.statusCode').should('equal', 200);

        // Tallenna johto ja hallintokorvaukset
        cy.get('#johto-ja-hallintokorvaus-elementti table.grid tbody tr:nth-child(2) td input')
            .eq(0).clear().type('2');
        cy.get('#johto-ja-hallintokorvaus-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-toimenkuvat-2025').its('response.statusCode').should('equal', 200);

        // Tallenna hoidonjohtopalkkiot
        cy.get('#hoidonjohtopalkkio-elementti table.grid tbody tr:nth-child(2) td input')
            .eq(0).clear().type('1');
        cy.get('#hoidonjohtopalkkio-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-hoidonjohtopalkkiot').its('response.statusCode').should('equal', 200);

        // Vahvista tavoite- ja kattohinta
        cy.get('button.nappi-ensisijainen[type="button"]').contains('Vahvista tavoite- ja kattohinta').click();
        cy.wait('@vahvista-tavoite-ja-kattohinta').its('response.statusCode').should('equal', 200);

    });

    it("Tarkista laskutusraja", function () {
        // Siirry Suunnittelu -> Hoitovuoden alun tavoitehinta
        cy.get('[data-cy=tabs-taso1-Suunnittelu]').click();
        cy.get('[data-cy="tabs-taso2-Hoitovuoden alun tavoitehinta"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: visibleTimeout}).should('not.exist');

        // Valitse 1. hoitovuosi
        cy.get('div.label-ja-alasveto.hoitokausi div.dropdown').eq(0).within(() => {
            cy.get('button').click({force: true});
            cy.contains('1. hoitovuosi').click();
        });

        // Tarkista että laskutusraja näkyy
        cy.get('div #tavoite-ja-kattohinta-elementti div')
            .contains('Laskutusraja')
            .next()
            .should('exist')
            .and('not.be.empty')
            .invoke('text')
            .then(trimmaaArvo)
            .then((arvo) => { laskutusraja_Kajaani_hoitovuosi1 = arvo; });
    });

    it("Peruuta vahvistus ja tarkista että laskutusraja nollautuu", function () {
        cy.intercept('POST', '_/vahvista-tavoite-ja-kattohinta').as('vahvista-tavoite-ja-kattohinta');
        cy.intercept('POST', '_/hae-urakan-laskutusraja').as('hae-laskutusraja');

        // Siirry Suunnittelu -> Hoitovuoden alun tavoitehinta
        cy.get('[data-cy=tabs-taso1-Suunnittelu]').click();
        cy.get('[data-cy="tabs-taso2-Hoitovuoden alun tavoitehinta"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: visibleTimeout}).should('not.exist');

        // Peruuta vahvistus
        cy.get('button.nappi-toissijainen[type="button"]').contains('Peruuta vahvistus').click();
        cy.wait('@vahvista-tavoite-ja-kattohinta').its('response.statusCode').should('equal', 200);

        // Tarkista että laskutusraja on asetettu
        cy.get('div #tavoite-ja-kattohinta-elementti div')
            .contains('Laskutusraja')
            .next()
            .invoke('text')
            .then(function (teksti) {
                expect(trimmaaArvo(teksti)).to.equal(laskutusraja_Kajaani_hoitovuosi1);
            });

        // Vahvista tavoite- ja kattohinta uudestaan
        cy.get('button.nappi-ensisijainen[type="button"]').contains('Vahvista tavoite- ja kattohinta').click();
        cy.wait('@vahvista-tavoite-ja-kattohinta').its('response.statusCode').should('equal', 200);
    });

    it("Laskutusraja näkyy Kulujen kohdistus -sivulla", function () {
        cy.intercept('POST', '_/hae-urakan-laskutusraja').as('hae-laskutusraja');
        cy.intercept('POST', '_/hae-hoitokauden-kulujen-summa').as('hae-hoitokauden-kulujen-summa');

        // Siirry Kulut → Kulujen kohdistus
        cy.get('[data-cy=tabs-taso1-Kulut]').click();
        cy.get('[data-cy="tabs-taso2-Kulujen kohdistus"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: visibleTimeout}).should('not.exist');

        // Odota että laskutusraja haetaan
        cy.wait('@hae-laskutusraja', {timeout: visibleTimeout}).its('response.statusCode').should('equal', 200);
        cy.wait('@hae-hoitokauden-kulujen-summa', {timeout: visibleTimeout}).its('response.statusCode').should('equal', 200);

        // Tarkista että Laskutusraja-osio näkyy
        tarkistaLaskutusrajaOsio();
    });

    it("Hoitovuoden vaihto hakee uuden laskutusrajan", function () {
        cy.intercept('POST', '_/hae-urakan-laskutusraja').as('hae-laskutusraja');

        // Siirry Kulut -> Kulujen kohdistus
        cy.get('[data-cy=tabs-taso1-Kulut]').click();
        cy.get('[data-cy="tabs-taso2-Kulujen kohdistus"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: visibleTimeout}).should('not.exist');

        // Vaihda hoitovuotta (jos mahdollista)
        cy.get('body').then(($body) => {
            cy.get('[data-cy=hoitokausi-valinta]').click();
            cy.contains('li', '2. hoitovuosi').click();

            // Odota että laskutusraja haetaan uudelleen
            cy.wait('@hae-laskutusraja', {timeout: visibleTimeout}).its('response.statusCode').should('equal', 200);

            // Tarkista että Laskutusraja-otsikko näkyy (vaikka arvoa ei olisi)
            cy.contains('h2', 'Laskutusraja').should('be.visible');

            // Tarkista että sivulla on notifikaatio (eli hoitovuoden alun tavoite- ja kattohinta on vahvistamatta)
            cy.get('.info-laatikko.vahva-ilmoitus')
                .should('exist')
                .and('be.visible');
        });
    });

    it("Laskutusraja näkyy Kustannusten seuranta -sivulla", function () {
        cy.intercept('POST', '_/hae-urakan-laskutusraja').as('hae-laskutusraja');
        cy.intercept('POST', '_/hae-hoitokauden-kulujen-summa').as('hae-hoitokauden-kulujen-summa');

        // Siirry Kulut → Kustannusten seuranta
        cy.get('[data-cy=tabs-taso1-Kulut]').click();
        cy.get('[data-cy="tabs-taso2-Kustannusten seuranta"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: visibleTimeout}).should('not.exist');

        // Odota että laskutusraja haetaan
        cy.wait('@hae-laskutusraja', {timeout: visibleTimeout}).its('response.statusCode').should('equal', 200);
        cy.wait('@hae-hoitokauden-kulujen-summa', {timeout: visibleTimeout}).its('response.statusCode').should('equal', 200);

        // Tarkista että Laskutusraja-osio näkyy
        tarkistaLaskutusrajaOsio();
    });

    it("Laskutusraja näkyy Muutokset-näkymässä", function () {
        cy.intercept('POST', '_/hae-urakan-laskutusraja').as('hae-laskutusraja');

        // Siirry Muutoksiin
        cy.get('[data-cy=tabs-taso1-Muutokset]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: visibleTimeout}).should('not.exist');

        // Tarkista että laskutusraja näkyy
        cy.get('div.muutosten-vaikutus div.tietoja.muutosten-vaikutus-container span span')
            .contains('Laskutusraja').parent().next()
            .invoke('text')
            .then(function (teksti) {
            expect(trimmaaArvo(teksti)).to.equal(laskutusraja_Kajaani_hoitovuosi1);
        });
    });
});

describe('Harjan generoimat kulut Kulujen kohdistus -näkymässä', function () {

    before(function () {
        cy.viewport(1100, 2000);

        cy.intercept('POST', '_/hae-urakan-kulut').as('hae-kulut');
        cy.intercept('POST', '_/hae-kaikkien-tehtavaryhmien-nimet').as('hae-tehtavaryhmat');

        avaaOulunKulujenKohdistus();
    });

    it('Maaliskuun 2020 kulurivi 01.03.2020 sisältää Harjan automaattisesti luoman kulun tehtäväryhmällä', function () {
        // Valitse 1. hoitovuosi
        cy.get('[data-cy=hoitokausi-valinta]').click();
        cy.contains('li', '1. hoitovuosi').click();

        // Valitse kuukaudeksi maaliskuu 2020
        cy.intercept('POST', '_/hae-urakan-kulut').as('hae-kulut-maaliskuu');
        cy.get('[id="kuukausi-valinta"]').click();
        cy.get('ul.livi-alasvetolista').contains('li', 'Maaliskuu 2020').click();
        cy.wait('@hae-tehtavaryhmat', {timeout: visibleTimeout}).its('response.statusCode').should('equal', 200);

        // Odota että lataus päättyy
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: visibleTimeout}).should('not.exist');

        // Etsi 01.03.2020 päivämäärärivi ja klikkaa sen alla oleva toimenpiderivi auki
        cy.contains('table.grid tbody tr.klikattava td', '01.03.2020', {timeout: visibleTimeout})
            .should('be.visible')
            .closest('tr')
            .click();

        // Tarkista, että auki olevan rivin alla on kulu, jossa on
        // lisätietona "Harjan automaattisesti luoma kulu" ja tehtäväryhmä on täytetty
        cy.get('table.grid tbody')
            .within(() => {
                cy.contains('td', 'Harjan automaattisesti luoma kulu')
                    .should('have.length.gte', 1)
                    .each(($td) => {
                        const $rivi = $td.closest('tr');
                        // Tarkista että tehtäväryhmä-sarake (indeksi 3) ei ole tyhjä
                        cy.wrap($rivi).find('td').eq(3).invoke('text').should('not.be.empty');
                    });
            });
    });
});
