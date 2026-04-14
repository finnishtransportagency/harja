let timeout = 60000;
let pageloadTimeout = 20000;
let clicktimeout = 2000;
let avaaKulujenKohdistus = (urakanNimi) => {
    cy.visit('/');
    cy.get('.ladataan-harjaa', {timeout: timeout}).should('not.exist')
    let hakupalkki = cy.get('.haku-input[placeholder="Hae Harjasta"]', {timeout: pageloadTimeout})
    hakupalkki.type(urakanNimi);
    hakupalkki.siblings().contains(urakanNimi).click();
    cy.get('[data-cy="tabs-taso1-Kulut"]').click();
}

let avaaKulunKirjaus = () => {
    cy.get('button').contains('Uusi kulu').click();
    cy.contains("Uusi kulu")
}

let valitseKulunPvm = () => {
    cy.get('[data-cy="koontilaskun-kk-dropdown"]').within(() => {
        cy.get('button').click({force: true});
        cy.contains('Syyskuu - 2. hoitovuosi').click();
    });

    // Dropdown jää auki, ellei focusta siirretä muualle
    cy.get('#kohdistuksen-summa-0').click().blur();

    cy.get('.kalenteri-kontti').within(() => {
        cy.get('input').focus().click();
        cy.get('td').contains('29').click();
    })
}

let tallennaJaTarkistaKulu = (kuluTaiKulut) => {
    cy.contains('Tallenna').click();

    cy.get('h1').contains('Kulujen kohdistus');

    cy.get('.pvm-kentta > .pvm-ikoni > input').eq(0).click().wait(clicktimeout).type('{selectall}29.09.2021');
    cy.get('.pvm-kentta > .pvm-ikoni > input').eq(1).click().wait(clicktimeout).type('{selectall}29.09.2021');
    cy.get('.pvm-kentta > .pvm-ikoni > input').eq(1).should('have.value', "29.09.2021").type('{enter}');

    if (Array.isArray(kuluTaiKulut)) {
        cy.get('table.grid tr.klikattava').eq(0).click();
        kuluTaiKulut.forEach((kulu, i) => {
          cy.get('table.grid tr.klikattava').eq(i + 1)
              .contains(kulu);
      })
    } else {
        cy.get('table.grid tr.klikattava')
            .contains(kuluTaiKulut)
    }
}

describe('Testaa Kittilän MHU Kulujen kirjaus-näkymää', () => {

    it('Kulujen kirjaus-näkymä aukeaa', () => {
        avaaKulujenKohdistus('Kittilän MHU 2019-2024');
        avaaKulunKirjaus();
    });

    it('Tehdään hankintakulu', () => {

        // Klikkaa tehtäväryhmä alasvetovalikko auki
        cy.get('[data-cy="hankintakulu-tehtavaryhma-dropdown"]').click();

        // Valitse A - Talvihoito
        cy.get('[data-cy="hankintakulu-tehtavaryhma-dropdown"] span a').contains('A - Talvihoito').click();

        valitseKulunPvm();

        // Varmista, että negatiivisen kulun kirjaaminen onnistuu
        cy.get('#kohdistuksen-summa-0').type('{selectall}-999').then(() => {
            cy.focused().blur({force: true})
        });

        // Varmista, että positiivisen kulun kirjaaminen onnistuu
        cy.get('#kohdistuksen-summa-0').type('{selectall}999').then(() => {
            cy.focused().blur({force: true})
        });
        cy.get('#kohdistuksen-summa-0').should('have.value', '999,00');
        tallennaJaTarkistaKulu('TALVIHOITO');


        // TODO: Kun seuraavan kerran kehitetään kulujen Cypress testejä, niin lisää kulun poisto vielä tähän samaan.
    });
});
