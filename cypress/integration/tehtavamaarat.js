Cypress.on('uncaught:exception', (err, runnable) => {
  // returning false here prevents Cypress from
  // failing the test
  return false
})

describe('Tehtävämäärien syöttö ja käpistely', () => {
  before(() => {
    cy.server()
   // cy.visit('/')
    cy.visit('http://localhost:3000/#urakat/suunnittelu/tehtavat?&hy=13&u=32')
    //cy.route('POST', '_/tehtavat').as('tehtavat')
cy.route('POST', '_/tehtavamaarat-hierarkiassa').as('tehtavamaarat')
    //cy.wait('@tehtavat')
    cy.wait('@tehtavamaarat')
    cy.viewport(1100, 2000)
  })

  it('Tarjousmäärän voi syöttää', () => {
    cy.server()
    cy.route('POST', '_/tallenna-sopimuksen-tehtavamaara').as('sop1')

    cy.get('table.grid').contains('Ise ohituskaistat').parent().find('td.muokattava').find('input').clear().type('666').blur()
    cy.wait('@sop1')
    cy.get('table.grid').contains('Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu').parent().find('td.muokattava').find('input').clear().type('666').blur()
    cy.wait('@sop1')
  })

  it('Ei voi tallentaa keskeneräisenä', () => {
    cy.server()
    cy.route('POST', '_/tallenna-sopimuksen-tila').as('tila')
    cy.contains('Tallenna').click()
    cy.contains('Syötä kaikkiin tehtäviin määrät. Jos sopimuksessa ei ole määriä kyseiselle tehtävälle, syötä').should('be.visible')
  })

  it('Voi suunnitella eri määrät eri vuosille', () => {
    cy.viewport(1100, 2000)
    cy.server()
    cy.route('POST', '_/tallenna-sopimuksen-tehtavamaara').as('sop1')
    cy.get('table.grid').contains('K2').parent().find('td.vetolaatikon-tila.klikattava').click()
    cy.get('table.grid').contains('Haluan syöttää joka vuoden erikseen').parent().find('input.vayla-checkbox').click()
    cy.get('table.grid').find('input#vetolaatikko-input-K2-2019').should('not.be.disabled')
    cy.get('table.grid').find('input#vetolaatikko-input-K2-2019').clear({force:true}).type('661', {force:true}).blur()
    cy.wait('@sop1')
    cy.get('table.grid').find('input#vetolaatikko-input-K2-2020').clear().type('662').blur()
    cy.wait('@sop1')    
    cy.get('table.grid').find('input#vetolaatikko-input-K2-2021').clear().type('663').blur()
    cy.wait('@sop1')
    cy.get('table.grid').find('input#vetolaatikko-input-K2-2022').clear().type('664').blur()
    cy.wait('@sop1')
    cy.get('table.grid').find('input#vetolaatikko-input-K2-2023').clear().type('665').blur()
    cy.wait('@sop1')
  })

  it('Voi tallentaa kun kaikki syötetty', () => {
    cy.server()
    cy.route('POST', '_/tallenna-sopimuksen-tila').as('tila')
    cy.contains('Tallenna').click()
    cy.wait('@tila')
    cy.contains('Syötä kaikkiin tehtäviin määrät. Jos sopimuksessa ei ole määriä kyseiselle tehtävälle, syötä').should('not.exist')
  })

  it('Määrän voi syöttää', () => {
    cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').clear().type('666').blur()
  })

  it('Toimenpidettä voi vaihtaa', () => {
    cy.get('div.select-default').first().find('button').click()    
    cy.get('.harja-alasvetolistaitemi').contains('3 Sorateiden hoito').click()
    cy.wait(2000)
    cy.get('table.grid').contains('Ise 2-ajorat').should('not.exist')
  })

  it('Hoitokautta voi vaihtaa', () => {
    cy.server()
    cy.route('POST', '_/tehtavamaarat-hierarkiassa').as('tehtavamaarat')
    cy.get('table.grid').contains('Sorateiden pölynsidonta').parent().find('td.muokattava').find('input').clear().type('667')
    cy.get('div.select-default').contains('01.10.2021-30.09.2022').click()
    cy.contains('01.10.2023-30.09.2024').click()
    cy.wait('@tehtavamaarat')
    cy.wait(2000)
    cy.get('table.grid').contains('667').should('not.exist')
    cy.get('div.select-default').contains('01.10.2023-30.09.2024').click()
    cy.get('.harja-alasvetolistaitemi').contains('01.10.2021-30.09.2022').click()
    cy.wait('@tehtavamaarat')
    cy.wait(2000)
    cy.get('table.grid').contains('Sorateiden pölynsidonta').parent().find('td.muokattava').find('input').should('have.value', '667')
  })

  it('Määrän voi vaihtaa', () => {
    cy.server()
    cy.route('POST', '_/tehtavamaarat-hierarkiassa').as('tehtavamaarat')
    cy.get('div.select-default').first().find('button').click()
    cy.get('.harja-alasvetolistaitemi').contains('1.0 TALVIHOITO').click()
    cy.wait(2000)
    cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').should('have.value', 666)
    cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').type('9').blur()
    cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').should('have.value', 6669)
  })

  after(() => {
    cy.server()
    cy.route('POST', '_/tallenna-tehtavamaarat').as('tallennatehtavamaarat')
    cy.get('div.select-default').first().find('button').click()
    cy.get('.harja-alasvetolistaitemi').contains('0 KAIKKI').click()
    cy.wait(1000)
    cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').clear()
    cy.wait('@tallennatehtavamaarat')
    cy.wait(1000)
    cy.get('div.select-default').first().find('button').click()    
    cy.get('.harja-alasvetolistaitemi').contains('3 Sorateiden hoito').click()
    cy.wait(1000)
    cy.get('table.grid').contains('Sorateiden pölynsidonta').parent().find('td.muokattava').find('input').clear()
  })
})
