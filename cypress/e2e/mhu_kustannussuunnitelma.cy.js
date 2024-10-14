import * as ks from '../support/kustannussuunnitelmaFns.js';
import transit from "transit-js";
import {avaaKustannussuunnittelu} from "../support/kustannussuunnitelmaFns.js";

// ######### HUOMIOT ##########

// TODO: Testaa osioiden omat yhteenvedot (katso osioiden testien todo-kommentit)
// TODO: Testaa mahdollisesti "Tavoite- ja kattohinta" osion arvojen päivittyminen aina yhden testiblokin jälkeen (katso osioiden testien todo-kommentit)
//       HOX: Lopullinen tavoite- ja kattohinta arvo tarkastetaan kuitenkin sivun refreshin jälkeen, mutta väliaikatietoja olisi kiva saada, jotta
//       olisi helpompi selvitetllä mikä osio tarkalleen tuottaa virheellisiä lukuja, mikäli muutoksia Kustannussuunnitelman koodiin tehdään.


//TODO: Kannattaa harkita tämänkin testipatterin pilkkomista useampaan tiedostoon, jos testien määrä kasvaa vielä suuremmaksi.

//TODO: 1.10 testidata muuttunut. Indeksejä ei löydy kuin ensimmäiselle hoitovuodella. Selvitetävä, miksi aiemmin on löytynyt kahdelle ja
//      varmistettava, että jatkossa testit eivät mene rikki itsestään kun päivä tai hoitokausi muuttuu.

// Täytetään ajax kutsun vastauksen perusteella
const indeksit = [];

function alustaIvalonUrakka() {
    ks.alustaKanta('Ivalon MHU testiurakka (uusi)');
}


// ### Testit ###

describe('Testaa Inarin MHU urakan kustannussuunnitelmanäkymää', function () {
    before(function () {
        alustaIvalonUrakka();
        avaaKustannussuunnittelu('Ivalon MHU testiurakka (uusi)', 'Lappi', indeksit);
    })

    it('Testaa tilayhteenvedon vierityslinkit', function () {
        cy.contains('#tilayhteenveto a', 'Suunnitellut hankinnat').click();
        cy.wait(500)
        cy.get("#hankintakustannukset-osio").should("be.visible")

        cy.contains('#tilayhteenveto a', 'Erillishankinnat').click();
        cy.wait(500)
        cy.get("#erillishankinnat-osio").should("be.visible")

        cy.contains('#tilayhteenveto a', 'Rahavaraukset').click();
        cy.wait(500)
        cy.get("#tavoitehintaiset-rahavaraukset-osio").should("be.visible")

        cy.contains('#tilayhteenveto a', 'Johto- ja hallintokorvaus').click();
        cy.wait(500)
        cy.get("#johto-ja-hallintokorvaus-osio").should("be.visible")

        cy.contains('#tilayhteenveto a', 'Hoidonjohtopalkkio').click();
        cy.wait(500)
        cy.get("#hoidonjohtopalkkio-osio").should("be.visible")

        cy.contains('#tilayhteenveto a', 'Tavoite- ja kattohinta').click();
        cy.wait(500)
        cy.get("#tavoite-ja-kattohinta-osio").should("be.visible")
    });
});


// ---------------------------------
// --- Suunnitellut hankinnat osio ---
// ---------------------------------

