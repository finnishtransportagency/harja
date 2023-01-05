// asetuksia
let clickTimeout = 60000; // Minuutin timeout hitaan ci putken takia
let potRaportoitava = "POT-raportoitava";
let uniikkiUlkoinenId = "97978911";

// Helper funkkareita
function siivoaKanta() {
    cy.terminaaliKomento().then((terminaaliKomento) => {
        // Poista luotu paikkauskohde CPKOHDE ja toteuma
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM paikkauksen_tienkohta p where p.\\\"paikkaus-id\\\" in (select pp.id from paikkaus pp join paikkauskohde pk on pk.nimi = 'CPKohde' where pp.\\\"paikkauskohde-id\\\" = pk.id);\"");
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM paikkaus p where p.\\\"paikkauskohde-id\\\" in (select id from paikkauskohde pk where pk.nimi = 'CPKohde' order by pk.id);\"");
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM paikkauskohde pk WHERE pk.nimi = 'CPKohde';\"");


        // Poista potRaportoitava paikkauskohde
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' +
            "\"DELETE FROM paikkauskohde pk WHERE pk.nimi = 'POT-raportoitava';\"");
    });
}

let avaaPaikkauskohteetSuoraan = function () {
    cy.server()
    cy.route('POST', '_/paikkauskohteet-urakalle').as('kohteet')
    cy.route('POST', '_/hae-paikkauskohteiden-tyomenetelmat').as('tyomenetelmat')
    cy.visit("/")
    cy.contains('.haku-lista-item', 'Lappi').click()
    cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
    cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Päällystys'})
    cy.contains('[data-cy=urakat-valitse-urakka] li', 'Kemin päällystysurakka', {timeout: clickTimeout}).click()
    // Kemin päällystysurakka on puutteellinen ja YHA lähetyksestä tulee varoitus. Suljetaan modaali
    cy.contains('.nappi-toissijainen', 'Sulje').click()
    cy.get('[data-cy=tabs-taso1-Paikkaukset]').click()
    cy.wait('@kohteet', {timeout: clickTimeout})
    cy.wait('@tyomenetelmat', {timeout: clickTimeout})

    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
    cy.get('label[for=filtteri-vuosi] + div').valinnatValitse({valinta: '2021'})

    cy.route('POST', '_/paikkauskohteet-urakalle').as('2021-kohteet')
    cy.contains('.nappi-ensisijainen', 'Hae kohteita', ).click({force: true})
    cy.wait('@2021-kohteet', {timeout: clickTimeout})

}

let avaaToteumat = () => {
    cy.server()
    cy.route('POST', '_/hae-urakan-paikkaukset').as('2021-paikkaukset')
    cy.visit("/")
    cy.contains('.haku-lista-item', 'Lappi').click()
    cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
    cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Päällystys'})
    cy.contains('[data-cy=urakat-valitse-urakka] li', 'Kemin päällystysurakka', {timeout: clickTimeout}).click()
    // Kemin päällystysurakka on puutteellinen ja YHA lähetyksestä tulee varoitus. Suljetaan modaali
    cy.contains('.nappi-toissijainen', 'Sulje').click()
    cy.get('[data-cy=tabs-taso1-Paikkaukset]').click()
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
    cy.get('[data-cy=tabs-taso2-Toteumat]').click()
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')


    cy.get('label[for=filtteri-aikavali] + div .pvm-kentta > .input-default > input').first().focus().type("01.01.2021" ).clear().type("01.01.2021" );
    cy.get('label[for=filtteri-aikavali] + div .pvm-kentta > .input-default > input').last().focus().type("31.12.2021").clear().type("31.12.2021" );
    cy.get('label').contains("Työmenetelmä").click(); // Klikataan vain ohi aukeavasta valikosta.
    cy.contains('.nappi-ensisijainen', 'Hae toteumia').click({force: true})
    cy.wait('@2021-paikkaukset', {timeout: clickTimeout})
    cy.contains('.viesti' ,'Päivitetään listaa..', {timeout: clickTimeout}).should('not.exist')}

