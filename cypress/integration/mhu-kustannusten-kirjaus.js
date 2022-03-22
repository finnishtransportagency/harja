import * as ks from '../support/kustannussuunnitelmaFns.js';

let avaaKulujenKirjaus = (urakanNimi) => {
    cy.visit("/");
    let hakupalkki = cy.get('.haku-input[placeholder="Hae Harjasta"]')
    hakupalkki.type(urakanNimi);
    hakupalkki.siblings().contains(urakanNimi).click();

    cy.get('[data-cy="tabs-taso1-Kulut"]').click();
    cy.get('button').contains('Uusi kulu').click();
}

let alustaTietokanta = () => {
    cy.terminaaliKomento().then((tk) => {
        ks.alustaKanta("Kittilän MHU 2019-2024")

        let tiedosto = "tietokanta/testidata/vuodenpaatos.sql"
        cy.exec(tk + "psql -h localhost -U harja harja -f" + tiedosto)
    })
}

describe('Testaa Kittilän MHU Kulujen kirjaus-näkymää', () => {
    before("Alusta kanta", () => alustaTietokanta())

    it('Kulujen kirjaus-näkymä aukeaa', () => {
        avaaKulujenKirjaus("Kittilän MHU 2019-2024")
        cy.contains("Uusi kulu")
    })
})
