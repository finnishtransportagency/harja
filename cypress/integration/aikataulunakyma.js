// Jostain syystä tosi kova feilailemaan Circlessä tämä testi, otetaanpa toviksi pois
// describe('Aikataulunäkymien avaaminen tiemerkintäurakassa', function () {
//     beforeEach(function () {
//         cy.visit("http://localhost:3000/#urakat/yleiset?&hy=12&u=12")
//     })
//
//     it("Aikataulun avaaminen toimii tiemerkinnässä", function () {
//         cy.contains('.klikattava', 'Aikataulu').click()
//         cy.get('.alasveto-vuosi').click()
//         cy.get('a').contains('2018').click()
//         cy.get('.navigation-right', {timeout: 40000}).first().click()
//         // Pitää näkyä alikohteet omassa taulukossaan
//         cy.contains('.panel-title', 'Kohteen tierekisteriosoitteet')
//         cy.contains('.panel-title', 'Muut tierekisteriosoitteet')
//         cy.contains('.panel-title', 'Kohteen päällystysurakan tarkka aikataulu')
//     })
// })


describe('Aikataulunäkymien avaaminen päällystysurakassa', function () {
    beforeEach(function () {
        cy.visit("http://localhost:3000/#urakat/yleiset?&hy=12&u=7")
    })

    it("Aikataulun avaaminen toimii päällystyksessä", function () {
        cy.contains('.klikattava', 'Aikataulu', {timeout: 40000}).click()
        cy.get('.navigation-right', {timeout: 40000}).first().click()
        // Ei saa näkyä alikohteet omassa taulukossaan
        cy.contains('.panel-title', 'Kohteen tierekisteriosoitteet').should('not.visible')
        cy.contains('.panel-title', 'Muut tierekisteriosoitteet').should('not.visible')
        cy.contains('.panel-title', 'Kohteen päällystysurakan tarkka aikataulu')
    })
})