describe('Suunnitellut hankinnat osio', function () {
    // #### "Suunnitellut hankinnat" taulukko ####
    describe('Testaa "Suunnitellut hankinnat" taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kiinteahintaiset-tyot').as('tallenna-kiinteahintaiset-tyot');

        });

        it('Taulukon arvot alussa oikein', function () {
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaOtsikot(['Kiinteät', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
                .testaaRivienArvot([1], [0, 0], ['1. hoitovuosi', '2. hoitovuosi', '3. hoitovuosi', '4. hoitovuosi', '5. hoitovuosi'])
                .testaaRivienArvot([1], [0, 1], ['', '', '', '', ''])
                .testaaRivienArvot([1], [0, 2], ['0,00', '0,00', '0,00', '0,00', '0,00'])
                // Vain ensimmäiselle hoitovuodelle on indeksi, joten muut summat ovat tyhjiä.
                .testaaRivienArvot([1], [0, 3], ['0,00', '', '', '', ''])

        });

        it('Muokkaa ensimmäisen vuoden arvoja ilman alaskopiointia', function () {
            // Disabloidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla defaulttina aktiivinen.
            cy.get('input[id="kopioi-hankinnat-tuleville-hoitovuosille"]')
                .should('not.be.checked');

            // Avaa 1. hoitovuosi rivi
            cy.get('#suunnitellut-hankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 0, 0, 1, '1');
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 0, 11, 1, '1', true);

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '', ks.formatoiArvoDesimaalinumeroksi(2),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 2, 1))]);

            // Tarkasta lokakuun indeksikorjaus
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([1, 0], [0, 3],
                    [ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 1, 1))]);

            // Tarkasta Suunnitellut hankinnat osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 1, 2);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 1, 2);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 2);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 1, 2);

        });

        it('Muokkaa ensimmäisen vuoden arvoja alaskopioinnin kanssa', function () {
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 0, 1, 1, '10');
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')


            // -- Arvojen tarkastus ---

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(111),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 111, 1))]);

            // Tarkasta Suunnitellut hankinnat osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 1, 111);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 1, 111);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 111);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 1, 111);

        });

        it('Muokkaa toisen vuoden arvoja ilman alaskopiointia', function () {
            // Avaa 2. hoitovuosi rivi
            cy.get('#suunnitellut-hankinnat-taulukko')
                .taulukonOsaPolussa([1, 1, 0, 0])
                .click();

            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 1, 0, 1, '2');
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 1, 11, 1, '2', true);

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')


            // -- Arvojen tarkastus ---

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(115),
                        ks.formatoiArvoDesimaalinumeroksi(ks.summaaJaIndeksikorjaaArvot(indeksit, [111, 4]))]);

            // Tarkasta Suunnitellut hankinnat osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 2, 4);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 2, 4);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 2, 4);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 2, 4);


        });

        it('Muokkaa toisen vuoden arvoja alaskopioinnin kanssa', function () {
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 1, 1, 1, '20');
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi (katso cypress-lokeista xhr-tageilla merkityt rivit)
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    [
                        'Yhteensä',
                        '',
                        ks.formatoiArvoDesimaalinumeroksi(333),
                        ks.formatoiArvoDesimaalinumeroksi(ks.summaaJaIndeksikorjaaArvot(indeksit, [111, 222]))
                    ]);

            // Tarkasta Suunnitellut hankinnat osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 2, 222);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 2, 222);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 2, 222);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 2, 222);
        });

        it('Muokkaa arvot tuleville hoitokausille', function () {
            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla disabloitu edellisten testien toimesta.
            cy.get('input[id="kopioi-hankinnat-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Avaa 3. hoitovuosi rivi
            cy.get('#suunnitellut-hankinnat-taulukko')
                .taulukonOsaPolussa([1, 2, 0, 0])
                .click();

            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 2, 0, 1, '5');
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kiinteahintaiset-tyot')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#suunnitellut-hankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(513),
                        ks.formatoiArvoDesimaalinumeroksi(ks.summaaJaIndeksikorjaaArvot(indeksit, [111, 222, 60, 60, 60]))]);

            // Tarkasta Suunnitellut hankinnat osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 3, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 3, 0);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 4, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 4, 0);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 5, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 5, 0);
            ks.tarkastaHintalaskurinYhteensaArvo('hankintakustannukset-hintalaskuri',
                [111, 222, 60, 60, 60]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'hankintakustannukset-indeksilaskuri',
                [111, 222, 60, 60, 60]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 3, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 3, undefined);
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 4, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 4, undefined);
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 5, 60);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 5, undefined);
            ks.tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri', [111, 222, 60, 60, 60]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'tavoitehinnan-indeksilaskuri', [111, 222]);

        });
    });

