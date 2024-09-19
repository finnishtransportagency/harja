import * as ks from "../support/kustannussuunnitelmaFns.js";
let clickTimeout = 6000;
let urakanNimi = 'Kittilän MHU 2019-2024';

// Alustetaan yllänimetty urakka Kustannussuunnittelua varten
function alustaUrakkaKustannussuunnitteluun() {
    ks.alustaKanta(urakanNimi);
}

describe('Päänäkymien avaamiset', function () {
    beforeEach(function () {
        cy.visit("/")
    })

    it("Urakkavalinta listan kautta toimii", function () {
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa').click()
        cy.contains('.haku-lista-item', 'Aktiivinen Oulu Testi').click()
        cy.contains('Aktiivinen Oulu Testi')
    })

    it("Raportit välilehti toimii", function () {
        cy.contains('ul#sivut a span', 'Raportit').click()
        cy.contains('div.valittu', 'Valitse').click()
        cy.contains('.harja-alasvetolistaitemi a', "Ilmoitusraportti").click()
        cy.contains('label.checkbox-label', "Valittu aikaväli").should('exist')
        cy.contains('label.checkbox-label', "Näytä urakka-alueet eriteltynä").should('exist')
        cy.contains('Hupsista').should('not.exist')
    })

    it("Tilannekuva välilehti toimii", function () {
        cy.contains('ul#sivut a span', 'Tilannekuva').click()
        cy.contains('div#tk-suodattimet a.klikattava', "Nykytilanne").should('exist')
        cy.contains('Hupsista').should('not.exist')
    })

    it("Ilmoitukset välilehti toimii", function () {
        cy.contains('ul#sivut a span', 'Ilmoitukset').click()
        cy.contains('div.livi-grid th', "Urakka").should('exist')
        cy.contains('Hupsista').should('not.exist')
    })

    it("Tienpidon luvat välilehti toimii", function () {
        cy.contains('ul#sivut a span', 'Tienpidon luvat').click()
        cy.contains('button', "Hae lupia").should('exist')
        cy.contains('Hupsista').should('not.exist')
    })

    it("Info -sivu toimii", function () {
        cy.contains('ul div#info a span', 'INFO').click()
        cy.contains('Hupsista').should('not.exist')
        cy.contains('Harja uutiset').should('exist')
    })
})

