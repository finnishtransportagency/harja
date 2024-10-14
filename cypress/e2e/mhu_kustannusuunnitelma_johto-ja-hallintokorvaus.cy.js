import * as ks from '../support/kustannussuunnitelmaFns.js';
import transit from "transit-js";
import {avaaKustannussuunnittelu} from "../support/kustannussuunnitelmaFns.js";

// Täytetään ajax kutsun vastauksen perusteella
const indeksit = [];

function alustaIvalonUrakka() {
    ks.alustaKanta('Ivalon MHU testiurakka (uusi)');
}

// --------------------------------------
// --- Johto- ja hallintokorvaus osio ---
// --------------------------------------

// FIXME: Tämäkin osio on muuttunut erilaiseksi 2022 ja myöhemmin alkaneille urakoille.
//        Katso tavoite- ja kattohintaosion kommentti.

describe('Johto- ja hallintokorvaus osio', function () {

    before(function () {
        alustaIvalonUrakka();
        avaaKustannussuunnittelu('Ivalon MHU testiurakka (uusi)', 'Lappi', indeksit);
    })

    describe('Testaa "tuntimäärät ja -palkat" taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-johto-ja-hallintokorvaukset').as('tallenna-johto-ja-hallintokorvaukset');
        });

        it('Taulukon arvot alussa oikein', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')

            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" ei ole aktiivinen.
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked');

            /*cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .testaaOtsikot(['Toimenkuva', 'Tunnit/kk, h', 'Tuntipalkka, €', 'Yhteensä/kk', 'kk/v'])
                .testaaRivienArvot([1], [0, 0], ['Sopimusvastaava'])
                .testaaRivienArvot([1], [0, 1], [''])
                .testaaRivienArvot([1], [0, 2], [''])
                .testaaRivienArvot([1], [0, 3], [''])
                .testaaRivienArvot([1], [0, 4], ['12'])
             */
        });

        it('Muokkaa Sopimusvastaava-toimenkuvan tunteja ja palkkoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
            // Avaa 'Vastuunalainen työnjohtaja' alitaulukko
            ks.toggleLaajennaRivi('toimenkuvat-taulukko', 'Vastuunalainen työnjohtaja', true);

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#toimenkuvat-taulukko table.grid tbody tr').eq(2).find('td').find('input')
                .should('not.be.checked')
                .check();


            // Aseta tunnit/kk 'Vastuunalainen työnjohtaja'
            cy.get('#toimenkuvat-taulukko table.grid tbody tr').eq(2).find('td')
                // Avatusta taulukkorivista löytyy toinen taulukko, jossa on kuukausittaiset arvo
                .find('.grid')
                // Ja koska "Suunnittele maksuerät kuukausittain" on valittu, niin lisätään arvo ensimmäiseen
                .find('tbody tr').eq(0).find('td').eq(1).find('input')
                .clear().type('10').blur();


            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')

            // Disabloi "Suunnittle maksuerät kuukausittain" (seuraavia testejä varten)
            cy.get('#toimenkuvat-taulukko table.grid tbody tr').eq(2).find('td').find('input')
                // Nyt kun kaikki input laatikot on auki, niin otetaan niistä vain ensimmäinen, eli checkbox
                .eq(0)
                .should('be.checked')
                .uncheck();

            // Sulje "Sopimusvastaava" alitaulukko (seuraavia testejä varten)
            ks.toggleLaajennaRivi('toimenkuvat-taulukko', 'Vastuunalainen työnjohtaja', true);
        });

        xit('Muokkaa Sopimusvastaava-toimenkuvan tunteja ja palkkoja jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // Avaa "Sopimusvastaava" alitaulukko
            ks.toggleLaajennaRivi('johto-ja-hallintokorvaus-laskulla-taulukko', 'Sopimusvastaava');

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('not.be.checked')
                .check();

            // Täytä tunnit/kk ensimmäisen kuukauden arvo Sopimusvastaavalle
            ks.muokkaaLaajennaRivinArvoa(
                'johto-ja-hallintokorvaus-laskulla-taulukko',
                0, 0, 1, '10')
            // Klikkaa "Kopioi allaoleviin" ->Kopioi saman arvon jokaiselle kuukaudelle
            ks.klikkaaTaytaAlas();

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')

            // Disabloi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen" (seuraavia testejä varten)
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0])
                .find('input')
                .should('be.checked')
                .uncheck();


            // Sulje "Sopimusvastaava" alitaulukko (seuraavia testejä varten)
            cy.log('Suljetaan "Sopimusvastaava" alitaulukko seuraavia testejä varten..');
            ks.toggleLaajennaRivi('johto-ja-hallintokorvaus-laskulla-taulukko', 'Sopimusvastaava');

            // Aseta tuntipalkka Sopimusvastaavalle
            //  NOTE: Rivin arvon muokkaaminen ei toimi, mikäli Sopimusvastaava-alitaulukko on auki! (Suljettu yllä)
            ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko',
                0, 2, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')


            // -- Arvojen tarkastus --

            // Tarkasta yhteensä/kk -arvo muokatulta riviltä (10 * 10 = 100€) 1. hoitovuosi
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .testaaRivienArvot([1], [0, 3], ['100'])

            // FIXME: johto-ja-hallintokorvaus-yhteenveto-taulukko arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            //        Vanhojen arvojen muokkaukset päivittyvät yhteenvetotaulukkoon normaalisti.
            // Tarkasta arvot taulukkoon liittyvästä yhteenvetotaulukosta (Tuntimäärät ja -palkat taulukon alapuolella)
            // cy.get('#johto-ja-hallintokorvaus-yhteenveto-taulukko')
            //     .testaaRivienArvot([2], [],
            //         ['Yhteensä', '',
            //             ks.formatoiArvoDesimaalinumeroksi(20)]);

            // FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            // Tarkasta Johto- ja hallintokorvau osion yhteenveto
            //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
            //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);
        })

        xit('Muokkaa Sopimusvastaava-toimenkuvan tunteja ja palkkoja tuleville hoitokausille', function () {
            // HUOM: Tässä testissä oletetaan, että tuntimäärät ja palkat alitaulukko on suljettu (Sitä ei varmisteta..)

            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä tunnit/kk arvo "Sopimusvastaava" riville 1. hoitovuodelle
            ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko', 0, 1, '10')

            // Aseta tuntipalkka Sopimusvastaavalle
            //  NOTE: Rivin arvon muokkaaminen ei toimi, mikäli Sopimusvastaava-alitaulukko on auki! (Suljettu yllä)
            ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko',
                0, 2, '20')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')

            // Tarkasta yhteensä/kk -arvo muokatulta riviltä (10 * 20 = 200€)
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .testaaRivienArvot([1], [0, 3], ['200'])


            // Valitse 3. hoitovuosi alasvetovalikosta
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('3.')
                .click();

            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla disabloitu defaulttina.
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked')
                .check();

            // Täytä arvo "Sopimusvastaava" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko', 0, 1, '10')
            // Aseta tuntipalkka Sopimusvastaavalle
            //  NOTE: Rivin arvon muokkaaminen ei toimi, mikäli Sopimusvastaava-alitaulukko on auki! (Suljettu yllä)
            ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko',
                0, 2, '5')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-johto-ja-hallintokorvaukset')


            // -- Arvojen tarkastus --

            // Tarkasta yhteensä/kk -arvo muokatulta riviltä (10 * 10 = 100€) 3. hoitovuosi
            cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                .testaaRivienArvot([1], [0, 3], ['50'])


            // FIXME: johto-ja-hallintokorvaus-yhteenveto-taulukko arvot eivät muutu kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            //        Vanhojen arvojen muokkaukset päivittyvät yhteenvetotaulukkoon normaalisti.
            // Tarkasta arvot taulukkoon liittyvästä yhteenvetotaulukosta (Tuntimäärät ja -palkat taulukon alapuolella)
            // cy.get('#johto-ja-hallintokorvaus-yhteenveto-taulukko')
            //     .testaaRivienArvot([2], [],
            //         ['Yhteensä', '',
            //             ks.formatoiArvoDesimaalinumeroksi(20)]);


            // FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            // Tarkasta Johto- ja hallintokorvau osion yhteenveto
            //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
            //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Palauta 1. hoitovuosi valituksi muita testejä varten
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('1.')
                .click();

            // Varmista, että "Kopioi hankinnat tuleville hoitovuosille" on disabled
            cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('be.checked').uncheck();
        });

        describe.skip('Testaa "Hankintavastaava (ennen urakkaa)"-toimenkuvaa', function () {
            it('Muokkaa "Hankintavastaava (ennen urakkaa)"-toimenkuvan tunteja ja palkkoja', function () {
                // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
                cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                    .find('.pudotusvalikko-filter')
                    .contains('1.')


                // Täytä tunnit/kk arvo "Hankintavastaava (ennen urakkaa)" riville 1. hoitovuodelle
                ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko', 7, [1], '10')

                // Aseta tuntipalkka "Hankintavastaava (ennen urakkaa)"
                ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko',
                    7, [2], '20')

                // Varmista, että tallennuskyselyt menevät läpi
                cy.wait('@tallenna-budjettitavoite')
                cy.wait('@tallenna-johto-ja-hallintokorvaukset')
                cy.wait('@tallenna-budjettitavoite')
                cy.wait('@tallenna-johto-ja-hallintokorvaukset')
                cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')


                cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                    .contains('Hankintavastaava (ennen urakkaa)')
                    .parent()
                    // Tarkasta, että riviltä löytyy oikea summa 10 * 20 = 200. (Hox: Tavoitehintaan tulee + 900 €, koska kuukausia on 4.5/v = 4.5 * 200 €)
                    .contains('200')
                // FIXME: En saanut millään testaaRivienArvot toimimaan tällä rivityypillä, enkä saanut apufunktiota korjattua
                //        niin, että se tukisi tätä uutta rivityyppiä. testaaRivienArvot vaatisi paremman ja joustavamman toteutuksen!
                //.testaaRivienArvot([1, 7], [3], ['200'], true)


                // Käy läpi muut hoitovuodet kuin 1. hoitovuosi.
                // ->Hankintavastaava (ennen urakkaa)-rivin ei pitäisi olla näkyvillä muilla hoitovuosilla kuin 1.
                cy.log('Varmistetaan, että "Hankintavastaava (ennen urakkaa)"-rivi ei näy muilla hoitovuosilla...');
                cy.wrap([2, 3, 4, 5]).each(hoitovuosi => {
                    cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                        .find('.pudotusvalikko-filter')
                        .click()
                        .contains(`${hoitovuosi}.`)
                        .click();

                    cy.wait(1000)

                    cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                        .contains('Hankintavastaava (ennen urakkaa)')
                        .parent()
                        .should('not.be.visible')
                });



                // -- Arvojen tarkastus --

                // FIXME: johto-ja-hallintokorvaus-yhteenveto-taulukko arvot eivät muutu kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
                //        Vanhojen arvojen muokkaukset päivittyvät yhteenvetotaulukkoon normaalisti.
                // Tarkasta arvot taulukkoon liittyvästä yhteenvetotaulukosta (Tuntimäärät ja -palkat taulukon alapuolella)
                // cy.get('#johto-ja-hallintokorvaus-yhteenveto-taulukko')
                //     .testaaRivienArvot([2], [],
                //         ['Yhteensä', '',
                //             ks.formatoiArvoDesimaalinumeroksi(20)]);


                // FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
                // Tarkasta Johto- ja hallintokorvau osion yhteenveto
                //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
                //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);


                // -- Palauta UI-valinnat ennalleen --
                cy.log('Palautetaan UI-valinnat ennalleen...');

                // Palauta 1. hoitovuosi valituksi muita testejä varten
                cy.get('div[data-cy="tuntimaarat-ja-palkat-taulukko-suodattimet"]')
                    .find('.pudotusvalikko-filter')
                    .click()
                    .contains('1.')
                    .click();
            });

        })

        describe.skip('Testaa uuden custom toimenkuvan lisäämistä tyhjälle riville', function () {
            it('Lisää "Testitoimenkuva" tuntipalkkoineen', function () {
                // Aseta custom nimi
                ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko',
                    10, 0, 'Testitoimenkuva', true, true)

                // Aseta tuntipalkka Sopimusvastaavalle
                ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko',
                    10, 1, '10', true, true)

                // Aseta tuntipalkka Sopimusvastaavalle
                ks.muokkaaRivinArvoa('johto-ja-hallintokorvaus-laskulla-taulukko',
                    10, 2, '10', true, true)


                // Varmista, että tallennuskyselyt menevät läpi
                cy.wait('@tallenna-budjettitavoite')
                cy.wait('@tallenna-johto-ja-hallintokorvaukset')
                cy.wait('@tallenna-budjettitavoite')
                cy.wait('@tallenna-johto-ja-hallintokorvaukset')


                // -- Arvojen tarkastus --

                // Tarkasta yhteensä/kk -arvo muokatulta riviltä
                cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                    // Arvotaulukon (1) Rivin 11 sarake 4 (jos taulukolla on kenttien alla yhteenvetorivi, niin se löytyy [2] polkuTaulukkoon avulla)
                    .testaaRivienArvot([1, 10], [3], ['100'])

                // FIXME: johto-ja-hallintokorvaus-yhteenveto-taulukko arvot eivät muutu kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
                //        Vanhojen arvojen muokkaukset päivittyvät yhteenvetotaulukkoon normaalisti.
                // Tarkasta arvot taulukkoon liittyvästä yhteenvetotaulukosta (Tuntimäärät ja -palkat taulukon alapuolella)
                // cy.get('#johto-ja-hallintokorvaus-yhteenveto-taulukko')
                //     .testaaRivienArvot([2], [],
                //         ['Yhteensä', '',
                //             ks.formatoiArvoDesimaalinumeroksi(20)]);


                // FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
                // Tarkasta Johto- ja hallintokorvau osion yhteenveto
                //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
                //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);
            });

            it('Muuta äsken lisätyn Testitoimenkuvan "kk/v" -arvoa', function () {
                // Valitse 11. rivin kk/v pudotusvalikosta arvoksi: "7"
                cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                    .taulukonOsaPolussa([1, 10, 0, 4], true)
                    .click()
                    .contains('7')
                    .click();

                // Modalin pitäisi aueta ja se pyytää vahvistamaan halutaanko aiemmin asetetut tiedot poistaa riviltä.
                // Painetaan "Poista tiedot"-nappulaa.
                cy.get('.modal')
                    .contains('button', 'Poista')
                    .click();

                // Varmista, että tallennuskyselyt menevät läpi
                cy.wait('@tallenna-johto-ja-hallintokorvaukset')


                // -- Arvojen tarkastus --

                // Tarkasta yhteensä/kk -arvo muokatulta riviltä (Yhteensä-arvon pitäisi olla edelleen sama kuin edellisessä testissä)
                cy.get('#johto-ja-hallintokorvaus-laskulla-taulukko')
                    // Arvotaulukon (1) Rivin 11 sarake 4 (jos taulukolla on kenttien alla yhteenvetorivi, niin se löytyy [2] polkuTaulukkoon avulla)
                    .testaaRivienArvot([1, 10], [3], ['100'])

                // FIXME: johto-ja-hallintokorvaus-yhteenveto-taulukko arvot eivät muutu kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
                //        Vanhojen arvojen muokkaukset päivittyvät yhteenvetotaulukkoon normaalisti.
                //        HOX: Uuden toimenkuvan lisääminen päivittää kyllä yhteenvetoja, mutta sitä on turha testata ennen edellämainitun ongelman korjaamista.
                // Tarkasta arvot taulukkoon liittyvästä yhteenvetotaulukosta (Tuntimäärät ja -palkat taulukon alapuolella)
                // cy.get('#johto-ja-hallintokorvaus-yhteenveto-taulukko')
                //     .testaaRivienArvot([2], [],
                //         ['Yhteensä', '',
                //             ks.formatoiArvoDesimaalinumeroksi(20)]);


                // FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
                //        HOX: Uuden toimenkuvan lisääminen päivittää kyllä yhteenvetoja, mutta sitä on turha testata ennen edellämainitun ongelman korjaamista.
                // Tarkasta Johto- ja hallintokorvau osion yhteenveto
                //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
                //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);
            });
        });
    });


    describe.skip('Testaa "Johto ja hallinto: Muut kulut" taulukkoa', function () {
        beforeEach(function () {
            cy.intercept('POST', '_/tallenna-budjettitavoite').as('tallenna-budjettitavoite');
            cy.intercept('POST', '_/tallenna-kustannusarvioitu-tyo').as('tallenna-kustannusarvioitu-tyo');
        });

        it('Taulukon arvot alussa oikein', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')

            // Varmista, että "Kopioi kuluvan hoitovuoden määrät tuleville vuosille" ei ole aktiivinen.
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('not.be.checked');

            cy.get('#toimistokulut-taulukko')
                .testaaOtsikot(['', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
                .testaaRivienArvot([1], [0, 0], ['Toimistokulut, Pientarvikevarasto'])
                .testaaRivienArvot([1], [0, 1], [''])
                .testaaRivienArvot([1], [0, 2], ['0,00'])
                .testaaRivienArvot([1], [0, 3], ['0,00'])
        });

        it('Muokkaa "Toimistokulut, pientarvikevarasto" arvoja jokaiselle kuukaudelle erikseen (Ilman kopiointia)', function () {
            // Avaa "Toimistokulut, pientarvikevarasto" alitaulukko
            ks.toggleLaajennaRivi('toimistokulut-taulukko', 'Toimistokulut');

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#toimistokulut-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('not.be.checked')
                .check();

            ks.muokkaaLaajennaRivinArvoa(
                'toimistokulut-taulukko',
                0, 0, 1, '10')
            ks.muokkaaLaajennaRivinArvoa(
                'toimistokulut-taulukko',
                0, 11, 1, '10', true)

            // Varmista, että tallennuskyselyt menevät läpi
            // FIXME: "Johto ja hallinto: Muut kulut" taulukko tuottaa jostain syystä tallenna-budjettitavoite kutsun duplikaattina!
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#toimistokulut-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(20),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 20, 1))]);

            /// FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            //         Kyseinen ongelma täytyy korjata ennen kuin voidaan testata osion yhteenvedon summia reaaliaikaisesti.
            // Tarkasta Johto- ja hallintokorvau osion yhteenveto
            //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
            //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Sulje "Toimistokulut, pientarvikevarasto" alitaulukko (seuraavia testejä varten)
            ks.toggleLaajennaRivi('toimistokulut-taulukko', 'Toimistokulut');
        })

        it('Muokkaa "Toimistokulut, pientarvikevarasto" arvoja jokaiselle kuukaudelle erikseen (Kopioinnin kanssa)', function () {
            // Avaa "Toimistokulut, pientarvikevarasto" alitaulukko
            ks.toggleLaajennaRivi('toimistokulut-taulukko', 'Toimistokulut');

            // Aktivoi "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen"
            cy.get('#toimistokulut-taulukko')
                .taulukonOsaPolussa([1, 0, 0, 1, 0], true)
                .find('input')
                .should('be.checked');

            // Täytä ensimmäisen kuukauden arvo
            ks.muokkaaLaajennaRivinArvoa(
                'toimistokulut-taulukko',
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
            cy.get('#toimistokulut-taulukko')
                .testaaRivienArvot([2], [],
                    ['Yhteensä', '',
                        ks.formatoiArvoDesimaalinumeroksi(120),
                        ks.formatoiArvoDesimaalinumeroksi(ks.indeksikorjaaArvo(indeksit, 120, 1))]);

            /// FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            //         Kyseinen ongelma täytyy korjata ennen kuin voidaan testata osion yhteenvedon summia reaaliaikaisesti.
            // Tarkasta Johto- ja hallintokorvau osion yhteenveto
            //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
            //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Sulje "Toimistokulut, pientarvikevarasto" alitaulukko (seuraavia testejä varten)
            ks.toggleLaajennaRivi('toimistokulut-taulukko', 'Toimistokulut');
        })

        it('Muokkaa "Toimistokulut, pientarvikevarasto" arvot tuleville hoitokausille', function () {
            // Varmista, että 1. hoitovuosi on valittuna alasvetovalikosta
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .contains('1.')


            // Täytä arvo "Toimistokulut, pientarvikevarasto" riville 1. hoitovuodelle
            ks.muokkaaRivinArvoa('toimistokulut-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // Valitse 3. hoitovuosi alasvetovalikosta
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('3.')
                .click();

            // Aktivoidaan "Kopioi hankinnat tuleville hoitovuosille". Ja huomaa, että kaikki
            // osiot muuttuvat samalla
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('be.checked');

            // Täytä arvo "Toimistokulut, pientarvikevarasto" riville 3. hoitovuodelle, joka kopioidaan myös seuraaville hoitovuosille.
            ks.muokkaaRivinArvoa('toimistokulut-taulukko', 0, 1, '10')

            // Varmista, että tallennuskyselyt menevät läpi
            cy.wait('@tallenna-budjettitavoite')
            cy.wait('@tallenna-kustannusarvioitu-tyo')


            // -- Arvojen tarkastus --

            // Tarkasta arvot taulukon yhteenvetorivillä
            cy.log('Tarkastetaan yhteenvetorivi...');
            cy.get('#toimistokulut-taulukko')
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

            /// FIXME: Johto- ja hallintokorvaus osion yhteenvedon arvot eivät muutu, kun lisätään ensimmäistä kertaa Tuntimäärät ja -palkat arvoja
            //         Kyseinen ongelma täytyy korjata ennen kuin voidaan testata osion yhteenvedon summia reaaliaikaisesti.
            // Tarkasta osion yhteenveto
            //ks.tarkastaHintalaskurinArvo('johto-ja-hallintokorvaus-hintalaskuri', 1, ?);
            //ks.tarkastaIndeksilaskurinArvo(indeksit, 'johto-ja-hallintokorvaus-indeksilaskuri', 1, ?);


            // -- Palauta UI-valinnat ennalleen --
            cy.log('Palautetaan UI-valinnat ennalleen...');

            // Palauta 1. hoitovuosi valituksi muita testejä varten
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('.pudotusvalikko-filter')
                .click()
                .contains('1.')
                .click();

            // Disabloi "Kopioi hankinnat tuleville hoitovuosille".
            cy.get('div[data-cy="johto-ja-hallinto-muut-kulut-taulukko-suodattimet"]')
                .find('input[id*="kopioi-tuleville-hoitovuosille"]')
                .should('be.checked')
                .uncheck();
        });
    })
});