// --

    // #### Määrämitattavien töiden taulukko ####
    //      "Haluan suunnitella myös määrämitattavia töitä toimenpiteelle: X"
    describe('Testaa määrämitattavien töiden taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kustannusarvioitu-tyo').as('tallenna-kustannusarvioitu-tyo');
        });

        // "Haluan suunnitella myös määrämitattavia töitä toimenpiteelle"-taulukon aukaisu
        it('Aktivoidaan määrämitattavien töiden taulukko', function () {
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko').should('not.be.visible');
            cy.get('label[for="laskutukseen-perustuen"]').click();
        });

        it('Taulukon arvot alussa oikein', function () {
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .testaaOtsikot(['', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
                .testaaRivienArvot([1], [0, 0], ['1. hoitovuosi', '2. hoitovuosi', '3. hoitovuosi', '4. hoitovuosi', '5. hoitovuosi'])
                .testaaRivienArvot([1], [0, 1], ['', '', '', '', ''])
                .testaaRivienArvot([1], [0, 2], ['0,00', '0,00', '0,00', '0,00', '0,00'])
                .testaaRivienArvot([1], [0, 3], ['0,00', '', '', '', ''])

        });

        it('Muokkaa ensimmäisen vuoden arvoja ilman alaskopiointia', function () {
            // Disabloidaan "Kopioi hankinnat tuleville hoitovuosille", jonka tulisi olla defaulttina päällä"
            cy.get('input[id="kopioi-hankinnat-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck();

            // Avaa 1. hoitovuosi rivi
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko',
                0, 0, 1, '10');
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko',
                0, 11, 1, '10', true);


            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(20),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 20, 1))]);

            // Tarkasta Suunnitellut hankinnat osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 1, 131);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 1, 131);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 131);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 1, 131);
        });

        it('Muokkaa ensimmäisen vuoden arvoja alaskopioinnin kanssa', function () {
            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko',
                0, 1, 1, '10');
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '', ks.formatoiArvoDesimaalinumeroksi(120),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 120, 1))]);

            // Tarkasta Suunnitellut hankinnat osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 1, 231);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 1, 231);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 1, 231);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 1, 231);
        });

        it('Muokkaa arvot tuleville hoitokausille', function () {
            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla disabloitu edellisten testien toimesta.
            cy.get('input[id="kopioi-hankinnat-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Avaa 2. hoitovuosi rivi
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .taulukonOsaPolussa([1, 1, 0, 0])
                .click();

            ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-laskutukseen-perustuen-taulukko',
                1, 0, 1, '50');
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#suunnitellut-hankinnat-laskutukseen-perustuen-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '', ks.formatoiArvoDesimaalinumeroksi(2520),
                        ks.formatoiArvoDesimaalinumeroksi(ks.summaaJaIndeksikorjaaArvot(indeksit, [120, 600, 600, 600, 600]))]);

            // Tarkasta Suunnitellut hankinnat osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 3, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 3, undefined);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 4, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 4, undefined);
            ks.tarkastaHintalaskurinArvo('hankintakustannukset-hintalaskuri', 5, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hankintakustannukset-indeksilaskuri', 5, undefined);
            ks.tarkastaHintalaskurinYhteensaArvo('hankintakustannukset-hintalaskuri', [231, 822, 660, 660, 660]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'hankintakustannukset-indeksilaskuri', [231, 822]);

            // Tarkasta Tavoite- ja kattohinta osion laskennalliset arvot
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 3, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 3, undefined);
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 4, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 4, undefined);
            ks.tarkastaHintalaskurinArvo('tavoitehinnan-hintalaskuri', 5, 660);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'tavoitehinnan-indeksilaskuri', 5, undefined);
            ks.tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri', [231, 822, 660, 660, 660]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'tavoitehinnan-indeksilaskuri', [231, 822]);
        });
    });
});


// -----------------------------
// --- Erillishankinnat osio ---
// -----------------------------