describe('MH-Urakan näkymien avaamiset', function () {
    it("Avaa Yleiset, Työmaapäiväkirja Turvallisuus", function () {
        alustaUrakkaKustannussuunnitteluun();
        cy.viewport(1100, 2000)
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Lappi').click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimi, {timeout: clickTimeout}).click()


        cy.get('[data-cy=tabs-taso1-Tyomaapaivakirja]').click()
        cy.contains('Työmaapäiväkirja').should('exist')
        cy.get('[data-cy=tabs-taso1-Yleiset]').click()
        cy.contains('Yleiset tiedot').should('exist')
        cy.get('[data-cy=tabs-taso1-Turvallisuus]').click()
        cy.contains('Turvallisuuspoikkeamat').should('exist')
    })

    // Ohitetaan testi sen flakeyden takia. Kustannussuunnitelma avataan tässä testissä jotenkin siten, että sen tila ei ole alustunut ja testi kaatuu kokonaan
    it.skip("Avaa Suunnittelun alatabit", function () {
        cy.viewport(1100, 2000)
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Lappi').click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry suunnittelu päätabille
        cy.get('[data-cy=tabs-taso1-Suunnittelu]').click()
        // Käydään alatabit läpi
        cy.get('[data-cy=tabs-taso2-Suolarajoitukset]').click()
        cy.contains('Urakan suolarajoitukset hoitovuosittain').should('exist')
        cy.get('[data-cy="tabs-taso2-Tehtavat ja maarat"]').click()
        cy.contains('Tehtävät ja määrät').should('exist')
        cy.get('[data-cy=tabs-taso2-Kustannussuunnitelma]').click()
        cy.contains('Suunnitelluista kustannuksista muodostetaan summa Sampon kustannussuunnitelmaa varten.', {timeout: clickTimeout}).should('exist')
    })

    it("Avaa Kulut ja sen alatabit", function () {
        cy.viewport(1100, 2000)
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Lappi').click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry Kulut päätabille
        cy.get('[data-cy=tabs-taso1-Kulut]').click()
        // Käydään alatabit läpi
        cy.get('[data-cy="tabs-taso2-Kustannusten seuranta"]').click()
        cy.contains('Kustannusten seuranta').should('exist')
        cy.get('[data-cy=tabs-taso2-Maksuerat]').click()
        cy.contains('Maksuerät').should('exist')
        cy.get('[data-cy=tabs-taso2-Laskutusyhteenveto]').click()
        cy.contains('Laskutusyhteenvedon muoto').should('exist')
        cy.get('[data-cy="tabs-taso2-Kulujen kohdistus"]').click()
        cy.contains('Kulujen kohdistus').should('exist')
    })

    it("Avaa Toteumat ja sen alatabit", function () {
        cy.viewport(1100, 2000)
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Lappi').click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry Toteumat päätabille
        cy.get('[data-cy=tabs-taso1-Toteumat]').click()
        // Käydään alatabit läpi
        
        // Vaatii ominaisuuden :tierekisterin-varusteet - ja meillä ei ole vielä kyvykkyyttä
        // Cypress testeistä tarkistaa, että onko ominaisuus käytössä vai ei
        //cy.get('[data-cy="tabs-taso2-Vanhat varustekirjaukset (Tierekisteri)"]').click()
        //cy.contains('Vanhat varustekirjaukset Harjassa').should('exist')

        // Vaatii ominaisuuden :tierekisterin-varusteet - ja meillä ei ole vielä kyvykkyyttä
        //cy.get('[data-cy=tabs-taso2-Varusteet]').click()
        //cy.contains('Varustetoimenpiteet').should('exist')
        cy.get('[data-cy="tabs-taso2-Muut materiaalit"]').click()
        cy.contains('Materiaalien käyttö').should('exist')
        cy.get('[data-cy="tabs-taso2-Pohjavesialueiden suola"]').click()
        cy.contains('Pohjavesialueiden suolatoteumat').should('exist')
        cy.get('[data-cy=tabs-taso2-Talvisuola]').click()
        cy.contains('Hae suolatoteumia tieosoiteväliltä').should('exist')
        cy.get('[data-cy=tabs-taso2-Tehtavat]').click()
        cy.contains('Määrämitattavat').should('exist')
    })

    it("Avaa Laadunseuranta ja sen alatabit", function () {
        cy.viewport(1100, 2000)
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Lappi').click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry Laadunseuranta päätabille
        cy.get('[data-cy=tabs-taso1-Laadunseuranta]').click()
        // Käydään alatabit läpi
        cy.get('[data-cy=tabs-taso2-Mobiilityokalu]').click()
        cy.contains('Esittely').should('exist')
        cy.get('[data-cy=tabs-taso2-Siltatarkastukset]').click()
        cy.contains('Sillat').should('exist')
        cy.get('[data-cy="tabs-taso2-Sanktiot ja bonukset"]').click()
        cy.contains('Sanktiot, bonukset ja arvonvähennykset').should('exist')
        cy.get('[data-cy=tabs-taso2-Laatupoikkeamat]').click()
        cy.contains('Laatupoikkeamat').should('exist')
        cy.get('[data-cy=tabs-taso2-Tarkastukset]').click()
        cy.contains('Tarkastukset').should('exist')
    })

    it("Avaa Lupaukset ja tavoitteet ja sen alatabit", function () {
        cy.viewport(1100, 2000)
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Lappi').click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry Lupkaukset ja tavoitteet päätabille
        cy.get('[data-cy="tabs-taso1-Lupaukset ja tavoitteet"]').click()
        // Käydään alatabit läpi
        cy.get('[data-cy=tabs-taso2-Valitavoitteet]').click()
        cy.contains('Urakkakohtaiset määräaikaan mennessä tehtävät työt').should('exist')
        cy.get('[data-cy=tabs-taso2-Lupaukset]').click()
        cy.contains('Lupaukset').should('exist')
    })


    it("Avaa Paikkaukset ja sen alatabit", function () {
        cy.viewport(1100, 2000)
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Lappi').click()
        cy.get('.ajax-loader', {timeout: 30000}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] li', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry Paikkaukset päätabille
        cy.get('[data-cy=tabs-taso1-Paikkaukset]').click()
        // Käydään alatabit läpi
        cy.get('[data-cy="tabs-taso2-Paallystysurakoiden paikkaukset"]').click()
        cy.contains('ELY').should('exist')
        cy.get('[data-cy=tabs-taso2-Toteumat]').click()
        cy.contains('Toteuman tieosoite').should('exist')
    })
})
