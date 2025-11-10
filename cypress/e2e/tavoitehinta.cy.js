import * as ks from "../support/kustannussuunnitelmaFns.js";

const clickTimeout = 6000;
const visibleTimeout = 30000;
const ladataanHarjaaTimeout = 30000;
const urakanNimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)';

// Alustetaan yllänimetty urakka Kustannussuunnittelua varten
function alustaUrakkaKustannussuunnitteluun(nimi) {
    ks.alustaKanta(nimi);
}

function alustaIin21TarjousUrakka() {
    ks.alustaKanta('Iin MHU 2021-2026');
}

function trimmaaArvo(arvo) {
    // Poistaa ylimääräiset välilyönnit ja trimmauksella
    return arvo.toString().replace(/\s+/g, ' ').replace('€', '').replace(' ', '').replace(',', '.').trim();
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

describe('Varmista Hoitovuoden alun tavoitehinta', function () {
    beforeEach(function () {
        cy.viewport(1100, 2000)
        cy.visit("/")
        // Varmista, että pääsivu on ladattu ennen testien aloitusta
        cy.get('.ladataan-harjaa', {timeout: ladataanHarjaaTimeout}).should('not.exist')
    })

    it("Urakkavalinta listan kautta toimii", function () {
        alustaUrakkaKustannussuunnitteluun('Rovaniemen MHU testiurakka (1. hoitovuosi)');
        cy.intercept('POST', '_/tallenna-kilpailutettavat-hankinnat').as('tallenna-kilpailutettavat-hankinnat');
        cy.intercept('POST', '_/tallenna-erillishankinnat').as('tallenna-erillishankinnat');
        cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset-2025').as('tallenna-toimenkuvat-2025');
        cy.intercept('POST', '_/tallenna-hoidonjohtopalkkiot').as('tallenna-hoidonjohtopalkkiot');
        cy.intercept('POST', '_/vahvista-tavoite-ja-kattohinta').as('vahvista-tavoite-ja-kattohinta');
        cy.intercept('POST', '_/tallenna-tarjouksen-tiedot').as('tallenna-tarjous');
        cy.intercept('POST', '_/hae-tarjouksen-tiedot').as('hae-tarjous');

        cy.contains('.haku-lista-item', 'Lappi').click()
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Rovaniemen MHU testiurakka (1. hoitovuosi)', {timeout: clickTimeout}).click()

        cy.get('[data-cy=tabs-taso1-Suunnittelu]').click();

        // Tallenna ensimmäisenä tarjoukseen jotain, koska muuten tavoitehinnan vahvistus ei onnistu
        cy.get('[data-cy="tabs-taso2-Tarjouksen tiedot"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');

        // Tallenna jotain kipailutettaviin hankintoihin
        muokkaaTarjousRiviaArvo('tarjous-hankinnat-grid', 'Kilpailutettavat hankinnat', 0, 10);
        // Tallenna muutokset
        cy.contains('button', 'Tallenna muutokset').click();

        // Tarkista että tallennuskutsu tehdään
        cy.wait('@tallenna-tarjous')
            .its('response.statusCode')
            .should('equal', 200);
        
        // Siirry suunnittelu / Hoitovuoden alun tavoitehinta tabille

        cy.get('[data-cy="tabs-taso2-Hoitovuoden alun tavoitehinta"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');

        // Valitse ensimmäinen hoitovuosi
        cy.get('div.label-ja-alasveto.hoitokausi div.dropdown').eq(0).within(() => {
            cy.get('button').click({force: true});
            cy.contains('1. hoitovuosi').click();
        });


        cy.get('#kilpailutettavat-hankinnat-elementti table.grid').gridOtsikot().then(() => {

            let valitseHankinnatInput = function (rivi) {
                return `#kilpailutettavat-hankinnat-elementti table.grid tbody tr:nth-child(${rivi + 1}) td input`;
            };

            // Muokataan Talvihoito laaja TPI
            cy.get(valitseHankinnatInput(1)).eq(0).clear().type('10');

            // Yhteensä ja Kirjaamatta teksti näkyy ja summat on oikein
            cy.get('#kilpailutettavat-hankinnat-elementti table.grid tbody').contains('Yhteensä').next().next().contains('10,00');
        });

        // Tallennetaan Kilpailutettavat hankinnat tietokantaan
        cy.get('#kilpailutettavat-hankinnat-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-kilpailutettavat-hankinnat')
            .its('response.statusCode')
            .should('equal', 200);

        // Viesti onnistumisesta pitäisi näkyä
        cy.contains('Kilpailutettavat hankinnat tallennettiin.', {timeout: 4000}).should('be.visible');

        // Erillishankinnat
        cy.get('#erillishankinnat-elementti table.grid').gridOtsikot().then(() => {
            let valitseErillishankinnatInput = function (rivi) {
                return `#erillishankinnat-elementti table.grid tbody tr:nth-child(${rivi + 1}) td input`;
            };

            // Muokataan Lokakuu 2025
            cy.get(valitseErillishankinnatInput(1)).eq(0).clear().type('20');
            // Yhteensä ja Kirjaamatta teksti ja summat täsmää
            cy.get('#erillishankinnat-elementti table.grid tbody')
                .contains('Yhteensä').next().contains('20,00');
        });

        // Tallennetaan Erillishnakinnat tietokantaan
        cy.get('#erillishankinnat-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-erillishankinnat')
            .its('response.statusCode')
            .should('equal', 200);

        // Viesti onnistumisesta pitäisi näkyä
        cy.contains('Erillishankinnat tallennettiin onnistuneesti.', {timeout: 4000}).should('be.visible');


        // Johto ja hallintokorvaukset
        cy.get('#johto-ja-hallintokorvaus-elementti table.grid').gridOtsikot().then(() => {

            let valitseJJHInput = function (rivi) {
                return `#johto-ja-hallintokorvaus-elementti table.grid tbody tr:nth-child(${rivi + 1}) td input`;
            };

            // Muokataan Lokakuu 2024
            cy.get(valitseJJHInput(1)).eq(0).clear().type('30');

        });

        // Tallennetaan johto ja hallintokorvaukset
        cy.get('#johto-ja-hallintokorvaus-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-toimenkuvat-2025')
            .its('response.statusCode')
            .should('equal', 200);

        // Hoidonjohtopalkkiot
        cy.get('#hoidonjohtopalkkio-elementti table.grid').gridOtsikot().then(() => {
            let valitseHoidonjohtopalkkioInput = function (rivi) {
                return `#hoidonjohtopalkkio-elementti table.grid tbody tr:nth-child(${rivi + 1}) td input`;
            };

            // Muokataan Lokakuu 2025
            cy.get(valitseHoidonjohtopalkkioInput(1)).eq(0).clear().type('40');


            // Yhteensä ja Kirjaamatta teksti ja summat täsmää
            cy.get('#hoidonjohtopalkkio-elementti table.grid tbody')
                .contains('Yhteensä').next().contains('40,00');
        });

        // Tallennetaan muutokset
        cy.get('#hoidonjohtopalkkio-elementti').contains('Tallenna tiedot').click();
        cy.wait('@tallenna-hoidonjohtopalkkiot')
            .its('response.statusCode')
            .should('equal', 200);

        // Viesti onnistumisesta pitäisi näkyä
        cy.contains('Hoidonjohtopalkkiot tallennettiin.', {timeout: 4000}).should('be.visible');

        // Vahvista tavoitehinta
        cy.get('button.nappi-ensisijainen[type="button"]').contains('Vahvista tavoite- ja kattohinta').click();
        cy.wait('@vahvista-tavoite-ja-kattohinta')
            .its('response.statusCode')
            .should('equal', 200);

        // Etsitään hoitovuoden alun tavoitehinta summa
        let tavoihinta =
            cy.get('div #tavoite-ja-kattohinta-elementti div')
                .contains('Hoitovuoden alun tavoitehinta').next().then(function(text1){
                return text1.text();
            });

        let indeksikorjattuTavoihinta =
            cy.get('div #tavoite-ja-kattohinta-elementti div')
                .contains('Hoitovuoden alun indeksikorjattu tavoitehinta').next().then(function(text1){
            return text1.text();
        });

        // Indeksikorjattu Tavoitehinta muutoksista
        cy.get('[data-cy=tabs-taso1-Muutokset]').click();
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        // varmista
        cy.get('div.muutosten-vaikutus div.tietoja.muutosten-vaikutus-container span')
            .contains('Hoitovuoden alun indeksikorjattu tavoitehinta').next().then(function(text1){
            const trimmattuTodellinen = trimmaaArvo(text1);
            const trimmattuOdotettu = trimmaaArvo(indeksikorjattuTavoihinta);
            expect(trimmattuTodellinen).to.equal(trimmattuOdotettu);
        });

        // Indeksikorjattu Tavoitehinta välikatselmuksesta
        cy.get('[data-cy=tabs-taso1-Valikatselmus]').click();
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        // varmista
        cy.get('div.valikatselmus-yhteenveto div.summa-rivi-ylin span.laskenta-rivi-lukema')
            .then(function(text1){
            const trimmattuTodellinen = trimmaaArvo(text1);
            const trimmattuOdotettu = trimmaaArvo(indeksikorjattuTavoihinta);
            expect(trimmattuTodellinen).to.equal(trimmattuOdotettu);
        });

        // Tavoitehinta kustannusten seurannassa
        cy.get('[data-cy=tabs-taso1-Kulut]').click();
        cy.get('[data-cy="tabs-taso2-Kustannusten seuranta"]').click();
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        // varmista
        cy.get('div.yhteenveto div.rivi span').contains('Tavoitehinta (indeksikorjattu)').next().then(function(text1){
                const trimmattuTodellinen = trimmaaArvo(text1);
                const trimmattuOdotettu = trimmaaArvo(indeksikorjattuTavoihinta);
                expect(trimmattuTodellinen).to.equal(trimmattuOdotettu);
            });

        // Tavoitehinta Laskutusyhteenvedossa
        cy.get('[data-cy=tabs-taso1-Kulut]').click();
        cy.get('[data-cy="tabs-taso2-Laskutusyhteenveto"]').click();
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist');

        // Valitse koko hoitokausi
        // Valitse ensimmäinen hoitovuosi
        cy.get('div.label-ja-alasveto.kuukausi div.dropdown').eq(0).within(() => {
            cy.get('button').click({force: true});
            cy.contains('Koko hoitokausi').click();
        });

        // varmista
        cy.get('span.varillinen-teksti').contains('Tavoitehinta (indeksikorjattu)').parent().parent().parent().next()
            .get('span.arvo')
            .then(function(text1){
            const trimmattuTodellinen = trimmaaArvo(text1);
            const trimmattuOdotettu = trimmaaArvo(indeksikorjattuTavoihinta);
            expect(trimmattuTodellinen).to.equal(trimmattuOdotettu);
        });



    });
});

