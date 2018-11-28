// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add("login", (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add("drag", { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add("dismiss", { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This is will overwrite an existing command --
// Cypress.Commands.overwrite("visit", (originalFn, url, options) => { ... })

Cypress.Commands.add("gridOtsikot", { prevSubject: 'element'}, (grid) => {
    let $otsikkoRivit = grid.find('th')
    let otsikotIndekseineen = new Map();
    for (let i = 0; i < $otsikkoRivit.length; i++) {
        let otsikonTeksti = $otsikkoRivit.eq(i).text().replace(/[\u00AD]+/g, '')
        otsikotIndekseineen.set(otsikonTeksti, i)
    }
    return {
        grid: grid,
        otsikot: otsikotIndekseineen
    }
})