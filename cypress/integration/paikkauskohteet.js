// Helper funkkareita
let clickTimeout = 60000; // Minuutin timeout hitaan ci putken takia
function siivoaKanta () {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        // Poista luotu paikkauskohde
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM paikkauskohde pk WHERE pk.nimi = 'CPKohde';\"");
    });
}

let avaaPaikkauskohteetSuoraan = function () {
    cy.server()
    cy.route('POST', '_/paikkauskohteet-urakalle').as('kohteet')
    cy.route('POST', '_/hae-paikkauskohteiden-tyomenetelmat').as('menetelmat')
    // Avaa Harja ihan juuresta
    cy.visit("/#urakat/paikkaukset-yllapito?&hy=13&u=36")
    cy.wait('@menetelmat', {timeout: clickTimeout})
    cy.wait('@kohteet', {timeout: clickTimeout})
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
}

let avaaToteumat = () => {
  avaaPaikkauskohteetSuoraan();
  cy.get('[data-cy=tabs-taso2-Toteumat]').click()
}

describe('Paikkauskohteet latautuu oikein', function () {
    it('Mene paikkauskohteet välilehdelle palvelun juuresta', function() {
        // Avaa Harja ihan juuresta
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Lappi').click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Päällystys'})
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Kemin päällystysurakka', {timeout: clickTimeout}).click()
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
        // siirry paikkauskohteisiin
        avaaPaikkauskohteetSuoraan()
        // Avataan paikkauskohdelomake uuden luomista varten
        cy.get('button').contains('.nappi-ensisijainen', 'Lisää kohde', {timeout: clickTimeout}).click({force: true})
        // Varmistetaan, että sivupaneeli aukesi
        cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
        // annetaan nimi
        cy.get('label[for=nimi] + input').type("CPKohde", {force: true})
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
        cy.get('button').contains('.nappi-ensisijainen', 'Tallenna muutokset',{timeout: clickTimeout}).click({force: true})

        // Varmista, että tallennus onnistui
        cy.get('.toast-viesti', {timeout: 60000}).should('be.visible')
    })

    it('Tilaa paikkauskohde', function() {

        // siirry paikkauskohteisiin
        avaaPaikkauskohteetSuoraan()

        cy.server()
        cy.route('POST', '_/tallenna-paikkauskohde-urakalle').as('tilaus')

        // Avataan paikkauskohdelomake uuden luomista varten
        cy.contains('tr.paikkauskohderivi > td > span > span ', 'CPKohde').click({force: true})
        // Varmistetaan, että sivupaneeli aukesi
        cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
        // Tilaa kohde
        cy.get('button').contains('.nappi-ensisijainen', 'Tilaa', {timout: clickTimeout}).click({force: true})
        // Vahvista tilaus
        cy.get('button').contains('.nappi-ensisijainen', 'Tilaa kohde', {timout: clickTimeout}).click({force: true})
        cy.wait('@tilaus', {timeout: 60000})

    })

    it('Lisää paikkauskohteelle toteuma', function() {

        // siirry paikkauskohteisiin
        avaaPaikkauskohteetSuoraan()

        //Avataan sivupaneeliin
        cy.contains('tr.paikkauskohderivi > td > span > span ', 'CPKohde').click({force: true})
        // Varmistetaan, että sivupaneeli aukesi
        cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
        // Avaa toteuman lisäys paneeli
        cy.get('button').contains('.nappi-toissijainen', 'Lisää toteuma', {timout: clickTimeout}).click({force: true})
        // Varmistetaan, että nyt on 2 sivupaneelia auki
        cy.get('div').find('.overlay-oikealla').should('have.length', 2)
    })


})

describe('Paikkaustoteumat toimii', function() {
  it('Mene paikkaustoteumat välilehdelle ja lisää toteuma', function() {
    cy.visit('/')
    cy.contains('.haku-lista-item', 'Lappi').click()
    cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
    cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Päällystys'})
    cy.contains('[data-cy=urakat-valitse-urakka] li', 'Kemin päällystysurakka', {timeout: 30000}).click()
    // Kemin päällystysurakka on puutteellinen ja YHA lähetyksestä tulee varoitus. Suljetaan modaali
    cy.contains('.nappi-toissijainen', 'Sulje').click()
    cy.get('[data-cy=tabs-taso1-Paikkaukset]').click()
    // Avataan myös toteuma välilehti ja palataan paikkauskohteisiin
    
    cy.get('[data-cy=tabs-taso2-Toteumat]').click()
    cy.contains('Lisää toteuma').click()
    cy.get('label[for=aosa] + span > input').type("4")
    cy.get('label[for=aet] + span > input').type("4")
    cy.get('label[for=losa] + span > input').type("5")
    cy.get('label[for=let] + span > input').type("5")
    cy.contains('Tallenna').should('be.disabled')
    cy.get('label[for=pinta-ala] + span > input').type('5')
    cy.contains('Tallenna').should('not.be.disabled')
    cy.contains('Tallenna').click()
  })

  it('Tarkastellaan toteumaa', () => {
    avaaToteumat();
    cy.contains('.otsikkopaneeli').click()
  })
})


describe('Siivotaan lopuksi', function () {
    before(siivoaKanta);
    // Siivotaan vain jäljet

    it('Tarkista, että kanta on siivottu', function() {

        // siirry paikkauskohteisiin
        avaaPaikkauskohteetSuoraan()

        cy.contains('tr.paikkauskohderivi > td > span > span ', 'CPKohde').should('not.be.visible')
    })
})
