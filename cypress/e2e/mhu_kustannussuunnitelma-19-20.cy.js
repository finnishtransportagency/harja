import * as ks from '../support/kustannussuunnitelmaFns.js';
import {
    avaaKustannussuunnittelu, formatoiArvoEuromuotoiseksi,
    indeksikorjaaArvo,
    kattohintaElem,
    kattohintaInput, muokkaaRivinArvoa, tarkistaIndeksikorjattuKH, tarkistaKattohinta, valitseHoitokausi
} from "../support/kustannussuunnitelmaFns.js";
import {kuluvaHoitokausiAlkuvuosi} from "../support/apurit.js"

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
    let maxHKVuosi = kuluvaHoitokausiAlkuvuosi();
    if(maxHKVuosi > 2023) maxHKVuosi = 2023
    let kuluvaHoitokausiNro = maxHKVuosi - 2019 + 1;

    let hoitokausiNyt = kuluvaHoitokausiNro + ". hoitovuosi (" + maxHKVuosi + "—" + (maxHKVuosi + 1) + ")";
    let valittavaHoitokausi = "3. hoitovuosi (2021—2022)";
    ks.valitseHoitokausi(hoitokausiNyt, valittavaHoitokausi);
    cy.get('#suunnitellut-hankinnat-taulukko')
        .taulukonOsaPolussa([1, 0, 0, 0])
        .click();

    // Testit olettavat, että "kopioi tuleville vuosille" on hankinnoissa päällä
    // Disabloidaan "Kopioi hankinnat tuleville hoitovuosille". Checkboxin tulisi olla defaulttina aktiivinen.
    cy.get('input[id="kopioi-hankinnat-tuleville-hoitovuosille"]')
        .should('not.be.checked')
        .check();

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
        ks.tarkistaKattohinta(1, '0,00', true);
        ks.tarkistaKattohinta(2, '0,00', true);
        ks.tarkistaKattohinta(3, '0,00', false);
        ks.tarkistaKattohinta(4, '0,00', true);
        ks.tarkistaKattohinta(5, '0,00', true);
    })

    it('Täytä kattohintataulukko', () => {
        ks.taytaKattohinta(3, "12200,00");

        ks.tarkistaKattohinta(3, '12200,00', false);

        ks.tarkistaIndeksikorjattuKH(3, 12200, indeksit);
    })

    it('Tarkista kattohintataulukon validointi', () => {
        ks.taytaKattohinta(3, "11999");

        // Cypressillä hoverin testaaminen on vähän kinkkistä. Tämä ei varmista, että virheilmoitus on näkyvissä,
        // mutta ainakin nähdään, että virhe on olemassa. Pelataan sen varaan, että gridin onhover ei mene itsestään rikki.
        cy.get(kattohintaElem(3)).should('have.class', "sisaltaa-virheen");
        cy.get(kattohintaElem(3)).should('have.text', " Kattohinnan täytyy olla suurempi kuin tavoitehinta " + formatoiArvoEuromuotoiseksi(12000));

        ks.taytaKattohinta(3, "12200");
        cy.get(kattohintaElem(3)).should('not.have.class', "sisaltaa-virheen");
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

    it('Vaihda vuosi ja täytä kattohinta', () => {
        let hoitokausiNyt = "3. hoitovuosi (2021—2022)";
        let valittavaHoitokausi = "4. hoitovuosi (2022—2023)";
        ks.valitseHoitokausi(hoitokausiNyt, valittavaHoitokausi);

        cy.get(kattohintaElem(3)).should('not.have', 'input');
        ks.taytaKattohinta(4, "14000");
        cy.get(kattohintaElem(4)).should('not.have.class', "sisaltaa-virheen")
            .then((elem => {
                elem.blur();
            }));
    })


    it('Päivitä sivu ja tarkista arvot', () => {
        cy.reload();
        cy.get('.ajax-loader', {timeout: 40000}).should('not.exist');


        let maxHKVuosi = kuluvaHoitokausiAlkuvuosi();
        if(maxHKVuosi > 2023) maxHKVuosi = 2023
        let kuluvaHoitokausiNro = maxHKVuosi - 2019 + 1;
        let hoitokausiNyt = kuluvaHoitokausiNro + ". hoitovuosi (" + maxHKVuosi + "—" + (maxHKVuosi + 1) + ")";
        // Valitaan 3. hoitovuosi.
        let valittavaHoitokausi = "3. hoitovuosi (2021—2022)";
        ks.valitseHoitokausi(hoitokausiNyt, valittavaHoitokausi);

        tarkistaKattohinta(1, "0,00", true)
        tarkistaKattohinta(2, "0,00", true)
        tarkistaKattohinta(3, "12200,00", false)
        tarkistaKattohinta(4, "14000,00", true)
        tarkistaKattohinta(5, "0,00", true)

        tarkistaIndeksikorjattuKH(3, 12200, indeksit)
        tarkistaIndeksikorjattuKH(4, 14000, indeksit)
    })

})
