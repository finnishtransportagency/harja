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
            DELETE
            FROM urakka_tehtavamaara
            WHERE urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');

            DELETE
            FROM sopimuksen_tehtavamaarat_tallennettu
            WHERE urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}');

            DELETE
            FROM sopimus_tehtavamaara
            WHERE urakka = (SELECT id FROM urakka WHERE nimi = '${urakkaNimi}')
              AND tehtava IN (SELECT id
                              FROM tehtava
                              WHERE nimi IN ('Ise ohituskaistat', 'Pysäkkikatosten puhdistus',
                                             'Opastustaulun/-viitan uusiminen'));
        `;

        // Poista uudet rivit komennosta, ja pidä yllä oleva query on luettavana
        cy.exec(`${terminaaliKomento} psql -h localhost -U harja harja -c "${sql.replace(/\n/g, ' ')}"`).then((tulos) => {
            console.log("Kanta alustettu:", tulos.stdout);
        });
    });
}

describe('Tehtävä- ja määräluettelo -näkymän testaus', () => {
    let urakanAlkuvuosi = kuluvaHoitokausiAlkuvuosi(-2);
    const urakanNimi = 'Pellon MHU testiurakka (3. hoitovuosi)';

    function avaaTehtavaJaMaaraluettelo() {
        cy.viewport(1100, 2000)
        avaaHarjaTimeoutilla();

        cy.contains('.haku-lista-item', 'Lappi', {timeout: ladataanHarjaaTimeout}).click();
        cy.get('.ajax-loader', {timeout: 10000}).should('not.exist');
        cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimi, {timeout: ladataanHarjaaTimeout}).click();

        cy.get('[data-cy=tabs-taso1-Suunnittelu]', {timeout: ladataanHarjaaTimeout}).click();

        cy.intercept('POST', '_/hae-tehtavat-ja-maarat').as('hae');
        cy.get('[data-cy="tabs-taso2-Tehtava- ja maaraluettelo"]').click();
        cy.wait('@hae', {timeout: ladataanHarjaaTimeout});

        cy.get('img[src="images/ajax-loader.gif"]', {timeout: ladataanHarjaaTimeout}).should('not.exist');
        cy.get('[data-cy="tehtavat-ja-maarat-grid"]', {timeout: ladataanHarjaaTimeout}).should('be.visible');
    }

    before(() => {
        // Resetoidaan urakan kaikki tehtävämäärät.
        alustaKantaanTehtavatJaMaarat(urakanNimi);
    })

    beforeEach(() => {
        avaaTehtavaJaMaaraluettelo();
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

        cy.get('[data-cy="btn-muokkaa-sopimuksen-maaria"]').click();
        cy.get('table.grid')
            .contains('Ise ohituskaistat')
            .parent()
            .find('td.muokattava input')
            .clear()
            .type('10')
            .blur();

        // Tallennetaan muutokset
        cy.get('[data-cy="btn-tallenna-tehtavat-ja-maarat"]').click();
        cy.wait('@tallenna')
            .its('response.statusCode')
            .should('equal', 200);

        // Viesti onnistumisesta pitäisi näkyä
        cy.contains('tallennettiin', {timeout: clickTimeout}).should('be.visible');
        cy.get('[data-cy="btn-muokkaa-sopimuksen-maaria"]').should('be.visible');

    });

    it('Kopioi seuraaville vuosille', () => {
        cy.intercept('POST', '_/tallenna-tehtavat-ja-maarat').as('tallenna');

        cy.get('[data-cy="btn-muokkaa-sopimuksen-maaria"]').click();

        // Jos puuttuvia määriä on, asetetaan ne 0:ksi jotta kopiointi + valmiiksi onnistuu.
        cy.get('body').then(($body) => {
            const btn = $body.find('[data-cy="btn-aseta-puuttuvat-nollaksi"]');
            if (btn.length) {
                cy.wrap(btn).click();
            }
        })

        cy.get('table.grid')
            .contains('Ise ohituskaistat')
            .parent()
            .find('td.muokattava input')
            .clear()
            .type('43')
            .blur();

        // Kopiointi avaa varmistusdialogin
        cy.get('[data-cy="btn-kopioi-nyt"]').click();
        cy.contains('button', 'Kopioi ja merkitse valmiiksi').click();
        cy.wait('@tallenna')
            .its('response.statusCode')
            .should('equal', 200);

        // Viesti onnistumisesta pitäisi näkyä
        cy.contains('kopioitiin', {timeout: 4000}).should('be.visible');

    });

})
