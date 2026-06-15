let timeout = 60000; // Minuutin timeout hitaan ci putken takia
const testiaika = new Date(2021, 9, 15, 12).getTime() // Urakan 1. vuoden loppu

// Helper funkkareita
function siivoaKanta() {
    console.log("Siivotaan kanta!");
    let komento = 'psql -h localhost -U harja harja -c ' + "\"DELETE FROM tavoitehinnan_oikaisu tavo WHERE tavo.\\\"hoitokauden-alkuvuosi\\\" = 2022 AND tavo.\\\"urakka-id\\\" = (SELECT id FROM urakka WHERE nimi = 'Iin MHU 2021-2026');\"";
    console.log("Komento:", komento);
    // Tyhjennetään tavoitehinnan muutokset ennen testiä
    cy.terminaaliKomento().then((terminaaliKomento) => {
        // Poista kiinteähintaiset työt
        cy.exec(terminaaliKomento + komento)
            .then((tulos) => {
                console.log("Poista oikaisut:", tulos)
            });
        })
}

describe('Välikatselmus aukeaa', () => {

    before(siivoaKanta);

    it('Välikatselmuksen voi avata kustannusten seurannasta', () => {
        cy.intercept('POST', 'urakan-kustannusten-seuranta-paaryhmittain' ).as('hae-kustannukset')
        cy.viewport(1400, 1400)
        cy.visit('/')
        cy.get('[data-cy="haku-lista-item"]').contains('Pohjois-Suomi', {timeout}).click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.contains('[data-cy=urakat-valitse-urakka] button', 'Iin MHU 2021-2026', {timeout}).click()
        cy.get('[data-cy=tabs-taso1-Kulut]').click()
        cy.get('[data-cy="tabs-taso2-Kustannusten seuranta"]').click()
        cy.wait('@hae-kustannukset')
        cy.get('[data-cy=hoitokausi-valinta]').valinnatValitse({valinta: '1. hoitovuosi (2021—2022)'})
        cy.contains('Siirry välikatselmukseen').click();
        cy.contains('Välikatselmus')
        cy.contains('Iin MHU 2021-2026')
        cy.contains('2. hoitovuosi (01.10.2022 − 30.09.2023)')
    })

    it('Välikatselmuksen voi avata päävalikosta', () => {

        //poistaOikaisut("Iin MHU 2021-2026");
        // Alustetaan muutama backend -kutsu
        cy.intercept('POST', 'hae-valikatselmuksen-tiedot-hoitovuodelle' ).as('hae-valikatselmuksen-tiedot')
        cy.intercept('POST', 'tallenna-tavoitehinnan-oikaisu' ).as('tavoitehinnan-oikaisu')
        cy.intercept('POST', 'poista-tavoitehinnan-oikaisu' ).as('poista-tavoitehinnan-oikaisu')

        cy.viewport(1400, 1400)
        cy.visit('/')
        cy.get('[data-cy="haku-lista-item"]').contains('Pohjois-Suomi', {timeout}).click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.contains('[data-cy=urakat-valitse-urakka] button', 'Iin MHU 2021-2026', {timeout}).click()
        cy.get('[data-cy=tabs-taso1-Valikatselmus]').click()
        cy.wait('@hae-valikatselmuksen-tiedot')
        cy.get('[data-cy=hoitokausi-valinta]').valinnatValitse({valinta: '2. hoitovuosi (01.10.2022 − 30.09.2023)'})

        // Tarkistellaan tietoja
        cy.contains('Välikatselmus')
        cy.contains('Iin MHU 2021-2026')
        cy.contains('2. hoitovuosi (01.10.2022 − 30.09.2023)')
        cy.contains('Tavoitehinnan muutokset')
        cy.contains('Yhteenveto')

        // Lisätään yksi tavoitehinnan oikaisu
        cy.contains('Muutos')
        cy.contains('Perustelu')
        cy.contains('Vaikutus')
        // Alkuun ei saa olla oikaisuja
        cy.contains('Ei muutoksia tavoitehintaan')

        cy.contains('Lisää rivi').click();
        cy.get('[data-cy="luokka-1"]').valinnatValitse({valinta: 'Tiestömuutokset'})
        cy.get('#0selite-1').type('{selectall}seliseli');
        cy.get('#0summa-1').type('{selectall}100000');
        cy.contains('Tallenna muutokset').click();
        cy.wait('@tavoitehinnan-oikaisu')

        // Tarkista oikaisun tiedot
        cy.get('[data-cy="luokka-1"]').contains('Tiestömuutokset');
        cy.get('#0selite-1').should('have.value', 'seliseli')
    })
})