describe('Paikkauskohteet latautuu oikein', function () {
    it('Mene paikkauskohteet välilehdelle palvelun juuresta', function () {
        // Avaa Harja ihan juuresta

        cy.viewport(1100, 2000)
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

        cy.server()
        cy.route('POST', '_/paikkauskohteet-urakalle').as('2021-kohteet')
        cy.contains('.nappi-ensisijainen', 'Hae kohteita', ).click({force: true})
        cy.wait('@2021-kohteet', {timeout: clickTimeout})
    })

    it('Lisää uusi levittimellä tehtätävä paikkauskohde', function () {
        cy.viewport(1100, 2000)
        // siirry paikkauskohteisiin
        avaaPaikkauskohteetSuoraan()
        // Avataan paikkauskohdelomake uuden luomista varten
        cy.get('button').contains('.nappi-ensisijainen', 'Lisää kohde', {timeout: clickTimeout}).click({force: true})
        // Varmistetaan, että sivupaneeli aukesi
        cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
        // annetaan nimi
        cy.get('#form-paikkauskohde-nimi').focus()
        cy.get('#form-paikkauskohde-nimi').type("CPKohde")
        cy.get('label[for=ulkoinen-id] + span > input').type(uniikkiUlkoinenId)
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
        cy.get('button').contains('.nappi-ensisijainen', 'Tallenna', {timeout: clickTimeout}).click({force: true})

        // Varmista, että tallennus onnistui
        cy.get('.toast-viesti', {timeout: 60000}).should('be.visible')

        // Vaihda oikeaan vuoteen
        cy.get('label[for=filtteri-vuosi] + div').filtteriValitse({valinta: '2021'})
        cy.contains('.nappi-ensisijainen', 'Hae kohteita').click()

        // Ja tarkista, että kohde tuli listaan.
        cy.contains('tr.paikkauskohderivi > td > div > span ', 'CPKohde').should('exist')
    })

    it('Tilaa paikkauskohde', function () {
        cy.viewport(1100, 2000)
        // siirry paikkauskohteisiin
        avaaPaikkauskohteetSuoraan()

        cy.server()
        cy.route('POST', '_/tallenna-paikkauskohde-urakalle').as('tilaus')
        cy.route('POST', '_/paikkauskohteet-urakalle').as('kohteet')

        // Avataan paikkauskohdelomake uuden luomista varten
        cy.contains('tr.paikkauskohderivi > td > div > span ', 'CPKohde').click({force: true})
        // Varmistetaan, että sivupaneeli aukesi
        cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
        // Tilaa kohde
        cy.get('button').contains('.nappi-ensisijainen', 'Tilaa', {timout: clickTimeout}).click({force: true})
        // Vahvista tilaus
        cy.get('button').contains('.nappi-ensisijainen', 'Tilaa kohde', {timout: clickTimeout}).click({force: true})
        cy.wait('@tilaus', {timeout: 60000})

    })

    it('Lisää levittimellä tehtävälle paikkauskohteelle toteuma', function () {
        cy.viewport(1100, 2000)
        // siirry paikkauskohteisiin
        avaaPaikkauskohteetSuoraan()

        //Avataan sivupaneeliin
        cy.contains('tr.paikkauskohderivi > td > div > span ', 'CPKohde').click({force: true})
        // Varmistetaan, että sivupaneeli aukesi
        cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
        // Avaa toteuman lisäys paneeli
        cy.get('button').contains('.nappi-toissijainen', 'Lisää toteuma', {timout: clickTimeout}).click({force: true})
        // Varmistetaan, että nyt on 2 sivupaneelia auki
        cy.get('div').find('.overlay-oikealla').should('have.length', 2)

        // Lisätään toteuman tiedot
        //cy.get('label[for=aosa] + span > input').type("4")
        cy.get('label[for=aet] + span > input').type("4")
        //cy.get('label[for=losa] + span > input').type("5")
        cy.get('label[for=let] + span > input').type("5")
        cy.contains('Tallenna').should('be.disabled')
        cy.get('label[for=kaista] + div').valinnatValitse({valinta: '1'})
        cy.get('label[for=ajorata] + div').valinnatValitse({valinta: '2'})
        cy.get('label[for=massatyyppi] + div').valinnatValitse({valinta: 'AB, Asfalttibetoni'})
        cy.get('label[for=kuulamylly] + div').valinnatValitse({valinta: 'AN5'})
        cy.get('label[for=raekoko] + div').valinnatValitse({valinta: '5'})
        cy.get('label[for=massamaara] + span > input').type('5')
        cy.get('label[for=massamenekki] + span > input').type('5')
        cy.get('label[for=leveys] + span > input').type('5')
        cy.get('label[for=pinta-ala] + span > input').type('5')
        cy.contains('Tallenna').should('not.be.disabled')
        cy.contains('Tallenna').click()
        cy.get('.toast-viesti', {timeout: 60000}).should('be.visible')
    })


})

