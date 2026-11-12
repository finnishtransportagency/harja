import {
    kuluvaHoitokausiAlkuvuosi,
    ladataanHarjaaTimeout,
    clickTimeout,
    avaaHarjaTimeoutilla
} from "../support/apurit.js";


function alustaKantaanTehtavatJaMaarat(urakkaNimi) {
    // Poista urakalta kaikki vuosittaiset suunnitelmat ja tehtävämäärät
    cy.terminaaliKomento().then((terminaaliKomento) => {
        const sql = `
            -- Varmista, että urakalla on sopimuksen tehtävämäärät olemassa (testidatan varaan ei kannata luottaa)
            SELECT luo_kaikille_tehtaville_testitarjousmaarat('${urakkaNimi}', 1100);

            DELETE
            FROM urakka_tehtavamaara
            WHERE urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');

            DELETE
            FROM sopimuksen_tehtavamaarat_tallennettu
            WHERE urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');
        `;

        // Poista uudet rivit komennosta, ja pidä yllä oleva query on luettavana
        cy.exec(`${terminaaliKomento} psql -h localhost -U harja harja -c "${sql.replace(/\n/g, ' ')}"`).then((tulos) => {
            console.log("Kanta alustettu:", tulos.stdout);
        });
    });
}

describe('Tehtävä- ja määräluettelo -näkymän testaus', () => {
    let urakanAlkuvuosi = kuluvaHoitokausiAlkuvuosi(-2);

    before(() => {
        // Resetoidaan urakan kaikki tehtävämäärät.
        alustaKantaanTehtavatJaMaarat('Rovaniemen MHU testiurakka (1. hoitovuosi)');
        avaaHarjaTimeoutilla();

        cy.get('[data-cy="haku-lista-item"]').contains('Lappi', {timeout: ladataanHarjaaTimeout}).click();
        cy.get('.ajax-loader', {timeout: 10000}).should('not.exist');
        cy.contains('[data-cy=urakat-valitse-urakka] button', 'Rovaniemen MHU testiurakka (1. hoitovuosi)', {timeout: ladataanHarjaaTimeout}).click();
        // Mene suunnittelu välilehdelle
        cy.get('[data-cy=tabs-taso1-Suunnittelu]', {timeout: ladataanHarjaaTimeout}).click();
        // Avaa Tehtävä- ja määräluettelo -välilehti
        cy.get('[data-cy="tabs-taso2-Tehtava- ja maaraluettelo"]').click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: ladataanHarjaaTimeout}).should('not.exist');

        // Varmista että näkymä on varmasti valmis ennen testejä
        cy.get('h1', {timeout: ladataanHarjaaTimeout}).contains('Tehtävä ja määräluettelo').should('be.visible');
        cy.get('table.grid', {timeout: ladataanHarjaaTimeout}).should('exist');
        cy.get('[data-cy="btn-muokkaa-sopimuksen-maaria"]', {timeout: ladataanHarjaaTimeout}).should('exist');
    })

    it('Avaa tehtävä- ja määräluettelo', () => {
        cy.get('h1').contains('Tehtävä ja määräluettelo').should('be.visible');
        cy.get('span').contains('Pysyvät muutokset sopimuksen määriin kirjataan muutokset-sivulla.').should('be.visible');

        cy.get('table.grid').contains('Ise 2-ajorat.').should('be.visible');
        cy.get('table.grid').contains('Ise 1-ajorat.').should('be.visible');
        cy.get('table.grid').contains('Ise rampit').should('be.visible');
        cy.get('table.grid').contains('1.0 TALVIHOITO').should('be.visible');
        cy.get('table.grid').contains('2.1 LIIKENNEYMPÄRISTÖN HOITO / Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen').should('be.visible');

    });

    it('Muokkaa alkuperäisen sopimuksen määriä', () => {
        cy.intercept('POST', '_/tallenna-tehtavat-ja-maarat').as('tallenna');

        cy.get('[data-cy="btn-muokkaa-sopimuksen-maaria"]', {timeout: ladataanHarjaaTimeout}).should('be.visible').click();

        // Käytetään tehtävää, jonka olemassaoloon ei liity erillistä testidata-oletusta
        cy.get('table.grid', {timeout: ladataanHarjaaTimeout})
            .contains('Ise rampit', {timeout: clickTimeout})
            .parents('tr')
            .first()
            .find('td.muokattava input')
            .clear()
            .type('10');

        // Tallennetaan muutokset
        cy.get('div.painikkeet button').contains('Tallenna').click();
        cy.wait('@tallenna')
            .its('response.statusCode')
            .should('equal', 200);

        // Viesti onnistumisesta pitäisi näkyä
        cy.get('div').contains('Tiedot tallennettiin onnistuneesti.', {timeout: clickTimeout}).should('be.visible');

    });

    it('Kopioi seuraaville vuosille', () => {
        cy.intercept('POST', '_/tallenna-tehtavat-ja-maarat').as('tallenna');

        // Valitse ensimmäinen hoitovuosi
        cy.get('div.label-ja-alasveto.hoitokausi div.dropdown').eq(0).within(() => {
            cy.get('button').click({force: true});
            cy.contains('1. hoitovuosi').click();
        });

        cy.get('[data-cy="btn-muokkaa-sopimuksen-maaria"]', {timeout: ladataanHarjaaTimeout}).should('be.visible').click();
        cy.get('table.grid', {timeout: ladataanHarjaaTimeout})
            .contains('Ise rampit', {timeout: clickTimeout})
            .parents('tr')
            .first()
            .find('td.muokattava input')
            .clear()
            .type('43');

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
        cy.get('table.grid', {timeout: ladataanHarjaaTimeout})
            .contains('Ise rampit', {timeout: clickTimeout})
            .parents('tr')
            .first()
            .find('td.ei-muokattava')
            .contains('43');

    });

})
