let timeout = 60000; // Minuutin timeout hitaan ci putken takia
const testiaika = new Date(2021, 9, 15, 12).getTime() // Urakan 1. vuoden loppu

describe('Välikatselmus aukeaa', () => {
    it('Välikatselmuksen voi avata kustannusten seurannasta', () => {
        cy.viewport(1100, 2000)
        cy.visit('/')
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa', {timeout}).click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Iin MHU 2021-2026', {timeout}).click()
        cy.get('[data-cy=tabs-taso1-Kulut]').click()
        cy.get('[data-cy="tabs-taso2-Kustannusten seuranta"]').click()
        cy.get('[data-cy=hoitokausi-valinta]').valinnatValitse({valinta: '01.10.2021-30.09.2022'})
        cy.contains('Tee välikatselmus').click()
        cy.contains('Välikatselmuksen päätökset')
        cy.contains('Iin MHU 2021-2026')
        cy.contains('1. hoitovuosi (2021—2022)')
    })
})