describe('Paikkaustoteumat toimii', function () {
    it('Mene paikkaustoteumat välilehdelle ja lisää toteuma', function () {
        cy.viewport(1100, 2000)
        avaaToteumat()

        cy.get('div .otsikkokomponentti').contains('CPKohde').parent().parent().contains('Lisää toteuma').click()
        //cy.get('label[for=aosa] + span > input').type("4")
        cy.get('label[for=aet] + span > input').type("4")
        //cy.get('label[for=losa] + span > input').type("5")
        cy.get('label[for=let] + span > input').type("5")
        cy.contains('Tallenna').should('be.disabled')
        cy.get('label[for=kaista] + div').valinnatValitse({valinta: '1'})
        cy.get('label[for=ajorata] + div').valinnatValitse({valinta: '2'})
        cy.get('label[for=massatyyppi] + div').valinnatValitse({valinta: 'AB, Asfalttibetoni'})
        cy.get('label[for=kuulamylly] + div').valinnatValitse({valinta: 'AN5'})
        cy.get('label[for=raekoko] + div').valinnatValitse({valinta: '5'})
        cy.get('label[for=massamaara] + span > input').type('5')
        cy.get('label[for=massamenekki] + span > input').type('5')
        cy.get('label[for=leveys] + span > input').type('5')
        cy.get('label[for=pinta-ala] + span > input').type('5')
        cy.contains('Tallenna').should('not.be.disabled')
        cy.contains('Tallenna').click()
        cy.get('.toast-viesti', {timeout: 60000}).should('be.visible')
    })

    it('Tarkastellaan toteumaa', () => {
        cy.viewport(1100, 2000)
        avaaToteumat()

        cy.contains('CPKohde').first().parent().parent().click({force: true})
        cy.get('table.grid > tbody > tr').first().click({force: true})
        cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
        cy.get('button').contains('.nappi-toissijainen', 'Peruuta', {timeout: clickTimeout}).click({force: true})
        cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('not.exist')
    })

    it('Poistetaan toteuma', () => {
        cy.viewport(1100, 2000)
        avaaToteumat();

        // Poista ensimmäinen toteuma
        cy.contains('CPKohde').first().parent().parent().click({force: true})
        cy.get('table.grid > tbody > tr').first().click({force: true})
        cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
        cy.contains('Poista toteuma').click()
        cy.get('.modal', {timeout: clickTimeout}).should('be.visible')
        cy.get('.modal').contains('Poista toteuma').click()
        cy.get('.modal', {timeout: clickTimeout}).should('not.exist')

        // Poistetaan toinenkin toteuma
        cy.get('table.grid > tbody > tr').first().click({force: true})
        cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
        cy.contains('Poista toteuma').click()
        cy.get('.modal', {timeout: clickTimeout}).should('be.visible')
        cy.get('.modal').contains('Poista toteuma').click()
        cy.get('.modal', {timeout: clickTimeout}).should('not.exist')
    })
})

