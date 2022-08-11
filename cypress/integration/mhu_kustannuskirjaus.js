let avaaKulujenKohdistus = (urakanNimi) => {
    cy.visit('/');
    let hakupalkki = cy.get('.haku-input[placeholder="Hae Harjasta"]')
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
            })
    })
}

let avaaKulunKirjaus = () => {
    cy.get('button').contains('Uusi kulu').click();
    cy.contains("Uusi kulu")
}

let valitseKulunPvm = () => {
    cy.get('[data-cy="koontilaskun-kk-dropdown"]').within(() => {
        cy.get('button').click();
        cy.contains('Syyskuu - 1. hoitovuosi').click();
    })

    cy.get('.kalenteri-kontti').within(() => {
        cy.get('input').click();
        cy.get('td').contains('30').click();
    })
}

let tallennaJaTarkistaKulu = (kuluTaiKulut) => {
    cy.contains('Tallenna').click();

    cy.contains('Kulujen kohdistus')

    cy.get('.pvm-kentta > input').eq(0).type('{selectall}30.09.2020')
    cy.get('.pvm-kentta > input').eq(1).type('{selectall}30.09.2020')
    cy.get('.pvm-kentta > input').eq(1).should('have.value', "30.09.2020").type('{enter}');


    if (Array.isArray(kuluTaiKulut)) {
        cy.get('table.grid tr.klikattava').eq(0).click();
        kuluTaiKulut.forEach((kulu, i) => {
          cy.get('table.grid tr.klikattava').eq(i + 1)
              .contains(kulu);
      })
    } else {
        cy.get('table.grid tr.klikattava')
            .contains(kuluTaiKulut)
    };

}

describe('Testaa Kittilän MHU Kulujen kirjaus-näkymää', () => {

    it('Kulujen kirjaus-näkymä aukeaa', () => {
        avaaKulujenKohdistus('Kittilän MHU 2019-2024');
        avaaKulunKirjaus();
    });

    it('Tee hoitovuoden tavoitehinnan ylityksen kulu', () => {
        cy.contains('Hoitovuoden päätös').click();
        cy.contains('Urakoitsija maksaa tavoitehinnan ylityksestä').click();

        cy.contains('Kulun tyyppi');
        cy.get('.tehtavaryhma-valinta-disabled')
            .contains('Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä');

        valitseKulunPvm();

        cy.get('input.maara-input').type('{selectall}1000').then(() => {
            cy.focused().blur({force: true})
        })
        cy.get('input.maara-input').should('have.value', '-1\u00a0000,00');
        tallennaJaTarkistaKulu('Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä');
    });

    it('Muokkaa hoitovuoden päätöksen kulua', () => {
        cy.get('table.grid tr.klikattava')
            .contains('Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä').click();

        cy.contains('Urakoitsija maksaa tavoite- ja kattohinnan ylityksestä').click();

        [0, 1].forEach((i) => {
            cy.get('.tehtavaryhma-valinta-disabled').eq(i)
                .contains('Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä');

            cy.get('input.maara-input').eq(i).type('{selectall}500').then(() => {
                cy.focused().blur();
            });
            cy.get('input.maara-input').eq(i).should('have.value', '-500,00');
        })

        tallennaJaTarkistaKulu([
            'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä',
            'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä']);
    })

    it('Poista hoitovuoden päätöksen kulu', () => {
        cy.get('table.grid tr.klikattava').eq(1).click();
        cy.contains('Poista kulu').click();
        cy.contains('Poista tiedot').click();

        cy.contains('Annetuilla hakuehdoilla ei näytettäviä kuluja')

    })

    it('Tee hoitovuoden päätöksen kulu', () => {
        haeUrakanPaatokset('Kittilän MHU 2019-2024', 2019).then((paatosSumma) => {
            avaaKulunKirjaus();

            cy.contains('Hoitovuoden päätös').click();
            valitseKulunPvm();

            cy.get('input.maara-input').type('{selectall}' + Math.abs(paatosSumma));

            tallennaJaTarkistaKulu('Hoitovuoden päättäminen / Tavoitepalkkio');
        });
    });
})
