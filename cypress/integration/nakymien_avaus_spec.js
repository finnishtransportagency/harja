 describe('Harja smoke testit', function () {
    beforeEach(function () {
        cy.visit("https://harja-test.solitaservices.fi/#urakat/yleiset?")
    })
    it("Urakkavalinta listan kautta toimii", function () {
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa').click()
    })
    it("Urakkavalinta kartalta toimii", function () {
        // cy.server()
        // cy.route('POST','**hallintayksikon-urakat**').as('hallintayksikon-urakat')

        // valitse hallintayksikkö
        cy.wait(10000)
        cy.get('#kartta').click(402, 268)

        // valitse urakka
        //cy.wait('@hallintayksikon-urakat')
        cy.wait(10000)
        cy.get('#kartta').click(493, 574)

    })
})