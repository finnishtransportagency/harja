import {kuluvaHoitokausiAlkuvuosi} from "../support/apurit.js";

function alustaKantaanTehtavatJaMaarat(urakkaNimi) {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        // Poista urakalta kaikki vuosittaiset suunnitelmat urakka_tehtavamaara taulusta
        console.log("Urakka: ", urakkaNimi);
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
    });
}

Cypress.on('uncaught:exception', (err, runnable) => {
    // returning false here prevents Cypress from
    // failing the test
    return false
})

describe('Tehtävämäärien syöttö ja käpistely', () => {
    let urakanAlkuvuosi = kuluvaHoitokausiAlkuvuosi(-2);

    before(() => {

        // Resetoidaan urakan 32 kaikki tehtävämäärät.
        alustaKantaanTehtavatJaMaarat('Pellon MHU testiurakka (3. hoitovuosi)');

        // Sivun tila on useiden lokaalitestien jälkeen väärässä kohdassa, joten joudumme käymään eri sivuilla nollataksemme tilateen
        cy.visit('http://localhost:3000/#urakat/suunnittelu/tehtavat?&hy=13&u=32');
        cy.intercept('POST', '_/hae-mhu-suunniteltavat-tehtavat').as('tehtavamaarat')
        cy.wait('@tehtavamaarat')
        cy.viewport(1100, 2000)
    })

    it('Tarjousmäärän voi syöttää', () => {
        cy.intercept('POST', '_/tallenna-sopimuksen-tehtavamaara').as('sop1')
        cy.get('h3').contains('Syötä tarjouksen määrät').should('be.visible');
        cy.get('table.grid').contains('Ise ohituskaistat').parent().find('td.muokattava').find('input').clear().type('666').blur()
        cy.get('table.grid').contains('Ennalta arvaamattomien kuljetusten avustaminen').parent().find('td.muokattava').find('input').clear().type('666').blur()
    })

    it('Ei voi tallentaa keskeneräisenä', () => {
        cy.get('table.grid').contains('Ise ohituskaistat').parent().find('td.muokattava').find('input').clear().type('0').clear().blur()
        cy.contains('Tallenna').should('be.disabled')
        cy.contains('Jotta voit tallentaa, syötä kaikkiin tehtäviin ensin määrät.').should('be.visible')
    })

    it('Voi suunnitella eri määrät eri vuosille', () => {
        cy.viewport(1100, 2000)
        cy.intercept('POST', '_/tallenna-sopimuksen-tehtavamaara').as('sop1')

        // Tämä conditionaalinen tarkistelu toimii. Tee checkboxille sama
        cy.get('table.grid').then(($ele) => {
            if ($ele.text().includes('Haluan syöttää joka vuoden erikseen')) {
                // Käytä check() koska useammalla peräkkäisellä ajolla checkbox voi olla valittuna ja check varmistaa, että se on valittuna
                cy.get('table.grid').contains('Haluan syöttää joka vuoden erikseen').parent().find('input.vayla-checkbox').check();
            } else {
                console.log('Ei ole auki. avataan.');
                cy.get('table.grid').contains('Päällystettyjen teiden palteiden poisto').parent().find('button.vetolaatikon-sailio').click()
                // Käytä check() koska useammalla peräkkäisellä ajolla checkbox voi olla valittuna ja check varmistaa, että se on valittuna
                cy.get('table.grid').contains('Haluan syöttää joka vuoden erikseen').parent().find('input.vayla-checkbox').check();
            }

            cy.get('table.grid').find('input#vetolaatikko-input-päällystettyjen-teiden-palteiden-poisto-' + urakanAlkuvuosi).should('not.be.disabled')
            cy.get('table.grid').find('input#vetolaatikko-input-päällystettyjen-teiden-palteiden-poisto-' + (urakanAlkuvuosi + 0)).clear({force: true}).type('661', {force: true}).blur()
            cy.wait('@sop1')
            cy.get('table.grid').find('input#vetolaatikko-input-päällystettyjen-teiden-palteiden-poisto-' + (urakanAlkuvuosi + 1)).clear().type('662').blur()
            cy.wait('@sop1')
            cy.get('table.grid').find('input#vetolaatikko-input-päällystettyjen-teiden-palteiden-poisto-' + (urakanAlkuvuosi + 2)).clear().type('663').blur()
            cy.wait('@sop1')
            cy.get('table.grid').find('input#vetolaatikko-input-päällystettyjen-teiden-palteiden-poisto-' + (urakanAlkuvuosi + 3)).clear().type('664').blur()
            cy.wait('@sop1')
            cy.get('table.grid').find('input#vetolaatikko-input-päällystettyjen-teiden-palteiden-poisto-' + (urakanAlkuvuosi + 4)).clear().type('665').blur()
            cy.wait('@sop1')

        })
    })

    it('Voi tallentaa kun kaikki syötetty', () => {
        cy.intercept('POST', '_/tallenna-sopimuksen-tila').as('tila')
        cy.get('table.grid').contains('Ise ohituskaistat').parent().find('td.muokattava').find('input').clear().type('555').blur()
        cy.contains('Tallenna').click()
        cy.wait('@tila')
        cy.contains('Syötä kaikkiin tehtäviin määrät. Jos sopimuksessa ei ole määriä kyseiselle tehtävälle, syötä').should('not.exist')
    })

    it('Määrän voi syöttää', () => {
        cy.intercept('POST', '_/tallenna-tehtavamaarat').as('tehtavamaarat')
        cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').clear().type('666').blur()
        cy.wait('@tehtavamaarat')
    })

    it('Toimenpidettä voi vaihtaa', () => {
        cy.get('div.select-default').first().find('button').click()
        cy.get('.harja-alasvetolistaitemi').contains('3 SORATEIDEN HOITO').click()
        cy.wait(2000)
        cy.get('table.grid').contains('Ise 2-ajorat').should('not.exist')
    })

    it('Hoitokautta voi vaihtaa', () => {
        let hoitokausiNyt = "3. hoitovuosi (" + (urakanAlkuvuosi + 2) + "—" + (urakanAlkuvuosi + 3) + ")";
        let hoitokausiViimeinen = "4. hoitovuosi (" + (urakanAlkuvuosi + 3) + "—" + (urakanAlkuvuosi + 4) + ")";

        cy.intercept('POST', '_/hae-mhu-suunniteltavat-tehtavat').as('tehtavamaarat')
        cy.get('table.grid').contains('Sorateiden pölynsidonta (materiaali)').parent().find('td.muokattava').find('input').clear().type('777')
        cy.get('div.select-default').contains(hoitokausiNyt).click()
        cy.contains(hoitokausiViimeinen).click()
        cy.get('div.select-default').contains(hoitokausiViimeinen, {timeout: 2000}).should('be.visible');

        //cy.get('table.grid').contains('667').should('not.exist')
        cy.get('div.select-default').contains(hoitokausiViimeinen).click()
        cy.get('.harja-alasvetolistaitemi').contains(hoitokausiNyt).click()
        cy.get('div.select-default').contains(hoitokausiNyt, {timeout: 2000}).should('be.visible');

        cy.get('table.grid').contains('Sorateiden pölynsidonta (materiaali)').parent().find('td.muokattava').find('input').should('not.have.value');
    })

    it('Määrän voi vaihtaa', () => {
        cy.intercept('POST', '_/hae-mhu-suunniteltavat-tehtavat').as('tehtavamaarat')
        cy.get('div.select-default').first().find('button').click()
        cy.get('.harja-alasvetolistaitemi').contains('1.0 TALVIHOITO').click()
        cy.wait(2000)
        cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').should('not.have.value', 666)
        cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').type('777').blur()
        cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').should('have.value', 777)
    })

    after(() => {
        cy.intercept('POST', '_/tallenna-tehtavamaarat').as('tallennatehtavamaarat')
        cy.get('div.select-default').first().find('button').click()
        cy.get('.harja-alasvetolistaitemi').contains('0 KAIKKI').click()
        cy.wait(1000)
        cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').clear().blur()
        cy.wait('@tallennatehtavamaarat')
        cy.wait(1000)
        cy.get('div.select-default').first().find('button').click()
        cy.get('.harja-alasvetolistaitemi').contains('3 SORATEIDEN HOITO').click()
        cy.wait(1000)
        cy.get('table.grid').contains('Sorateiden pölynsidonta (materiaali)').parent().find('td.muokattava').find('input').clear().blur()
    })
})