describe('Erillishankinnat osio', function () {
    describe('Testaa "erillishankinnat" taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kustannusarvioitu-tyo').as('tallenna-kustannusarvioitu-tyo');

        });

        it('Taulukon arvot alussa oikein', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')

            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" on aktiivinen.
            // Ota ota se pois
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked');

            cy.get('#erillishankinnat-taulukko')
                .testaaOtsikot(['', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
                .testaaRivienArvot([1], [0, 0], ['Erillishankinnat'])
                .testaaRivienArvot([1], [0, 1], [''])
                .testaaRivienArvot([1], [0, 2], ['0,00'])
                .testaaRivienArvot([1], [0, 3], ['0,00'])
        });

        it('Muokkaa erillishankintojen arvoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
            // Avaa Erillishankinnat alitaulukko
            cy.get('#erillishankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#erillishankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('not.be.checked')
                .check();

            ks.muokkaaLaajennaRivinArvoa(
                'erillishankinnat-taulukko',
                0, 0, 1, '10')
            ks.muokkaaLaajennaRivinArvoa(
                'erillishankinnat-taulukko',
                0, 11, 1, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            // FIXME: Erillishankinnat tuottaa jostain syystä tallenna-budjettitavoite kutsun duplikaattina!
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#erillishankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(20),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 20, 1))]);

            // Tarkasta Suunnitellut hankinnat osion yhteenveto
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 1, 20);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 1, 20);
        })

        it('Muokkaa vahinkojen arvoja jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // NOTE: Tässä oletetaan, rivi on laajennettu ja "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            //       aktivoitu edellisessä testissä.

            // Täytä ensimmäisen kuukauden arvo
            ks.muokkaaLaajennaRivinArvoa(
                'erillishankinnat-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#erillishankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(120),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 120, 1))]);

            // Tarkasta hankintakustannukset osion yhteenveto
            cy.log('Tarkastetaan osion yhteenveto...');
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 1, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 1, 120);
        })

        it('Muokkaa arvot tuleville hoitokausille', function () {
            // Sulje Erillishankinnat alitaulukko
            cy.get('#erillishankinnat-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0, 0])
                .click();

            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä arvo "Erillishankinnat" riville 1. hoitovuodelle
            ks.muokkaaRivinArvoa('erillishankinnat-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Valitse 3. hoitovuosi alasvetovalikosta
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('3.')
                .click();

            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille".
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Täytä arvo "Erillishankinnat" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            ks.muokkaaRivinArvoa('erillishankinnat-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#erillishankinnat-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        // Erillishankinnat-taulukon yhteenvetorivillä lasketaan kaikki hoitovuodet yhteen.
                        //   Sama pitää ottaa huomioon testissäkin.
                        ks.formatoiArvoDesimaalinumeroksi(/*1.*/ 120 + /*2.*/ 0 + /*3.*/ 120 + /*4.*/ 120 + /*5.*/ 120),
                        ks.formatoiArvoDesimaalinumeroksi(
                            ks.summaaLuvut(
                                /*1.*/ks.indeksikorjaaArvo(indeksit, 120, 1),
                                /*2.*/ ks.indeksikorjaaArvo(indeksit, 0, 2),
                                /*3.*/  ks.indeksikorjaaArvo(indeksit, 120, 3),
                                /*4.*/ ks.indeksikorjaaArvo(indeksit, 120, 4),
                                /*5.*/ ks.indeksikorjaaArvo(indeksit, 120, 5)
                            ))]);



            // Tarkasta osion yhteenveto
            cy.log('Tarkastetaan osion yhteenveto...');
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 1, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 1, 120);
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 2, 0);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 2, 0);
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 3, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 3, undefined);
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 4, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 4, undefined);
            ks.tarkastaHintalaskurinArvo('erillishankinnat-hintalaskuri', 5, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'erillishankinnat-indeksilaskuri', 5, undefined);
            ks.tarkastaHintalaskurinYhteensaArvo('erillishankinnat-hintalaskuri', [120, 0, 120, 120, 120]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'erillishankinnat-indeksilaskuri', [120, 0]);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Palauta 1. hoitovuosi valituksi muita testejä varten
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('1.')
                .click();

            // Disabloi "Kopioi hankinnat tuleville hoitovuosille".
            cy.get('div[data-cy="erillishankinnat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck()
        });
    })
});





// -------------------------------
// --- Hoidonjohtopalkkio osio ---
// -------------------------------

