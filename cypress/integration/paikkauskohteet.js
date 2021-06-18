describe('Paikkauskohteet latautuu oikein', function () {
    it('Mene paikkauskohteet välilehdelle palvelun juuresta', function() {
        // Avaa Harja ihan juuresta
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Lappi').click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.be.visible')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Päällystys'})
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Kemin päällystysurakka', {timeout: 30000}).click()
        // Kemin päällystysurakka on puutteellinen ja YHA lähetyksestä tulee varoitus. Suljetaan modaali
        cy.contains('.nappi-toissijainen', 'Sulje').click()
        cy.get('[data-cy=tabs-taso1-Paikkaukset]').click()
        // Avataan myös toteuma välilehti ja palataan paikkauskohteisiin

        cy.get('[data-cy=tabs-taso2-Toteumat]').click()
        cy.get('[data-cy=tabs-taso2-Paikkauskohteet]').click()
        //cy.get('[data-cy=tabs-taso2-Paallystysilmoitukset]').parent().should('have.class', 'active')
        //cy.get('img[src="images/ajax-loader.gif"]').should('not.exist')
    })

    it('Lisää uusi levittimellä tehtytävä paikkauskohde', function() {
        cy.server()
        cy.route('POST', '_/paikkauskohteet-urakalle').as('kohteet')
        cy.route('POST', '_/hae-paikkauskohteiden-tyomenetelmat').as('menetelmat')
        // Avaa Harja ihan juuresta
        cy.visit("/#urakat/paikkaukset-yllapito?&hy=13&u=36")
        cy.wait('@menetelmat', {timeout: 30000})
        cy.wait('@kohteet', {timeout: 30000})
        cy.get('.ajax-loader', {timeout: 30000}).should('not.be.visible')
        // Avataan paikkauskohdelomake uuden luomista varten
        cy.contains('.nappi-ensisijainen', 'Lisää kohde').click({force: true})
        // Varmistetaan, että sivupaneeli aukesi
        cy.get('.overlay-oikealla', {timeout: 30000}).should('be.visible')
        // annetaan nimi
        cy.get('label[for=nimi] + input').type("CPKohde")
        cy.get('label[for=ulkoinen-id] + span > input').type("12345678")
        // Valitse työmenetelmä
        cy.get('label[for=tyomenetelma] + div').valinnatValitse({valinta: 'PAB-paikkaus levittäjällä'})
        cy.get('label[for=tie] + span > input').type("81")
        cy.get('label[for=ajorata] + div').valinnatValitse({valinta: '2'})
        cy.get('label[for=aosa] + span > input').type("4")
        cy.get('label[for=aet] + span > input').type("4")
        cy.get('label[for=losa] + span > input').type("5")
        cy.get('label[for=let] + span > input').type("5")
        // Ajankohta
        cy.get('label[for=alkupvm] + .pvm-kentta > .input-default').type("1.5.2021")
        cy.get('label[for=loppupvm] + .pvm-kentta > .input-default').type("1.6.2021")
        //Suunnitellut määrät ja summa
        cy.get('label[for=suunniteltu-maara] + span > input').type("355")
        cy.get('label[for=yksikko] + div').valinnatValitse({valinta: 'jm'})
        cy.get('label[for=suunniteltu-hinta] + span > input').type("40000")
        cy.contains('.nappi-ensisijainen', 'Tallenna muutokset').click({force: true})

        // Varmista, että tallennus onnistui
        cy.get('.toast-viesti', {timeout: 60000}).should('be.visible')
    })
})