//describe('Päällystysilmoitukset toimii', function () {

    // it('Lisää POT-raportoitava paikkauskohde', function () {
    //     cy.viewport(1100, 2000)
    //     // siirry paikkauskohteisiin
    //     avaaPaikkauskohteetSuoraan()
    //
    //     // Avataan paikkauskohdelomake uuden luomista varten
    //     cy.get('button').contains('.nappi-ensisijainen', 'Lisää kohde', {timeout: clickTimeout}).click({force: true})
    //     // Varmistetaan, että sivupaneeli aukesi
    //     cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
    //     // annetaan nimi
    //     cy.get('label[for=nimi] + input').type(potRaportoitava, {force: true})
    //     cy.get('label[for=ulkoinen-id] + span > input').type("87654321")
    //     // Valitse työmenetelmä
    //     cy.get('label[for=tyomenetelma] + div').valinnatValitse({valinta: 'SMA-paikkaus levittäjällä'})
    //     cy.get('label[for=tie] + span > input').type("13873")
    //     cy.get('label[for=ajorata] + div').valinnatValitse({valinta: '0'})
    //     cy.get('label[for=aosa] + span > input').type("1")
    //     cy.get('label[for=aet] + span > input').type("0")
    //     cy.get('label[for=losa] + span > input').type("1")
    //     cy.get('label[for=let] + span > input').type("1000")
    //     // Ajankohta
    //     cy.get('label[for=alkupvm] + .pvm-kentta > .input-default').type("1.8.2021")
    //     cy.get('label[for=loppupvm] + .pvm-kentta > .input-default').type("1.9.2021")
    //     //Suunnitellut määrät ja summa
    //     cy.get('label[for=suunniteltu-maara] + span > input').type("1111")
    //     cy.get('label[for=yksikko] + div').valinnatValitse({valinta: 'jm'})
    //     cy.get('label[for=suunniteltu-hinta] + span > input').type("200000")
    //     cy.get('button').contains('.nappi-ensisijainen', 'Tallenna', {timeout: clickTimeout}).click({force: true})
    //
    //     // Varmista, että tallennus onnistui
    //     cy.get('.toast-viesti', {timeout: 60000}).should('be.visible')
    // })
    //
    // it('Tilaa POT-raportoitava', function () {
    //     cy.viewport(1100, 2000)
    //     // siirry paikkauskohteisiin
    //     avaaPaikkauskohteetSuoraan()
    //
    //     cy.server()
    //     cy.route('POST', '_/tallenna-paikkauskohde-urakalle').as('tilaus')
    //     cy.route('POST', '_/paikkauskohteet-urakalle').as('kohteet')
    //
    //     // Avataan paikkauskohdelomake uuden luomista varten
    //     cy.contains('tr.paikkauskohderivi > td > div > span ', potRaportoitava).click({force: true})
    //     // Varmistetaan, että sivupaneeli aukesi
    //     cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
    //     // Valitse pot-raportointi
    //     cy.contains('POT-lomake').click({force: true})
    //     // Tilaa kohde
    //     cy.get('button').contains('.nappi-ensisijainen', 'Tilaa', {timout: clickTimeout}).click({force: true})
    //     // Vahvista tilaus
    //     cy.get('button').contains('.nappi-ensisijainen', 'Tilaa kohde', {timout: clickTimeout}).click({force: true})
    //     cy.wait('@tilaus', {timeout: clickTimeout})
    // })

    // it('Avaa POT-lomake', function () {
    //     cy.viewport(1100, 2000)
    //     // siirry paikkauskohteisiin
    //     avaaPaikkauskohteetSuoraan()
    //
    //     // Avataan POT lomake
    //     cy.contains('tr.paikkauskohderivi > td > div > span ', potRaportoitava).click({force: true})
    //     // Varmistetaan, että sivupaneeli aukesi
    //     cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
    //     // Avaa POT lomake
    //     cy.get('button').contains('.nappi-toissijainen', 'Tee päällystysilmoitus', {timout: clickTimeout}).click({force: true})
    //     cy.get('H1').contains("Päällystysilmoitus");
    // })
    //
    // it('Tallenna POT-lomake', function () {
    //
    //     cy.viewport(1100, 2000)
    //     cy.server()
    //     cy.route('POST', '_/hae-urakan-massat-ja-murskeet').as('hae-massat')
    //     cy.route('POST', '_/tallenna-urakan-massa').as('tallenna-massa')
    //     cy.route('POST', '_/tallenna-paallystysilmoitus').as('tallenna-paallystysilmoitus')
    //     // siirry paikkauskohteisiin
    //     avaaPaikkauskohteetSuoraan()
    //
    //     // Avataan POT lomake
    //     cy.contains('tr.paikkauskohderivi > td > div > span ', potRaportoitava).click({force: true})
    //     // Varmistetaan, että sivupaneeli aukesi
    //     cy.get('.overlay-oikealla', {timeout: clickTimeout}).should('be.visible')
    //     // Avaa POT lomake
    //     cy.get('button').contains('.nappi-toissijainen', 'Tee päällystysilmoitus', {timout: clickTimeout}).click({force: true})
    //     cy.get('H1').contains("Päällystysilmoitus");
    //
    //     // Valitse takuuaika
    //     cy.get('label[for=takuuaika] + div').find('div').valinnatValitse({valinta: '2 vuotta'})
    //
    //     // Avaa massamodaali
    //     cy.get('button').contains('.nappi-toissijainen', 'Muokkaa urakan materiaaleja', {timout: clickTimeout}).click({force:true});
    //     cy.wait('@hae-massat', {timeout: clickTimeout})
    //     cy.get('h2').contains('Materiaalikirjasto')
    //     cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
    //
    //     // Lisää massa lomake
    //     cy.get('button').contains('.lisaa-massa','Lisää massa').click({force:true});
    //     cy.get('.lomake-otsikko-pieni').contains('Uusi massa',{ matchCase: false })
    //     cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist')
    //
    //     // Valitse massatyyppi
    //     cy.get('label[for=tyyppi] + div').valinnatValitse({valinta: 'AB, Asfalttibetoni'})
    //     // Valitse Max raekoko
    //     cy.get('label[for=max-raekoko] + div').valinnatValitse({valinta: '5'})
    //     //Nimen tarkenne
    //     cy.get('label[for=nimen-tarkenne] + input').type('AB-bet-5', {force: true})
    //     // Valitse kuulamyllyluokka
    //     cy.get('label[for=kuulamyllyluokka] + div').valinnatValitse({valinta: 'AN7'})
    //     // Valitse litteyslukuluokka
    //     cy.get('label[for=litteyslukuluokka] + div').valinnatValitse({valinta: 'FI15'})
    //     //Dop tarkenne
    //     cy.get('label[for=dop-nro] + input').type('5', {force: true})
    //     // Runkoaineen materiaali - Valitaan eka, koska helppoa ja toivotaan, että ui ei muutu
    //     cy.get('label').contains('Kiviaines').prev().check()
    //     // Ja yritetään löytää sen alta input kentät
    //     // Kiviainesesiintymä
    //     cy.get('.aineiden-muokkaustila').contains('span.kentan-label', 'Kiviainesesiintymä').parent().next().type('AB-AN7-FI15')
    //     cy.get('.aineiden-muokkaustila').contains('span.kentan-label', 'Kuulamyllyarvo').parent().next().type('6')
    //     cy.get('.aineiden-muokkaustila').contains('span.kentan-label', 'Litteysluku').parent().next().type('12')
    //     cy.get('.aineiden-muokkaustila').contains('span.kentan-label', 'Massa-%').parent().next().type('17')
    //
    //     // Sideaineet
    //     cy.get('div.sideaine-komponentti').contains('span','Tyyppi').parent().next().valinnatValitse({valinta: 'Bitumi, 35/50'})
    //     cy.get('div.sideaine-komponentti').contains('.kentan-label', 'Pitoisuus %').parent().next().first().type('17')
    //     cy.get('.massa-lomake').contains('button', 'Tallenna').click({force: true})
    //     cy.wait('@tallenna-massa', {timeout: clickTimeout})
    //
    //     //Sulje massamodaali
    //     cy.get('h2').contains('Materiaalikirjasto')
    //     cy.get('h6').contains('Massat')
    //     cy.get('button.close').click({force: true})
    //
    //     // Yritä valita toimenpide ja muut tärkeät päällystysjutut
    //     cy.get('div.livi-muokkaus-grid').find('td').find('div.alasveto-gridin-kentta').first().valinnatValitse({valinta: 'LTA'})
    //     cy.get('div.livi-muokkaus-grid').find('td').find('div.alasveto-gridin-kentta').eq(1).valinnatValitse({valinta: '0'})
    //     cy.get('div.livi-muokkaus-grid').find('td').find('div.alasveto-gridin-kentta').eq(2).valinnatValitse({valinta: '1'})
    //     cy.get('div.livi-muokkaus-grid').find('td').find('div.alasveto-gridin-kentta').eq(3).valinnatValitse({valinta: 'AB5 AB-bet-5'})
    //     cy.get('div.livi-muokkaus-grid').find('td').find('input').eq(5).type('7')
    //     cy.get('div.livi-muokkaus-grid').find('td').find('input').eq(6).type('10')
    //
    //     // Tallenna
    //     cy.get('#tallenna-paallystysilmoitus').click({force: true})
    //     cy.wait('@tallenna-paallystysilmoitus')
    //     // Palattiinko päällystysilmoituslistaan
    //     cy.get('h1').contains('Paikkauskohteiden päällystysilmoitukset')
    //
    // })

//})

describe('Siivotaan lopuksi', function () {
    before(siivoaKanta);
    // Siivotaan vain jäljet

    it('Tarkista, että kanta on siivottu', function () {
        cy.viewport(1100, 2000)
        // siirry paikkauskohteisiin
        avaaPaikkauskohteetSuoraan()

        cy.contains('tr.paikkauskohderivi > td > div > span ', 'CPKohde').should('not.exist')
        cy.contains('tr.paikkauskohderivi > td > div > span ', potRaportoitava).should('not.exist')
    })
})