describe('Hoidonjohtopalkkio osio', function () {
    describe('Testaa "hoidonjohtopalkkio" taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kustannusarvioitu-tyo').as('tallenna-kustannusarvioitu-tyo');
        });

        it('Taulukon arvot alussa oikein', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')

            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" ei ole aktiivinen.
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')

            cy.get('#hoidonjohtopalkkio-taulukko')
                .testaaOtsikot(['', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
                .testaaRivienArvot([1], [0, 0], ['Hoidonjohtopalkkio'])
                .testaaRivienArvot([1], [0, 1], [''])
                .testaaRivienArvot([1], [0, 2], ['0,00'])
                .testaaRivienArvot([1], [0, 3], ['0,00'])
        });

        it('Muokkaa hoidonjohtopalkkion arvoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
            // Avaa hoidonjohtopalkkio alitaulukko
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0])
                .click();

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('not.be.checked')
                .check();

            ks.muokkaaLaajennaRivinArvoa(
                'hoidonjohtopalkkio-taulukko',
                0, 0, 1, '10')
            ks.muokkaaLaajennaRivinArvoa(
                'hoidonjohtopalkkio-taulukko',
                0, 11, 1, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            // FIXME: Hoidonjohtopalkkiot taulukko tuottaa jostain syystä tallenna-budjettitavoite kutsun duplikaattina!
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#hoidonjohtopalkkio-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(20),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 20, 1))]);

            // Tarkasta hoidonjohtopalkkio osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 1, 20);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 1, 20);
        })

        it('Muokkaa hoidonjohtopalkkion jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // NOTE: Tässä oletetaan, rivi on laajennettu ja "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            //       aktivoitu edellisessä testissä.

            // Täytä ensimmäisen kuukauden arvo
            ks.muokkaaLaajennaRivinArvoa(
                'hoidonjohtopalkkio-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#hoidonjohtopalkkio-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(120),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 120, 1))]);

            // Tarkasta hoidonjohtopalkkio osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 1, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 1, 120);
        })

        it('Muokkaa hoidonjohtopalkkion arvot tuleville hoitokausille', function () {
            // Disabloi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('be.checked')
                .uncheck();

            // Sulje hoidonjohtopalkkio alitaulukko
            cy.get('#hoidonjohtopalkkio-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 0, 0])
                .click();

            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä arvo "hoidonjohtopalkkio" riville 1. hoitovuodelle
            ks.muokkaaRivinArvoa('hoidonjohtopalkkio-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Valitse 3. hoitovuosi alasvetovalikosta
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('3.')
                .click();

            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille".
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Täytä arvo "hoidonjohtopalkkio" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            ks.muokkaaRivinArvoa('hoidonjohtopalkkio-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.get('#hoidonjohtopalkkio-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        // Hoidonjohtopalkkiot-taulukon yhteenvetorivillä lasketaan kaikki hoitovuodet yhteen.
                        //   Sama pitää ottaa huomioon testissäkin.
                        ks.formatoiArvoDesimaalinumeroksi(/*1.*/ 120 + /*2.*/ 0 + /*3.*/ 120 + /*4.*/ 120 + /*5.*/ 120),
                        ks.formatoiArvoDesimaalinumeroksi(
                            ks.summaaLuvut(
                                /*1.*/ks.indeksikorjaaArvo(indeksit, 120, 1),
                                /*2.*/ ks.indeksikorjaaArvo(indeksit, 0, 2),
                                /*3.*/  ks.indeksikorjaaArvo(indeksit, 120, 3),
                                /*4.*/ ks.indeksikorjaaArvo(indeksit, 120, 4),
                                /*5.*/ ks.indeksikorjaaArvo(indeksit, 120, 5)
                            ))]);


            // Tarkasta hoidonjohtopalkkio osion yhteenveto
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 1, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 1, 120);
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 2, 0);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 2, 0);
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 3, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 3, undefined);
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 4, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 4, undefined);
            ks.tarkastaHintalaskurinArvo('hoidonjohtopalkkio-hintalaskuri', 5, 120);
            ks.tarkastaIndeksilaskurinArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', 5, undefined);
            ks.tarkastaHintalaskurinYhteensaArvo('hoidonjohtopalkkio-hintalaskuri', [120, 0, 120, 120, 120]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'hoidonjohtopalkkio-indeksilaskuri', [120, 0]);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Palauta 1. hoitovuosi valituksi muita testejä varten
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('1.')
                .click();

            // Disabloi "Kopioi hankinnat tuleville hoitovuosille".
            cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck()
        });
    })
});


// -----------------------------------
// --- Tavoite- ja kattohinta osio ---
// -----------------------------------

