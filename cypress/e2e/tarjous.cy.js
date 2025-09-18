import * as ks from '../support/kustannussuunnitelmaFns.js';

// Apufunktiot tarjous-näkymälle
function alustaIvalonTarjousUrakka() {
    ks.alustaKanta('Ivalon MHU testiurakka (uusi)');
}

function avaaTarjousNakyma() {
    cy.visit('/#urakat/suunnittelu/tarjous?&hy=13&u=34');
}

function trimmaaArvo(arvo) {
    // Poistaa ylimääräiset välilyönnit ja trimmauksella
    return arvo.toString().replace(/\s+/g, ' ').trim();
}

function tarkistaTarjousRivinArvo(taulukonDataCy, rivinTunniste, sarakeIndex, odotettuArvo) {
    cy.get(`[data-cy=${taulukonDataCy}]`)
        .should('be.visible')
        .contains('tbody tr', rivinTunniste)
        .find('input')
        .eq(sarakeIndex)
        .should('be.visible')
        .invoke('val')
        .then((todellinen_arvo) => {
            const trimmattuTodellinen = trimmaaArvo(todellinen_arvo);
            const trimmattuOdotettu = trimmaaArvo(odotettuArvo);
            expect(trimmattuTodellinen).to.equal(trimmattuOdotettu);
        });
}

function muokkaaTarjousRiviaArvo(taulukonDataCy, rivinTunniste, sarakeIndex, uusiArvo) {
    cy.get(`[data-cy=${taulukonDataCy}]`)
        .should('be.visible')
        .contains('tbody tr', rivinTunniste)
        .find('input')
        .eq(sarakeIndex)
        .should('be.visible')
        .clear()
        .type(uusiArvo)
}

function tarkistaYhteensaSarake(taulukonDataCy, rivinTunniste, odotettuSumma, sarakeIndex) {
    cy.get(`[data-cy=${taulukonDataCy}]`)
        .should('be.visible')
        .contains('tbody tr', rivinTunniste)
        .closest('tr')
        .find('td')
        .eq(sarakeIndex)
        .should('contain', odotettuSumma);
}

describe('Tarjous-näkymä', function () {

    before(function () {
        alustaIvalonTarjousUrakka();
    });

    beforeEach(function () {
        avaaTarjousNakyma();
        
        // Odota että sivun perustiedot latautuvat
        cy.get('[data-cy=tarjous-erillishankinnat-grid]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-cy=tarjous-hankinnat-grid]', { timeout: 10000 }).should('be.visible');
        
        cy.intercept('POST', '_/tallenna-tarjouksen-tiedot').as('tallenna-tarjous');
        cy.intercept('POST', '_/hae-tarjouksen-tiedot').as('hae-tarjous');
    });

    describe('Perustoiminnallisuus', function () {

        it('Tarjous-näkymä latautuu oikein', function () {
            // Tarkista että kaikki pääkomponentit näkyvät
            cy.contains('Hankinnat').should('be.visible');
            cy.contains('Erillishankinnat').should('be.visible');
            cy.contains('Johto- ja hallintokorvaus').should('be.visible');
            cy.contains('Hoidonjohtopalkkio').should('be.visible');
            cy.contains('Tavoite- ja kattohinta').should('be.visible');

            // Tarkista että tallennuspainikkeet näkyvät
            cy.contains('button', 'Tallenna muutokset').should('be.visible');
            cy.contains('button', 'Tyhjennä').should('be.visible');

            // Tarkista että hoitovuosi-otsikot näkyvät oikein
            cy.contains('1. Hoitovuosi').should('be.visible');
            cy.contains('€ / hoitovuosi').should('be.visible');
            cy.contains('Yhteensä (€)').should('be.visible');
        });

        it('Hankinnat-grid toimii oikein', function () {
            // Tarkista että hankinnat-taulukko näkyy
            cy.get('.grid').should('contain', 'Hankinnat');
            
            // Varmista että löytyy odotettuja rivejä
            cy.contains('Kilpailutettavat hankinnat').should('be.visible');
            cy.contains('Äkilliset hoitotyöt').should('be.visible');
            cy.contains('Vahinkojen korjaukset').should('be.visible');
        });

    });

    describe('Erillishankinnat-gridin testit', function () {

        it('Erillishankinnat-grid näkyy oikein', function () {
            // Tarkista että erillishankinnat-taulukko näkyy
            cy.get('.grid').should('contain', 'Erillishankinnat');
            
            // Tarkista että € / hoitovuosi kenttä on muokattavissa
            cy.contains('tbody tr', 'Erillishankinnat')
                .find('input[type="text"]')
                .should('be.visible')
                .should('not.be.disabled');
        });

        it('Erillishankinnat tallennus toimii', function () {
            const eperhoitovuosiArvo = '2500';

            // Muokkaa erillishankintojen arvoa
            muokkaaTarjousRiviaArvo('tarjous-erillishankinnat-grid', 'Erillishankinnat', 0, eperhoitovuosiArvo);
            
            // Tallenna muutokset
            cy.contains('button', 'Tallenna muutokset').click();
            
            // Tarkista että tallennuskutsu tehdään
            cy.wait('@tallenna-tarjous')
                .its('response.statusCode')
                .should('equal', 200);

            // Tarkista että success-viesti näkyy tai arvo säilyy
            cy.reload();
            cy.wait('@hae-tarjous');
            
            // Varmista että tallennettu arvo säilyy
             tarkistaTarjousRivinArvo('tarjous-erillishankinnat-grid', 'Erillishankinnat', 4, '2 500,00');
        });

    });

});