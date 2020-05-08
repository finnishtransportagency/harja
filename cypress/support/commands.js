// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add("login", (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add("drag", { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add("dismiss", { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This is will overwrite an existing command --
// Cypress.Commands.overwrite("visit", (originalFn, url, options) => { ... })

// komentoja taulukkokomponenttia varten
import * as f from '../support/taulukkoFns.js';

Cypress.Commands.add("taulukonRiviTekstilla", { prevSubject: 'element'}, ($taulukko, teksti) => {
    return f.taulukonRiviTekstillaSync($taulukko, teksti);
});
Cypress.Commands.add("rivinSarake", { prevSubject: 'element'}, ($rivi, index) => {
    return f.rivinSarakeSync($rivi, index);
});
Cypress.Commands.add("taulukonOsa", { prevSubject: 'element'}, ($taulukko, index) => {
    return f.taulukonOsaSync($taulukko, index);
});
Cypress.Commands.add("taulukonOsaPolussa", { prevSubject: 'element'}, ($taulukko, polku) => {
    let $sisaOsa = $taulukko;
    polku.forEach((polunOsa) => {
        $sisaOsa = f.taulukonOsaSync($sisaOsa, polunOsa);
    });
    return $sisaOsa;
});

Cypress.Commands.add("testaaOtsikot", { prevSubject: 'element'}, ($taulukko, otsikoidenArvot) => {
    cy.wrap($taulukko).find('[data-cy=otsikko-rivi]').then(($otsikkoRivi) => {
        for (let i = 0; i < otsikoidenArvot.length - 1; i++) {
            if (otsikoidenArvot[i] === '') {
                cy.wrap($otsikkoRivi).rivinSarake(i).then(($solu) => {
                    expect($solu
                        .find(() => {
                            return true;
                        })
                        .addBack()
                        .contents()
                        .filter(function (index, element) {
                            return (element.nodeType === 3 && element.wholeText !== '');
                        })
                        .length)
                        .to.equal(0);
                });
            } else {
                cy.wrap($otsikkoRivi).rivinSarake(i).contains(otsikoidenArvot[i]).should('exist');
            }
        }
    }).then(() => {
        return $taulukko;
    });
});

Cypress.Commands.add("testaaRivienArvot", { prevSubject: 'element'}, ($taulukko, polkuTaulukkoon, polkuSarakkeeseen, arvot) => {
    cy.wrap($taulukko.clone()).then(($taulukko) => {
        let $sisaTaulukko = $taulukko;
        polkuTaulukkoon.forEach((polunOsa) => {
            $sisaTaulukko = f.taulukonOsaSync($sisaTaulukko, polunOsa);
        });
        let $sarakkeenSolut = f.taulukonOsatSync($sisaTaulukko);
        polkuSarakkeeseen.forEach((polunOsa) => {
            $sarakkeenSolut = $sarakkeenSolut.map((i, element) => {
                return f.taulukonOsaSync(Cypress.$(element), polunOsa).get(0);
            });
        });
        for (let i = 0; i < arvot.length; i++) {
            expect($sarakkeenSolut.eq(i).filter(':contains(' + arvot[i] + ')').length, 'Oletettiin löytyvän arvo: ' + arvot[i] + ' indeksiltä: ' + i).to.equal(1);
        }
    }).then(() => {
        return $taulukko;
    });
});

// Komentoja gridkomponenttia varten
Cypress.Commands.add("gridOtsikot", { prevSubject: 'element'}, (grid) => {
    let $otsikkoRivit = grid.find('th')
    let otsikotIndekseineen = new Map();
    for (let i = 0; i < $otsikkoRivit.length; i++) {
        let otsikonTeksti = $otsikkoRivit.eq(i).text().replace(/[\u00AD]+/g, '')
        otsikotIndekseineen.set(otsikonTeksti, i)
    }
    return {
        grid: grid,
        otsikot: otsikotIndekseineen
    }
});

