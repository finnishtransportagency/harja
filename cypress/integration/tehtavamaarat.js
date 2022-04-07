Cypress.on('uncaught:exception', (err, runnable) => {
  // returning false here prevents Cypress from
  // failing the test
  return false
})

describe('Tehtävämäärien syöttö ja käpistely', () => {
  before(() => {
    cy.server()
    cy.visit('/')
    cy.visit('http://localhost:3000/#urakat/suunnittelu/tehtavat?hy=12&u=35')
    cy.route('POST', '_/tehtavat').as('tehtavat')
cy.route('POST', '_/tehtavamaarat-hierarkiassa').as('tehtavamaarat')
    cy.wait('@tehtavat')
    cy.wait('@tehtavamaarat')
    cy.viewport(1100, 2000)
  })

  it('Määrän voi syöttää', () => {
    cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').type('666')
    
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
    cy.get('table.grid').contains('Ise 2-ajorat').parent().find('td.muokattava').find('input').type('9')
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
