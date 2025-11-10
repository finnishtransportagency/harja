import {kuluvaHoitokausiAlkuvuosi} from "../support/apurit.js";


const ladataanHarjaaTimeout = 3000;

function alustaKantaanTehtavatJaMaarat(urakkaNimi) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        // Poista urakalta kaikki vuosittaiset suunnitelmat urakka_tehtavamaara taulusta
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM urakka_tehtavamaara ut " +
            ` WHERE ut.urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista urakalta kaikki vuosittaiset suunnitelmat urakka_tehtavamaara taulusta:", tulos)
            });
        // Poista tiedot, että onko tarjousta/sopimusta syötetty
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM sopimuksen_tehtavamaarat_tallennettu stt " +
            ` WHERE stt.urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista tiedot, että onko tarjousta/sopimusta syötetty:", tulos)
            });

        // Poista muutamalta tehtävältä tarjoustieto
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM sopimus_tehtavamaara where tehtava = (select id from tehtava where nimi = 'Ise ohituskaistat') " +
            ` AND urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista tarjoussumma tehtavalta:", tulos)
            });
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM sopimus_tehtavamaara where tehtava = (select id from tehtava where nimi = 'Pysäkkikatosten puhdistus') " +
            ` AND urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista tarjoussumma tehtavalta:", tulos)
            });
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM sopimus_tehtavamaara where tehtava = (select id from tehtava where nimi = 'Opastustaulun/-viitan uusiminen') " +
            ` AND urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');\"`)
            .then((tulos) => {
                console.log("Poista tarjoussumma tehtavalta:", tulos)
            });
    });
}
describe('Tehtävä- ja määräluettelo -näkymän testaus', () => {
    let urakanAlkuvuosi = kuluvaHoitokausiAlkuvuosi(-2);

    before(() => {
        // Resetoidaan urakan kaikki tehtävämäärät.
        alustaKantaanTehtavatJaMaarat('Pellon MHU testiurakka (3. hoitovuosi)');
        cy.visit("/");

        // Varmista, että pääsivu on ladattu ennen testien aloitusta
        cy.get('.ladataan-harjaa', { timeout: ladataanHarjaaTimeout }).should('not.exist')
        cy.contains('.haku-lista-item', 'Lappi', {timeout: 30000}).click();
        cy.get('.ajax-loader', {timeout: 10000}).should('not.exist');
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Pellon MHU testiurakka (3. hoitovuosi)', {timeout: 10000}).click();
        // Mene suunnittelu välilehdelle
        cy.get('[data-cy=tabs-taso1-Suunnittelu]', {timeout: 20000}).click();
        // Avaa Tehtävä- ja määräluettelo -välilehti
        cy.get('[data-cy="tabs-taso2-Tehtava- ja maaraluettelo"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');
    })

    it('Avaa tehtävä- ja määräluettelo', () => {
        cy.get('h1').contains('Tehtävät ja määrät').should('be.visible');
        cy.get('span').contains('Sovitut muutokset alkuperäisiin sopimuksen tehtävämääriin kirjataan muutokset-sivulla.').should('be.visible');

        cy.get('table.grid').contains('Ise 2-ajorat.').should('be.visible');
        cy.get('table.grid').contains('Ise 1-ajorat.').should('be.visible');
        cy.get('table.grid').contains('Ise rampit').should('be.visible');
        cy.get('table.grid').contains('1.0 TALVIHOITO').should('be.visible');
        cy.get('table.grid').contains('2.1 LIIKENNEYMPÄRISTÖN HOITO / Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen').should('be.visible');

    });

    it('Muokkaa sopimuksen määriä', () => {
        cy.intercept('POST', '_/tallenna-tehtavat-ja-maarat').as('tallenna');

        cy.get('[data-cy="btn-muokkaa-sopimuksen-maaria"]').click();
        cy.get('table.grid').contains('Ise ohituskaistat').parent().find('td.muokattava').find('input').clear().type('10');

        // Tallennetaan muutokset
        cy.get('div.painikkeet button').contains('Tallenna').click();
        cy.wait('@tallenna')
            .its('response.statusCode')
            .should('equal', 200);

        // Viesti onnistumisesta pitäisi näkyä
        cy.get('div').contains('Tiedot tallennettiin onnistuneesti.', {timeout: 4000}).should('be.visible');

    });

    it('Kopioi seuraaville vuosille', () => {
        cy.intercept('POST', '_/tallenna-tehtavat-ja-maarat').as('tallenna');

        // Valitse ensimmäinen hoitovuosi
        cy.get('div.label-ja-alasveto.hoitokausi div.dropdown').eq(0).within(() => {
            cy.get('button').click({force: true});
            cy.contains('1. hoitovuosi').click();
        });

        cy.get('[data-cy="btn-muokkaa-sopimuksen-maaria"]').click();
        cy.get('table.grid').contains('Ise ohituskaistat').parent().find('td.muokattava').find('input').clear().type('43');

        // Tallennetaan muutokset
        cy.get('[data-cy="btn-kopioi-tuleville-hoitovuosille"]').click();
        cy.wait('@tallenna')
            .its('response.statusCode')
            .should('equal', 200);

        // Viesti onnistumisesta pitäisi näkyä
        cy.contains('Tiedot tallennettiin onnistuneesti.', {timeout: 4000}).should('be.visible');

        // Tarkista, että viidennellä hoitovuodella on sama summa
        cy.get('div.label-ja-alasveto.hoitokausi div.dropdown').eq(0).within(() => {
            cy.get('button').click({force: true});
            cy.contains('5. hoitovuosi').click();
        });
        cy.get('table.grid').contains('Ise ohituskaistat').parent().find('td.ei-muokattava').contains('43');

    });

})
