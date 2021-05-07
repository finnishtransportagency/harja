describe('Päänäkymien avaamiset', function () {
    beforeEach(function () {
        cy.visit("/")
    })

    it("Urakkavalinta listan kautta toimii", function () {
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa').click()
        cy.contains('.haku-lista-item', 'Aktiivinen Oulu Testi').click()
        cy.contains('Aktiivinen Oulu Testi')
    })

    it("Raportit välilehti toimii", function () {
        cy.contains('ul#sivut a span', 'Raportit').click()
        cy.contains('div.valittu', 'Valitse').click()
        cy.contains('.harja-alasvetolistaitemi a', "Ilmoitusraportti").click()
        cy.contains('span.raksiboksi-teksti', "Valittu aikaväli").should('exist')
        cy.contains('span.raksiboksi-teksti', "Näytä urakka-alueet eriteltynä").should('exist')
        cy.contains('Hupsista').should('not.exist')
    })

    it("Tilannekuva välilehti toimii", function () {
        cy.contains('ul#sivut a span', 'Tilannekuva').click()
        cy.contains('div#tk-suodattimet a.klikattava', "Nykytilanne").should('exist')
        cy.contains('Hupsista').should('not.exist')
    })

    it("Ilmoitukset välilehti toimii", function () {
        cy.contains('ul#sivut a span', 'Ilmoitukset').click()
        cy.contains('div.livi-grid th', "Urakka").should('exist')
        cy.contains('Hupsista').should('not.exist')
    })

    it("Tienpidon luvat välilehti toimii", function () {
        cy.contains('ul#sivut a span', 'Tienpidon luvat').click()
        cy.contains('button', "Hae lupia").should('exist')
        cy.contains('Hupsista').should('not.exist')
    })
})
