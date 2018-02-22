describe('Päänäkymien avaamiset', function () {
    beforeEach(function () {
        cy.visit("http://localhost:3000/#urakat/yleiset?")
    })
    
    it("Urakkavalinta listan kautta toimii", function () {
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa').click()
        cy.contains('.haku-lista-item', 'Oulu').click()
        cy.contains('Oulun alueurakka')
    })
})