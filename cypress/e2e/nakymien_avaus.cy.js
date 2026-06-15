import * as ks from "../support/kustannussuunnitelmaFns.js";
import {avaaHarjaTimeoutilla} from "../support/apurit.js";

const clickTimeout = 6000;
const visibleTimeout = 30000;
const urakanNimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)';

// Alustetaan yllänimetty urakka Kustannussuunnittelua varten
function alustaUrakkaKustannussuunnitteluun() {
    ks.alustaKanta(urakanNimi);
}

describe('Päänäkymien avaamiset', function () {
    beforeEach(function () {
        cy.viewport(1100, 2000)
        avaaHarjaTimeoutilla();
    })

    it("Urakkavalinta listan kautta toimii", function () {
        cy.get('[data-cy="haku-lista-item"]').contains('Pohjois-Suomi').click()
        cy.get('[data-cy="haku-lista-item"]').contains('Aktiivinen Oulu Testi').click()
        cy.contains('Aktiivinen Oulu Testi')
    })

    it("Raportit välilehti toimii", function () {
        cy.intercept( '_/hae-raportit' ).as('hae-raportit')
        cy.contains('a.nav-link', 'Raportit').click()
        // Odota raporttien latautumista, jotta raportti voidaan valita
        cy.wait('@hae-raportit', { timeout: visibleTimeout })
            .its('response.statusCode').should('equal', 200)
        cy.contains('div.valittu', 'Valitse').click()
        cy.contains('.harja-alasvetolistaitemi a', "Ilmoitusraportti").click()
        cy.contains('label.checkbox-label', "Valittu aikaväli").should('exist')
        cy.contains('label.checkbox-label', "Näytä urakka-alueet eriteltynä").should('exist')
        cy.contains('Hupsista').should('not.exist')

    })

    it("Tilannekuva välilehti toimii", function () {
        // Käytä ankkuria suoraan, jotta vältetään "element is detached" -flaky re-renderin aikana
        cy.contains('a.nav-link', 'Tilannekuva', {timeout: clickTimeout}).should('be.visible').click()
        cy.contains('div#tk-suodattimet a.klikattava', "Nykytilanne", { timeout: 10000 }).should('exist')
        cy.contains('Hupsista').should('not.exist')
    })

    it("Ilmoitukset välilehti toimii", function () {
        cy.contains('a.nav-link', 'Ilmoitukset').click()
        cy.contains('div.livi-grid th', "Urakka", { timeout: 10000 }).should('exist')
        cy.contains('Hupsista').should('not.exist')
    })

    it("Tienpidon luvat välilehti toimii", function () {
        cy.contains('a.nav-link', 'Tiepidon luvat').click();
        cy.contains('button', "Hae lupia", { timeout: visibleTimeout }).should('be.visible');
        cy.contains('Hupsista').should('not.exist')
    })

    it("Urakoiden tilanne välilehti toimii", function () {
        cy.contains('a.nav-link', 'Urakoiden tilanne').click()
        cy.contains('h1', "Urakoiden tilanne", { timeout: visibleTimeout }).should('be.visible');
        cy.contains('Hupsista').should('not.exist')
    })

    it("Info -sivu toimii", function () {
        cy.wait(100)
        cy.get('i.icon.ti.ti-user').closest('a, button').click();
        cy.contains('a.dropdown-item', 'INFO').click();
        cy.contains('Hupsista').should('not.exist')
        cy.contains('Saavutettavuusseloste ').should('exist')
    })
})

