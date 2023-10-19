let timeout = 60000;
let avaaKulujenKohdistus = (urakanNimi) => {
    cy.visit('/');
    cy.get('.ladataan-harjaa', {timeout: timeout}).should('not.exist')
    let hakupalkki = cy.get('.haku-input[placeholder="Hae Harjasta"]', {timeout: 30000})
    hakupalkki.type(urakanNimi);
    hakupalkki.siblings().contains(urakanNimi).click();
    cy.get('[data-cy="tabs-taso1-Kulut"]').click();
}

let haeUrakanPaatokset = (urakka, hoitokausi) => {
    return cy.terminaaliKomento().then((tk) => {
        cy.exec(tk + 'psql -h localhost -U harja harja -c ' +
            `\"SELECT \\"urakoitsijan-maksu\\" ` + `FROM urakka_paatos ` +
            `WHERE \\"urakka-id\\" = (SELECT id FROM urakka WHERE nimi = '${urakka}') ` +
            `AND poistettu = false ` +
            `AND \\"hoitokauden-alkuvuosi\\" = ${hoitokausi}\"`)
            .then((dbTulos) => {
                return Number.parseFloat(dbTulos.stdout.split('\n')[2])
            });
    });
}

let avaaKulunKirjaus = () => {
    cy.get('button').contains('Uusi kulu').click();
    cy.contains("Uusi kulu")
}

let valitseKulunPvm = () => {
    cy.get('[data-cy="koontilaskun-kk-dropdown"]').within(() => {
        cy.get('button').click({force: true});
        cy.contains('Syyskuu - 2. hoitovuosi').click();
    })

    cy.get('.kalenteri-kontti').within(() => {
        cy.get('input').click();
        cy.get('td').contains('29').click();
    })
}

let tallennaJaTarkistaKulu = (kuluTaiKulut) => {
    cy.contains('Tallenna').click();

    cy.contains('Kulujen kohdistus');

    cy.get('.pvm-kentta > input').eq(0).click().wait(3000).type('{selectall}29.09.2021');
    cy.get('.pvm-kentta > input').eq(1).click().wait(3000).type('{selectall}29.09.2021');
    cy.get('.pvm-kentta > input').eq(1).should('have.value', "29.09.2021").type('{enter}');

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

    it('Tehdään Normaali suunniteltu tai määrämitattava hankintakulu', () => {
        cy.contains('Normaali suunniteltu tai määrämitattava hankintakulu').click();

        cy.get('[data-cy="kulu-tehtavaryhma-dropdown"]').within(() => {
            cy.get('button').click({force: true});
            cy.contains('Talvihoito (A)').click();
        });

        valitseKulunPvm();

        cy.get('input.maara-input').type('{selectall}-999').then(() => {
            cy.focused().blur({force: true})
        });
        cy.get('input.maara-input').should('have.value', '-999,00');
        tallennaJaTarkistaKulu('Talvihoito laaja TPI');


        // TODO: Kun seuraavan kerran kehitetään kulujen Cypress testejä, niin lisää kulun poisto vielä tähän samaan.
    });
});
