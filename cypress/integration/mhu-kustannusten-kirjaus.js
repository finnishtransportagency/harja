let avaaKulujenKirjaus = (urakanNimi) => {
    cy.visit("/");
    let hakupalkki = cy.get('.haku-input[placeholder="Hae Harjasta"]')
    hakupalkki.type(urakanNimi);
    hakupalkki.siblings().contains(urakanNimi).click();

    cy.get('[data-cy="tabs-taso1-Kulut"]').click();
    cy.get('button').contains('Uusi kulu').click();
}

let alustaTietokanta = (urakanNimi) => {
    cy.terminaaliKomento().then((tk) => {
        cy.exec(tk + "")
    })
}

describe('Testaa Kittilän MHU Kulujen kirjaus-näkymää', () => {
    /*
    it('Kulujen kirjaus-näkymä aukeaa', () => {
        avaaKulujenKirjaus("Kittilän MHU 2019-2024")
        cy.contains("")
    })
     */
    
    it('Testaa tietokantaskriptiä', () => {

        cy.terminaaliKomento().then((tk) => {
            let tiedosto = "tietokanta/testidata/vuodenpaatos.sql"
            cy.exec(tk + "psql -h localhost -U harja harja -f" + tiedosto)
        })
    })
})
