import {kuluvaHoitovuosi} from "../support/apurit.js";

Cypress.on('uncaught:exception', (err, runnable) => {
  // returning false here prevents Cypress from
  // failing the test
  return false
})

describe('Tehtävämäärien syöttö ja käpistely', () => {
  before(() => {
    cy.visit('http://localhost:3000/#urakat/suunnittelu/tehtavat?&hy=13&u=32')
    cy.intercept('POST', '_/tehtavamaarat-hierarkiassa').as('tehtavamaarat')
    cy.wait('@tehtavamaarat')
    cy.viewport(1100, 2000)
  })

  it('Tarjousmäärän voi syöttää', () => {
    cy.intercept('POST', '_/tallenna-sopimuksen-tehtavamaara').as('sop1')
    cy.get('table.grid').contains('Ise ohituskaistat').parent().parent().find('td.muokattava').find('input').clear().type('666').blur()
    cy.get('table.grid').contains('Ennalta arvaamattomien kuljetusten avustaminen').parent().parent().find('td.muokattava').find('input').clear().type('666').blur()
  })

  it('Ei voi tallentaa keskeneräisenä', () => {
    cy.contains('Tallenna').should('be.disabled')
    cy.contains('Jotta voit tallentaa, syötä kaikkiin tehtäviin ensin määrät.').should('be.visible')
  })

  it('Voi suunnitella eri määrät eri vuosille', () => {
    cy.viewport(1100, 2000)
    cy.intercept('POST', '_/tallenna-sopimuksen-tehtavamaara').as('sop1')
    cy.get('table.grid').contains('Opastustaulun/-viitan uusiminen').parent().parent().find('td.vetolaatikon-tila.klikattava').click()
    cy.get('table.grid').contains('Haluan syöttää joka vuoden erikseen').parent().parent().find('input.vayla-checkbox').click()
    cy.get('table.grid').find('input#vetolaatikko-input-opastustaulun\\/-viitan-uusiminen-2020').should('not.be.disabled')
    cy.get('table.grid').find('input#vetolaatikko-input-opastustaulun\\/-viitan-uusiminen-2020').clear({force:true}).type('661', {force:true}).blur()
    cy.wait('@sop1')
    cy.get('table.grid').find('input#vetolaatikko-input-opastustaulun\\/-viitan-uusiminen-2021').clear().type('662').blur()
    cy.wait('@sop1')    
    cy.get('table.grid').find('input#vetolaatikko-input-opastustaulun\\/-viitan-uusiminen-2022').clear().type('663').blur()
    cy.wait('@sop1')
    cy.get('table.grid').find('input#vetolaatikko-input-opastustaulun\\/-viitan-uusiminen-2023').clear().type('664').blur()
    cy.wait('@sop1')
    cy.get('table.grid').find('input#vetolaatikko-input-opastustaulun\\/-viitan-uusiminen-2024').clear().type('665').blur()
    cy.wait('@sop1')
  })

  it('Voi tallentaa kun kaikki syötetty', () => {
    cy.intercept('POST', '_/tallenna-sopimuksen-tila').as('tila')
    cy.contains('Tallenna').click()
    cy.wait('@tila')
    cy.contains('Jotta voit tallentaa, syötä kaikkiin tehtäviin ensin määrät. Jos sopimuksessa ei ole määriä kyseiselle tehtävälle, syötä ').should('not.exist')
  })

  it('Määrän voi syöttää', () => {
      cy.intercept('POST', '_/tallenna-tehtavamaarat').as('tehtavamaarat')
      cy.get('table.grid').contains('Ise 2-ajorat').parent().parent().find('td.muokattava').find('input').clear().type('666').blur()
      cy.wait('@tehtavamaarat')
  })

  it('Toimenpidettä voi vaihtaa', () => {
    cy.get('div.select-default').first().find('button').click()    
    cy.get('.harja-alasvetolistaitemi').contains('3 SORATEIDEN HOITO').click()
    cy.wait(2000)
    cy.get('table.grid').contains('Ise 2-ajorat').should('not.exist')
  })

  it('Hoitokautta voi vaihtaa', () => {
    let hoitokausiNyt = "3. hoitovuosi (2022—2023)";
    let hoitokausiViimeinen = "4. hoitovuosi (2023—2024)";

    cy.intercept('POST', '_/tehtavamaarat-hierarkiassa').as('tehtavamaarat')
    cy.get('table.grid').contains('Sorateiden pölynsidonta (materiaali)').parent().parent().find('td.muokattava').find('input').clear().type('667')
    cy.get('div.select-default').contains(hoitokausiNyt).click()
    cy.contains(hoitokausiViimeinen).click()
    cy.get('div.select-default').contains(hoitokausiViimeinen, {timeout: 2000}).should('be.visible');

    cy.get('table.grid').contains('667').should('not.exist')
    cy.get('div.select-default').contains(hoitokausiViimeinen).click()
    cy.get('.harja-alasvetolistaitemi').contains(hoitokausiNyt).click()
    cy.get('div.select-default').contains(hoitokausiNyt, {timeout: 2000}).should('be.visible');

    cy.get('table.grid').contains('Sorateiden pölynsidonta (materiaali)').parent().parent().find('td.muokattava').find('input').should('have.value', '667')
  })

  it('Määrän voi vaihtaa', () => {
    cy.intercept('POST', '_/tehtavamaarat-hierarkiassa').as('tehtavamaarat')
    cy.get('div.select-default').first().find('button').click()
    cy.get('.harja-alasvetolistaitemi').contains('1.0 TALVIHOITO').click()
    cy.wait(2000)
    cy.get('table.grid').contains('Ise 2-ajorat').parent().parent().find('td.muokattava').find('input').should('have.value', 666)
    cy.get('table.grid').contains('Ise 2-ajorat').parent().parent().find('td.muokattava').find('input').type('9').blur()
    cy.get('table.grid').contains('Ise 2-ajorat').parent().parent().find('td.muokattava').find('input').should('have.value', 6669)
  })

  after(() => {
    cy.intercept('POST', '_/tallenna-tehtavamaarat').as('tallennatehtavamaarat')
    cy.get('div.select-default').first().find('button').click()
    cy.get('.harja-alasvetolistaitemi').contains('0 KAIKKI').click()
    cy.wait(1000)
      cy.get('table.grid').contains('Ise 2-ajorat').parent().parent().find('td.muokattava').find('input').clear().blur()
    cy.wait('@tallennatehtavamaarat')
    cy.wait(1000)
    cy.get('div.select-default').first().find('button').click()    
    cy.get('.harja-alasvetolistaitemi').contains('3 SORATEIDEN HOITO').click()
    cy.wait(1000)
    cy.get('table.grid').contains('Sorateiden pölynsidonta (materiaali)').parent().parent().find('td.muokattava').find('input').clear().blur()
  })
})