Cypress.Commands.add("terminaaliKomento", () => {
    cy.exec('/usr/local/bin/docker ps', {failOnNonZeroExit: false}).then((tulos) => {
        cy.exec('/usr/local/bin/docker ps | grep harjadb', {failOnNonZeroExit: false}).then((harjadbTulos) => {
            let terminaaliKomento;
            if (tulos.code === 0) {
                if (harjadbTulos.stdout !== '') {
                    terminaaliKomento = '/usr/local/bin/docker exec harjadb ';
                } else {
                    throw new Error('harjadb ei ole päällä')
                }
            } else {
                terminaaliKomento = '';
            }
            return terminaaliKomento
        })
    })
});

Cypress.Commands.add("valinnatValitse", { prevSubject: 'element'}, ($valinnat, parametrit) => {
    cy.wrap($valinnat).find('button').click();
    // Pudotusvalikoissa pitää tarkistaa ensin, että onhan ne vaihtoehdot näkyvillä. Tämä siksi, että valikon
    // painaminen, jolloin lista vaihtoehtoja tulee näkyviin re-renderaa listan. Tämä taasen aiheuttaa sen,
    // että Cypress saattaa keretä napata tuolla seuraavalla 'contains' käskyllä elementin, jonka React
    // poistaa DOM:ista.
    cy.wrap($valinnat).should('have.class', 'open');
    cy.wrap($valinnat).contains('ul li a', parametrit.valinta).should('be.visible').click({force: true});
});

Cypress.Commands.add("pvmValitse", {prevSubject: 'element'}, ($pvm, parametrit) => {
    cy.wrap($pvm).clear();
    cy.wrap($pvm).focus();
    cy.wrap($pvm.parent()).find('table').should('exist');
    cy.wrap($pvm).type(parametrit.pvm).then(($pvmUudestaan) => {
        // Joskus Cypress ei vain kirjoita koko tekstiä kenttään
        if ($pvmUudestaan.val() !== parametrit.pvm) {
            cy.wrap($pvmUudestaan).clear().type(parametrit.pvm)
        }
    });
    cy.wrap(Cypress.$('#app')).click('topRight', {force: true});
    cy.wrap($pvm.parent()).find('table').should('not.exist').then(($table) => {
        return $pvm
    })
});

Cypress.Commands.add("pvmTyhjenna", {prevSubject: 'element'}, ($pvm) => {
    cy.wrap($pvm).clear();
    cy.wrap(Cypress.$('#app')).click('topRight', {force: true});
    cy.wrap($pvm.parent()).find('table').should('not.exist').then(($table) => {
        return $pvm
    })
});

