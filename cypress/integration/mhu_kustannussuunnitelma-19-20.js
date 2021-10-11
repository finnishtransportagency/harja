import * as ks from '../support/kustannussuunnitelmaFns.js';
import {
    avaaKustannussuunnittelu, formatoiArvoEuromuotoiseksi,
    indeksikorjaaArvo,
    kattohintaElem,
    kattohintaInput, muokkaaRivinArvoa, tarkistaIndeksikorjattuKH, tarkistaKattohinta
} from "../support/kustannussuunnitelmaFns.js";

/**
 * Vuosina 2019 ja 2020 aloitettujen MH-Urakoiden kattohinnan suunnittelu
 * poikkeaa muista. Niiden kattohintaa ei lasketa normaalilla 1.1-kertoimella,
 * vaan ne lisätään käsin. Tämän takia omat testit näille.
 * Omassa suitessa, koska kustannussuunnitelman testisuite on paisunut pitkäksi.
 */

const indeksit = [];

function alustaKittilanUrakka() {
    ks.alustaKanta('Kittilän MHU 2019-2024');
}

function taytaArvoja() {
    cy.get('#suunnitellut-hankinnat-taulukko')
        .taulukonOsaPolussa([1, 0, 0, 0])
        .click();
    ks.muokkaaLaajennaRivinArvoa('suunnitellut-hankinnat-taulukko', 0, 0, 1, '1000')
    ks.klikkaaTaytaAlas();
}

describe('Testaa Kittilän MHU kustannussuunnitelmanäkymää', () => {
    before(() => {
            alustaKittilanUrakka();
            avaaKustannussuunnittelu('Kittilän MHU 2019-2024', 'Lappi', indeksit);
            taytaArvoja();
        }
    )

    it('Testaa kattohinnan taulukon alkuarvot', () => {
        ks.tarkistaKattohinta(1, '0,00');
        ks.tarkistaKattohinta(2, '0,00');
        ks.tarkistaKattohinta(3, '0,00');
        ks.tarkistaKattohinta(4, '0,00');
        ks.tarkistaKattohinta(5, '0,00');
    })

    it('Täytä kattohintataulukko', () => {
        ks.taytaKattohinta(1, "1111");
        ks.taytaKattohinta(2, "2222");
        ks.taytaKattohinta(3, "3333");
        ks.taytaKattohinta(4, "4444");
        ks.taytaKattohinta(5, "5555");

        ks.tarkistaKattohinta(1, '1111,00');
        ks.tarkistaKattohinta(2, '2222,00');
        ks.tarkistaKattohinta(3, '3333,00');
        ks.tarkistaKattohinta(4, '4444,00');
        ks.tarkistaKattohinta(5, '5555,00');

        ks.tarkistaIndeksikorjattuKH(1, 1111, indeksit);
        ks.tarkistaIndeksikorjattuKH(2, 2222, indeksit);
        ks.tarkistaIndeksikorjattuKH(3, 3333, indeksit);
        ks.tarkistaIndeksikorjattuKH(4);
        ks.tarkistaIndeksikorjattuKH(5);
    })

    it('Tarkista kattohintataulukon validointi', () => {
        ks.taytaKattohinta(1, "11999");

        // Cypressillä hoverin testaaminen on vähän kinkkistä. Tämä ei varmista, että virheilmoitus on näkyvissä,
        // mutta ainakin nähdään, että virhe on olemassa. Pelataan sen varaan, että gridin onhover ei mene itsestään rikki.
        cy.get(kattohintaElem(1)).should('have.class', "sisaltaa-virheen");
        cy.get(kattohintaElem(1)).should('have.text', " Kattohinnan täytyy olla suurempi kuin tavoitehinta " + formatoiArvoEuromuotoiseksi(12000));

        ks.taytaKattohinta(1, "12200");
        cy.get(kattohintaElem(1)).should('not.have.class', "sisaltaa-virheen");
    })

    it('Tavoitehinnan päivitys päivittää validoinnin', () => {
        ks.taytaKattohinta(3, "12200");
        cy.get(kattohintaElem(3)).should('not.have.class', "sisaltaa-virheen");

        // Nosta tavoitehintaaa hoidonjohtopalkkiolla
        cy.get('div[data-cy="hoidonjohtopalkkio-taulukko-suodattimet"]')
            .find('.pudotusvalikko-filter')
            .click()
            .contains('3.')
            .click();

        ks.muokkaaRivinArvoa('hoidonjohtopalkkio-taulukko', 0, 1, '100')

        cy.get(kattohintaElem(3)).should('have.class', "sisaltaa-virheen");
        cy.get(kattohintaElem(3)).should('have.text', " Kattohinnan täytyy olla suurempi kuin tavoitehinta " + formatoiArvoEuromuotoiseksi(13200));
    })

    it('Päivitä sivu ja tarkista arvot', () => {
        cy.reload();
        cy.get('.ajax-loader', {timeout: 40000}).should('not.exist');

        tarkistaKattohinta(1, "12200,00")
        tarkistaKattohinta(2, "2222,00")
        tarkistaKattohinta(3, "12200,00")
        tarkistaKattohinta(4, "4444,00")
        tarkistaKattohinta(5, "5555,00")

        tarkistaIndeksikorjattuKH(1, 12200, indeksit)
        tarkistaIndeksikorjattuKH(3, 12200, indeksit)
    })
})