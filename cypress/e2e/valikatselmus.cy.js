let timeout = 60000; // Minuutin timeout hitaan ci putken takia
const testiaika = new Date(2021, 9, 15, 12).getTime() // Urakan 1. vuoden loppu

describe('Välikatselmus aukeaa', () => {
    it('Välikatselmuksen voi avata kustannusten seurannasta', () => {
        cy.intercept('POST', 'urakan-kustannusten-seuranta-paaryhmittain' ).as('hae-kustannukset')
        cy.viewport(1400, 1600)
        cy.visit('/')
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa', {timeout}).click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Iin MHU 2021-2026', {timeout}).click()
        cy.get('[data-cy=tabs-taso1-Kulut]').click()
        cy.get('[data-cy="tabs-taso2-Kustannusten seuranta"]').click()
        cy.wait('@hae-kustannukset')
        cy.get('[data-cy=hoitokausi-valinta]').valinnatValitse({valinta: '1. hoitovuosi (2021—2022)'})
        cy.contains('Tee välikatselmus').click()
        cy.contains('Välikatselmuksen päätökset')
        cy.contains('Iin MHU 2021-2026')
        cy.contains('1. hoitovuosi (01.10.2021 - 30.09.2022')
    })

    it('Välikatselmuksen voi avata päävalikosta', () => {
        // Alustetaan muutama backend -kutsu
        cy.intercept('POST', 'hae-valikatselmuksen-tiedot-hoitovuodelle' ).as('hae-valikatselmuksen-tiedot')
        cy.intercept('POST', 'tallenna-tavoitehinnan-oikaisu' ).as('tavoitehinnan-oikaisu')
        cy.intercept('POST', 'poista-tavoitehinnan-oikaisu' ).as('poista-tavoitehinnan-oikaisu')

        cy.viewport(1400, 1600)
        cy.visit('/')
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa', {timeout}).click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Iin MHU 2021-2026', {timeout}).click()
        cy.get('[data-cy=tabs-taso1-Valikatselmus]').click()
        cy.wait('@hae-valikatselmuksen-tiedot')
        cy.get('[data-cy=hoitokausi-valinta]').valinnatValitse({valinta: '2. hoitovuosi (2022—2023)'})

        // Tarkistellaan tietoja
        cy.contains('Välikatselmuksen päätökset')
        cy.contains('Iin MHU 2021-2026')
        cy.contains('2. hoitovuosi (01.10.2022 - 30.09.2023')
        cy.contains('Tavoitehinnan oikaisut')
        cy.contains('Yhteenveto')

        // Lisätään yksi tavoitehinnan oikaisu
        cy.contains('Luokka')
        cy.contains('Selite')
        cy.contains('Summa')
        // Alkuun ei saa olla oikaisuja
        cy.contains('Ei oikaisuja')
        cy.contains('Lisää oikaisu').click();

          //  .should('have.attr', 'id')
         //   .and('include', '/luokka-1').click();
        //cy.contains('[data-cy="luokka"]').click()
        cy.get('[data-cy="luokka-1"]').valinnatValitse({valinta: 'Tiestömuutokset'})
        cy.get('#0summa-1').type('{selectall}100000').blur();
        //cy.wait('@tavoitehinnan-oikaisu')
        cy.wait('@hae-valikatselmuksen-tiedot')
        cy.get('[data-cy="luokka-1"]').contains('Tiestömuutokset');
        cy.get('#0summa-1').should('have.value', '100000')
        cy.get('[aria-label="Poista"]').click();
        //cy.wait('@poista-tavoitehinnan-oikaisu')
        //cy.wait('@hae-valikatselmuksen-tiedot')

        // Poiston jälkeen ei saa olla oiikaisuja
        cy.contains('Ei oikaisuja')
    })
})
