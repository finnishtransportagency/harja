import * as ks from "../support/kustannussuunnitelmaFns.js";
import {avaaHarjaTimeoutilla, muokkaaTarjousRiviaArvo} from "../support/apurit.js";

const clickTimeout = 6000;
const visibleTimeout = 30000;
const urakanNimi = 'POP MHU Kajaani 2025-2030';

function alustaUrakkaKustannussuunnitteluun(nimi) {
    ks.alustaKanta(nimi);
}

function trimmaaArvo(arvo) {
    return arvo.toString().replace(/\s+/g, ' ').replace('€', '').replace(' ', '').replace(',', '.').trim();
}

describe('Laskutusraja testit', function () {

    before(function () {
        alustaUrakkaKustannussuunnitteluun(urakanNimi);
        cy.viewport(1100, 2000);
        avaaHarjaTimeoutilla();
    });

    it("Vahvista tavoitehinta ja tarkista laskutusraja", function () {
        cy.intercept('POST', '_/tallenna-kilpailutettavat-hankinnat').as('tallenna-kilpailutettavat-hankinnat');
        cy.intercept('POST', '_/tallenna-tarjouksen-tiedot').as('tallenna-tarjous');
        cy.intercept('POST', '_/hae-tarjouksen-tiedot').as('hae-tarjous');
        cy.intercept('POST', '_/tallenna-erillishankinnat').as('tallenna-erillishankinnat');
        cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset-2025').as('tallenna-toimenkuvat-2025');
        cy.intercept('POST', '_/tallenna-hoidonjohtopalkkiot').as('tallenna-hoidonjohtopalkkiot');
        cy.intercept('POST', '_/vahvista-tavoite-ja-kattohinta').as('vahvista-tavoite-ja-kattohinta');
        cy.intercept('POST', '_/hae-urakan-laskutusraja').as('hae-laskutusraja');

        // Navigoi urakkaan
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa').click();
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist');
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'});
        cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimi, {timeout: clickTimeout}).click();

        cy.get('[data-cy=tabs-taso1-Suunnittelu]').click();

        // Tallenna ensimmäisenä tarjoukseen jotain, koska muuten tavoitehinnan vahvistus ei onnistu
        cy.get('[data-cy="tabs-taso2-Tarjouksen tiedot"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');

        // Tallenna jotain kilpailutettaviin hankintoihin
        muokkaaTarjousRiviaArvo('tarjous-hankinnat-grid', 'Kilpailutettavat hankinnat', 0, 10);
        // Tallenna muutokset
        cy.contains('button', 'Tallenna muutokset').click();

        // Tarkista että tallennuskutsu tehdään
        cy.wait('@tallenna-tarjous')
            .its('response.statusCode')
            .should('equal', 200);

        cy.get('[data-cy="tabs-taso2-Hoitovuoden alun tavoitehinta"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');

        // Valitse 1. hoitovuosi
        cy.get('div.label-ja-alasveto.hoitokausi div.dropdown').eq(0).within(() => {
            cy.get('button').click({force: true});
            cy.contains('1. hoitovuosi').click();
        });

        // Tallenna jotain kilpailutettaviin hankintoihin
        cy.get('#kilpailutettavat-hankinnat-elementti table.grid tbody tr:nth-child(1) td input')
            .eq(0).clear().type('10');
        cy.get('#kilpailutettavat-hankinnat-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-kilpailutettavat-hankinnat').its('response.statusCode').should('equal', 200);

        // Tallenna erillishankinnat
        cy.get('#erillishankinnat-elementti table.grid tbody tr:nth-child(2) td input')
            .eq(0).clear().type('0');
        cy.get('#erillishankinnat-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-erillishankinnat').its('response.statusCode').should('equal', 200);

        // Tallenna johto ja hallintokorvaukset
        cy.get('#johto-ja-hallintokorvaus-elementti table.grid tbody tr:nth-child(2) td input')
            .eq(0).clear().type('0');
        cy.get('#johto-ja-hallintokorvaus-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-toimenkuvat-2025').its('response.statusCode').should('equal', 200);

        // Tallenna hoidonjohtopalkkiot
        cy.get('#hoidonjohtopalkkio-elementti table.grid tbody tr:nth-child(2) td input')
            .eq(0).clear().type('0');
        cy.get('#hoidonjohtopalkkio-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-hoidonjohtopalkkiot').its('response.statusCode').should('equal', 200);

        // Vahvista tavoite- ja kattohinta
        cy.get('button.nappi-ensisijainen[type="button"]').contains('Vahvista tavoite- ja kattohinta').click();
        cy.wait('@vahvista-tavoite-ja-kattohinta').its('response.statusCode').should('equal', 200);

        // Odota että laskutusraja haetaan vahvistuksen jälkeen
        cy.wait('@hae-laskutusraja', {timeout: 10000}).its('response.statusCode').should('equal', 200);

        // Tarkista että laskutusraja näkyy
        cy.get('div #tavoite-ja-kattohinta-elementti div')
            .contains('Laskutusraja')
            .next()
            .should('exist')
            .and('not.be.empty');
    });

    it("Peruuta vahvistus ja tarkista että laskutusraja nollautuu", function () {
        cy.intercept('POST', '_/vahvista-tavoite-ja-kattohinta').as('vahvista-tavoite-ja-kattohinta');
        cy.intercept('POST', '_/hae-urakan-laskutusraja').as('hae-laskutusraja');

        // Oletetaan että ollaan jo oikeassa näkymässä edellisen testin jälkeen
        // Jos ei ole, navigoi sinne
        cy.get('[data-cy=tabs-taso1-Suunnittelu]').click();
        cy.get('[data-cy="tabs-taso2-Hoitovuoden alun tavoitehinta"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');

        // Peruuta vahvistus
        cy.get('button.nappi-toissijainen[type="button"]').contains('Peruuta vahvistus').click();
        cy.wait('@vahvista-tavoite-ja-kattohinta').its('response.statusCode').should('equal', 200);

        // Odota että laskutusraja haetaan peruutuksen jälkeen
        cy.wait('@hae-laskutusraja', {timeout: 10000}).its('response.statusCode').should('equal', 200);

        // Tarkista että laskutusraja on nolla tai ei näy
        cy.get('div #tavoite-ja-kattohinta-elementti div')
            .contains('Laskutusraja')
            .next()
            .invoke('text')
            .should('satisfy', (text) => {
                const trimmed = trimmaaArvo(text);
                return trimmed === '0' || trimmed === '0.00' || trimmed === '';
            });
    });

    it("Laskutusraja näkyy Kulujen kohdistus -sivulla", function () {
        cy.intercept('POST', '_/hae-urakan-laskutusraja').as('hae-laskutusraja');
        cy.intercept('POST', '_/hae-hoitokauden-kulujen-summa').as('hae-hoitokauden-kulujen-summa');

        // Ensin vahvista tavoitehinta uudelleen
        cy.get('[data-cy=tabs-taso1-Suunnittelu]').click();
        cy.get('[data-cy="tabs-taso2-Hoitovuoden alun tavoitehinta"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');

        cy.get('button.nappi-ensisijainen[type="button"]').contains('Vahvista tavoite- ja kattohinta').click();
        cy.wait(2000); // Odota vahvistusta

        // Siirry Kulut → Kulujen kohdistus
        cy.get('[data-cy=tabs-taso1-Kulut]').click();
        cy.get('[data-cy="tabs-taso2-Kulujen kohdistus"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');

        // Odota että laskutusraja haetaan
        cy.wait('@hae-laskutusraja', {timeout: 10000}).its('response.statusCode').should('equal', 200);
        cy.wait('@hae-hoitokauden-kulujen-summa', {timeout: 10000}).its('response.statusCode').should('equal', 200);

        // Tarkista että Laskutusraja-osio näkyy
        cy.contains('h2', 'Laskutusraja', {timeout: visibleTimeout}).should('be.visible');

        // Tarkista että laskutusrajan käyttö näkyy
        cy.get('div.laskutusraja div.lukema-label')
            .contains('Laskutusrajan käyttö')
            .should('be.visible');

        // Tarkista että laskutusrajan arvo näkyy
        cy.get('div.laskutusraja div.lukema')
            .should('exist')
            .and('not.be.empty');
    });

    it("Hoitovuoden vaihto hakee uuden laskutusrajan", function () {
        cy.intercept('POST', '_/hae-urakan-laskutusraja').as('hae-laskutusraja');

        // Oletetaan että ollaan Kulujen kohdistus -sivulla
        cy.get('[data-cy=tabs-taso1-Kulut]').click();
        cy.get('[data-cy="tabs-taso2-Kulujen kohdistus"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');

        // Vaihda hoitovuotta (jos mahdollista)
        cy.get('body').then(($body) => {
            // Tarkista onko hoitokausi-valinta olemassa
            if ($body.find('[data-cy=hoitokausi-valinta]').length > 0) {
                // Avaa dropdown ja tarkista onko 2. hoitovuosi olemassa
                cy.get('[data-cy=hoitokausi-valinta]').click();

                cy.get('body').then(($dropdown) => {
                    if ($dropdown.find('li:contains("2. hoitovuosi")').length > 0) {
                        cy.contains('li', '2. hoitovuosi').click();

                        // Odota että laskutusraja haetaan uudelleen
                        cy.wait('@hae-laskutusraja', {timeout: 10000}).its('response.statusCode').should('equal', 200);

                        // Tarkista että Laskutusraja-otsikko näkyy (vaikka arvoa ei olisi)
                        cy.contains('h2', 'Laskutusraja').should('be.visible');

                        // Tarkista että sivulla on notifikaatio (eli hoitovuoden alun tavoite- ja kattohinta on vahvistamatta)
                        cy.get('.info-laatikko.vahva-ilmoitus')
                            .should('exist')
                            .and('be.visible');
                    } else {
                        cy.log('Vain 1. hoitovuosi saatavilla, skipataan hoitovuoden vaihto');
                        // Sulje dropdown jos se on auki
                        cy.get('[data-cy=hoitokausi-valinta]').click();
                    }
                });
            } else {
                cy.log('Hoitokausi-valinta ei löydy, skipataan hoitovuoden vaihto');
            }
        });
    });
});