describe('MH-Urakan näkymien avaamiset', function () {
    beforeEach(function () {
        cy.viewport(1100, 2000)
        avaaHarjaTimeoutilla();
    })

    it("Avaa Yleiset, Työmaapäiväkirja Turvallisuus", function () {
        alustaUrakkaKustannussuunnitteluun();
        cy.get('[data-cy="haku-lista-item"]').contains('Lappi').click()
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] button', urakanNimi, {timeout: clickTimeout}).click()


        cy.get('[data-cy=tabs-taso1-Tyomaapaivakirja]').click()
        cy.contains('Työmaapäiväkirja').should('exist')
        cy.get('[data-cy=tabs-taso1-Yleiset]').click()
        cy.contains('Yleiset tiedot').should('exist')
        cy.get('[data-cy=tabs-taso1-Turvallisuus]').click()
        cy.contains('Turvallisuuspoikkeamat').should('exist')
    })

    // Ohitetaan testi sen flakeyden takia. Kustannussuunnitelma avataan tässä testissä jotenkin siten, että sen tila ei ole alustunut ja testi kaatuu kokonaan
    it("Avaa Suunnittelun alatabit", function () {
        cy.get('[data-cy="haku-lista-item"]').contains('Lappi').click()
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] button', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry suunnittelu päätabille
        cy.get('[data-cy=tabs-taso1-Suunnittelu]').click()
        // Käydään alatabit läpi
        cy.get('[data-cy=tabs-taso2-Suolarajoitukset]').click()
        cy.contains('Urakan suolarajoitukset hoitovuosittain').should('exist')
        cy.get('[data-cy="tabs-taso2-Tehtava- ja maaraluettelo"]').click()
        cy.contains('Tehtävä ja määräluettelo').should('exist')
        //cy.get('[data-cy=tabs-taso2-Kustannussuunnitelma]').click()
        //cy.contains('Suunnitelluista kustannuksista muodostetaan summa Sampon kustannussuunnitelmaa varten.', {timeout: clickTimeout}).should('exist')
    })

    it("Avaa Kulut ja sen alatabit", function () {
        cy.get('[data-cy="haku-lista-item"]').contains('Lappi').click()
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] button', urakanNimi, {timeout: clickTimeout}).click()

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
        cy.get('[data-cy="haku-lista-item"]').contains('Lappi').click()
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] button', urakanNimi, {timeout: clickTimeout}).click()

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
        cy.get('[data-cy="tabs-taso2-Rajoitusalueiden suola"]').click()
        cy.contains('Rajoitusalueiden suolatoteumat').should('exist')
        cy.get('[data-cy=tabs-taso2-Talvisuola]').click()
        cy.contains('Hae suolatoteumia tieosoiteväliltä').should('exist')
        cy.get('[data-cy=tabs-taso2-Tehtavat]').click()
        cy.contains('Määrämitattavat').should('exist')
    })

    it("Avaa Laadunseuranta ja sen alatabit", function () {
        cy.get('[data-cy="haku-lista-item"]').contains('Lappi').click()
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        cy.wait(100)
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] button', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry Laadunseuranta päätabille
        cy.get('[data-cy=tabs-taso1-Laadunseuranta]').click()
        // Käydään alatabit läpi
        cy.get('[data-cy=tabs-taso2-Talvihoitoreititys]').click()
        cy.contains('Talvihoitoreititys').should('exist')
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
        cy.get('[data-cy="haku-lista-item"]').contains('Lappi').click()
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] button', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry Lupkaukset ja tavoitteet päätabille
        cy.get('[data-cy="tabs-taso1-Lupaukset ja tavoitteet"]').click()
        // Käydään alatabit läpi
        cy.get('[data-cy=tabs-taso2-Valitavoitteet]').click()
        cy.contains('Urakkakohtaiset määräaikaan mennessä tehtävät työt').should('exist')
        cy.get('[data-cy=tabs-taso2-Lupaukset]').click()
        cy.contains('Lupaukset').should('exist')
    })


    it("Avaa Paikkaukset ja sen alatabit", function () {
        cy.get('[data-cy="haku-lista-item"]').contains('Lappi').click()
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] button', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry Paikkaukset päätabille
        cy.get('[data-cy=tabs-taso1-Paikkaukset]').click()
        cy.get('[data-cy="tabs-taso2-Paallystysurakoiden paikkaukset"]').click()
        cy.contains('Elinvoimakeskus').should('exist')

        // Avaa toteumat
        cy.get('[data-cy="tabs-taso2-Kohteiden toteumat"]').click();
        cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist');
        cy.wait(1000);
        cy.contains('Toteuman tieosoite').should('exist')

    })

    it("Avaa Välikatselmus", function () {
        cy.get('[data-cy="haku-lista-item"]').contains('Lappi').click()
        cy.get('.ajax-loader', {timeout: visibleTimeout}).should('not.exist')
        cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Hoito'})
        // Asetettu urakka, joka varmasti menee joskus vanhaksi
        cy.contains('[data-cy=urakat-valitse-urakka] button', urakanNimi, {timeout: clickTimeout}).click()

        // Siirry Välikatselmus päätabille
        cy.get('[data-cy=tabs-taso1-Valikatselmus]').click()
        cy.contains('Välikatselmus').should('exist')
        cy.contains('Yhteenveto').should('exist')
        cy.contains('Hoitovuoden lopun tavoitehinta').should('exist')
        cy.contains('Hoitovuoden lopun kattohinta').should('exist')
    })

})