Cypress.Commands.add("POTTestienAlustus", (kohde, alikohde) => {
    let vapaaYHANumero;
    cy.terminaaliKomento().then((terminaaliKomento) => {
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c "SELECT yha_kohdenumero FROM yllapitokohde;"').then((harjadbTulos) => {
            let yhaKohdenumerot = harjadbTulos.stdout.split('\n').map((tulos) => {
                let numero;
                try{
                    numero=tulos.trim().parseInt();
                } catch(error) {
                    numero=null;
                }
                return numero;
            }).filter((tulos) => {
                if (tulos !== null) {
                    return tulos
                }
            });
            let vapaatNumerot = [];
            for(let i=0; i<100; i++)
                if(!yhaKohdenumerot.includes(i))
                    vapaatNumerot.push(i);
            vapaaYHANumero = vapaatNumerot[Math.floor(Math.random() * vapaatNumerot.length)];
        })
    });
    cy.terminaaliKomento().then((terminaaliKomento) => {
        let lisaaPallystyskohdeKomento = '"INSERT INTO yllapitokohde ' +
            '(yllapitoluokka, urakka, sopimus, yha_kohdenumero, kohdenumero, nimi, yllapitokohdetyyppi, yllapitokohdetyotyyppi, yhaid,' +
            ' tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista,' +
            ' suorittava_tiemerkintaurakka, vuodet, keskimaarainen_vuorokausiliikenne, poistettu)' +
            'VALUES' +
            '  (' + kohde.yllapitoluokka + ', (SELECT id' +
            '       FROM urakka' +
            '       WHERE nimi = \'Muhoksen päällystysurakka\'),' +
            '      (SELECT id' +
            '       FROM sopimus' +
            '       WHERE urakka = (SELECT id' +
            '                       FROM urakka' +
            '                       WHERE nimi = \'Muhoksen päällystysurakka\') AND paasopimus IS NULL),' +
            '      ' + vapaaYHANumero + ', \'L' + vapaaYHANumero + '\', \'E2E-Testi\', \'paallyste\' :: yllapitokohdetyyppi,' +
            '      \'paallystys\' ::yllapitokohdetyotyyppi, 3233231,' +
            '      22, 1, 65, 3, 100, ' + kohde.ajorata + ', ' + kohde.kaista + ', (SELECT id' +
            '                             FROM urakka' +
            '                             WHERE nimi =' +
            '                                   \'Oulun tiemerkinnän palvelusopimus 2013-2022\'),' +
            '   \'{' + kohde.vuosi + '}\', ' + kohde.kvl + ', FALSE);"';
        let lisaaPaallystyskohteenAlikohdeKomento = '"INSERT INTO yllapitokohdeosa ' +
            '(id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, sijainti)' +
            ' VALUES (3231, (SELECT id' +
            '              FROM yllapitokohde' +
            '              WHERE nimi = \'E2E-Testi\'), \'E2E-Testi-kohdeosa\', 22, 1, 65, 3, 100, ' + alikohde.ajorata + ', ' + alikohde.kaista + ', ' +
            '         (SELECT tierekisteriosoitteelle_viiva_ajr AS geom' +
            '          FROM tierekisteriosoitteelle_viiva_ajr(22, 1, 0, 3, 100, ' + alikohde.ajorata + '))' +
            ');"';
        let lisaaPaallystyskohteenAikataulu = '"INSERT INTO yllapitokohteen_aikataulu (yllapitokohde, kohde_alku, paallystys_alku, paallystys_loppu, valmis_tiemerkintaan,' +
            '                                       tiemerkinta_alku, tiemerkinta_loppu, kohde_valmis)' +
            '    VALUES ((SELECT id FROM yllapitokohde WHERE nimi=\'E2E-Testi\'), \'1.1.2017\'::DATE, \'1.5.2017\'::DATE, \'1.7.2017\'::DATE,' +
            '            \'1.8.2017\'::TIMESTAMP, \'1.9.2017\'::DATE, \'1.10.2017\'::DATE, \'1.12.2017\'::DATE);"';
        let poistaPaallystysilmoitus = '"DELETE ' +
            'FROM paallystysilmoitus ' +
            'WHERE paallystyskohde = (SELECT id FROM yllapitokohde WHERE nimi = \'E2E-Testi\');"';
        let poistaKohteenOsaKomento = '"DELETE FROM yllapitokohdeosa' +
            ' WHERE yllapitokohde=(SELECT id FROM yllapitokohde WHERE nimi=\'E2E-Testi\');"';
        let poistaKohdeKomento = '"DELETE FROM yllapitokohde WHERE nimi=\'E2E-Testi\';"';
        let poistaKohteenAikataulu = '"DELETE FROM yllapitokohteen_aikataulu' +
            ' WHERE yllapitokohde=(SELECT id FROM yllapitokohde WHERE nimi=\'E2E-Testi\');"';
        cy.log("TERMINAALIKOMENTO: " + terminaaliKomento)
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + poistaKohteenAikataulu)
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + poistaPaallystysilmoitus)
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + poistaKohteenOsaKomento)
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + poistaKohdeKomento)
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + lisaaPallystyskohdeKomento)
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + lisaaPaallystyskohteenAlikohdeKomento)
        cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + lisaaPaallystyskohteenAikataulu)
    })
});