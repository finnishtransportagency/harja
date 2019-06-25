describe('Tilannekuva latautuu oikein', function () {
    it('Mene tilannekuvaan', function() {
        cy.visit('/#tilannekuva/nykytilanne?')
        cy.get('[data-cy=Nykytilanne]').parent().should('have.class', 'active')
        cy.get('[type=radio]').first().parent().then(($radioButton) => {
            expect($radioButton.text().trim()).contain('0-2h')
        })
    })
    it('Klikkaile tabit läpi', function() {
        cy.get('[data-cy=Historiakuva]').click().parent().should('have.class', 'active')
        cy.get('[data-cy=Tienakyma]').click().parent().should('have.class', 'active')
    })
})