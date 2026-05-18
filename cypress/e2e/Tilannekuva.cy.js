let timeout = 60000;

describe('Tilannekuva latautuu oikein', function () {
    it('Mene tilannekuvaan', function() {
        cy.visit('/');
        cy.get('.ladataan-harjaa', {timeout: timeout}).should('not.exist')
        cy.visit('/#tilannekuva/nykytilanne?')
        cy.get('[data-cy=Nykytilanne]').should('have.attr', 'aria-selected', 'true')
        cy.get('[type=radio]').first().parent().then(($radioButton) => {
            expect($radioButton.text().trim()).contain('0-2h')
        })
    })
    it('Klikkaile tabit läpi', function() {
        cy.get('[data-cy=Historiakuva]').click().should('have.attr', 'aria-selected', 'true')
        cy.get('[data-cy=Tienakyma]').click().should('have.attr', 'aria-selected', 'true')
    })
})