describe('2019-2020 Tavoite- ja kattohinta osio', function () {
    //TODO: Kattohinta: Tulevaisuudessa 2019 ja 2020 alkaneille urakoille käyttäjä syöttää kattohinnan manuaalisesti ✓
    //       * Kenttien validointi: Ko vuoden Kattohinta ei saa olla pienempi kuin ko vuoden Tavoitehinta: Loogista, mutta varmistettava.
    //       * Virheilmoitus käyttäjälle: "Kattohinta ei voi olla pienempi kuin tavoitehinta."
    //       * Lasketaan indeksikorjaus ✓
    //       * Ts. seuraavat 5 vuotta tulee olemaan urakoita, joissa kattohinta pitää kirjata manuaalisesti

    // TODO: Nämä meni rikki 1.10, pitää tehdä uusi testiurakka, joka alkaa aina 2019 tai 2020.
    // beforeEach(function () {
    //     cy.intercept('POST', '_/vahvista-kustannussuunnitelman-osa-vuodella')
    //         .as('vahvista-kustannussuunnitelman-osa-vuodella');
    //
    //     cy.intercept('POST', '_/kumoa-suunnitelman-osan-vahvistus-hoitovuodelle')
    //         .as('kumoa-suunnitelman-osan-vahvistus-hoitovuodelle');
    // });
    //
    // // Alkutila
    // // Muokkaa käsin arvoja
    // it('Tarkista oletusarvot', () => {
    //     ks.tarkistaKattohinta(1, '0,00');
    //     ks.tarkistaKattohinta(2, '0,00');
    //     ks.tarkistaKattohinta(3, '0,00');
    //     ks.tarkistaKattohinta(4, '0,00');
    //     ks.tarkistaKattohinta(5, '0,00');
    // })
    //
    // it('Aseta kattohinta ja tarkista indeksikorjaus', () => {
    //     ks.taytaKattohinta(1, 1000);
    //     ks.tarkistaKattohinta(1, 1000)
    //     ks.tarkistaIndeksikorjattuKH(1, '1 068,43 €')
    //
    //     ks.taytaKattohinta(2, 2000);
    //     ks.tarkistaKattohinta(2, 2000)
    //     ks.tarkistaIndeksikorjattuKH(2, '2 289,76 €')
    //
    //     ks.taytaKattohinta(3, 3000);
    //     ks.tarkistaKattohinta(3, 3000)
    //     ks.tarkistaIndeksikorjattuKH(3, null)
    //
    //     ks.taytaKattohinta(4, 4000);
    //     ks.tarkistaKattohinta(4, 4000)
    //     ks.tarkistaIndeksikorjattuKH(3, null)
    //
    //     ks.taytaKattohinta(5, 5000);
    //     ks.tarkistaKattohinta(5, 5000)
    //     ks.tarkistaIndeksikorjattuKH(3, null)
    // })
});


describe('Tarkasta tallennetut arvot', function () {
    describe('Lataa sivu uudestaan ja tarkasta, että kaikki osioissa tallennettu data löytyy.', function () {
        it('Lataa sivu', function () {

            // HUOM:
            //   Reload saattaa cancelata meneillään olevan tallennus XHR-kyselyn edeltävästä testistä.
            //   Siksi on tärkeää käyttää cy.wait-komentoa edeltävissä testeissä, jotka tekevät muokkauksia ja tallentavat arvoja tietokantaan.
            //   Jokainen tallennuskysely tulee interceptata ja odottaa erikseen cy.wait-kutsulla. (Katso esimerkkiä testeistä).
            //   Näin varmistutaan, että kaikki arvot on tallennettu tietokantaan ennen lopullista arvojen testaamista.
            cy.reload();
            cy.get('.ajax-loader', {timeout: 40000}).should('not.exist');
        });
    });

    // Tavoite- ja kattohinta osion yhteenvetolaatikoiden testit
    describe('Testaa tavoite- ja kattohinta osion yhteenvetolaatikot', function () {
        it('Testaa arvot tavoite- ja kattohinta osiossa', function () {
            // Suunnitellut hankinnat + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio
            ks.tarkastaHintalaskurinYhteensaArvo('tavoitehinnan-hintalaskuri',
                [471, 822, 900, 900, 900]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'tavoitehinnan-indeksilaskuri',
                [471, 822, 900, 900, 900]);

            // (Suunnitellut hankinnat + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1
            ks.tarkastaHintalaskurinYhteensaArvo('kattohinnan-hintalaskuri',
                [471 * 1.1, 822 * 1.1, 900 * 1.1, 900 * 1.1, 900 * 1.1]);
            ks.tarkastaIndeksilaskurinYhteensaArvo(indeksit, 'kattohinnan-indeksilaskuri',
                [471 * 1.1, 822 * 1.1, 900 * 1.1, 900 * 1.1, 900 * 1.1]);
        });
    });
});
