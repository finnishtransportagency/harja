import * as f from '../support/taulukkoFns.js';

function testaaSuunnitelmienTila(polku, ensimmaisenVuodenTila, toisenVuodenTila) {
    function vuodenTilanLuokka (tila) {
        switch (tila) {
            case 'valmis':
                return 'glyphicon-ok';
            case 'kesken':
                return 'livicon-question';
            case 'aloittamatta':
                return 'glyphicon-remove';
        }
    }

    let ensimmaisenVuodenLuokka = vuodenTilanLuokka(ensimmaisenVuodenTila);
    let toisenVuodenLuokka = vuodenTilanLuokka(toisenVuodenTila);

    cy.get('#suunnitelmien-taulukko').then(($taulukko) => {
        let $rivi = $taulukko;
        for (let i = 0; i < polku.length; i++) {
            console.log('loop..');
            console.log($rivi);
            console.log(polku[i]);
            $rivi = f.taulukonRiviTekstillaSync($rivi, polku[i]);
        }
        cy.wrap(f.rivinSarakeSync($rivi, 1).find('span.' + ensimmaisenVuodenLuokka))
            .should('exist');
        cy.wrap(f.rivinSarakeSync($rivi, 2).find('span.' + toisenVuodenLuokka))
            .should('exist');
    })
}

describe('Testaa Inarin MHU urakan kustannussuunnitelmanäkymää', function () {
    it('Avaa näkymä', function () {
        cy.visit("/#urakat/yleiset?&hy=13&u=35");
        cy.get('[data-cy=tabs-taso1-Suunnittelu]', {timeout: 20000}).click();
        cy.get('img[src="images/ajax-loader.gif"]', {timeout: 20000}).should('not.exist');
    });
    it('Avaa suunnitelmien tila taulukon vetolaatikot', function () {
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'Talvihoito').click();
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'Liikenneympäristön hoito').click();
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'Sorateiden hoito').click();
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'Päällystepaikkaukset').click();
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'MHU Ylläpito').click();
        cy.contains('#suunnitelmien-taulukko .toimenpide-rivi', 'MHU Korvausinvestointi').click();
    });
});

describe('Testaa hankinnat taulukkoa', function () {
    it('Taulukon arvot alussa oikein', function () {
        testaaSuunnitelmienTila(['Talvihoito'], 'aloittamatta', 'aloittamatta');
        cy.get('#suunnittellut-hankinnat-taulukko')
            .testaaOtsikot(['Kiinteät', 'Määrä €/kk', 'Yhteensä', 'Indeksikorjattu'])
            .testaaSarakkeenArvot([1], [0, 0], ['1. hoitovuosi', '2. hoitovuosi', '3. hoitovuosi', '4. hoitovuosi', '5. hoitovuosi'])
            .testaaSarakkeenArvot([1], [0, 1], ['', '', '', '', ''])
            .testaaSarakkeenArvot([1], [0, 2], ['0,00', '0,00', '0,00', '0,00', '0,00'])
            .testaaSarakkeenArvot([1], [0, 3], ['0,00', '0,00', '0,00', '0,00', '0,00'])

    })
});