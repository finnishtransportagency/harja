describe('Luo uusi tietyöilmoitus', () => {
    const ilmoittaja = 'Testi Käyttäjä';
    const urakanNimi = 'Cypress test';
    const tienNumero = 22;
    const tienNimi = 'Testikatu';
    const aosa = 1;
    const aet = 8080;
    const tanaan = new Date();

    // Nopeusrajoitukset, tienpinnat ja kiertotien tienpinnat
    const taulukkoTiedot = [
        {
            tunniste: 'nopeusrajoitukset',
            rivit: [
                { lista: '40', arvo: '100' },
                { lista: '60', arvo: '200' },
                { lista: '80', arvo: '300' }
            ]
        },
        {
            tunniste: 'tienpinnat',
            rivit: [
                { lista: 'Päällystetty', arvo: '200' },
                { lista: 'Jyrsitty', arvo: '400' },
                { lista: 'Murske', arvo: '600' }
            ]
        },
        {
            tunniste: 'kiertotienpinnat',
            rivit: [
                { lista: 'Päällystetty', arvo: '400' },
                { lista: 'Jyrsitty', arvo: '800' },
                { lista: 'Murske', arvo: '1200' }
            ]
        }];

    beforeEach(() => {
        cy.terminaaliKomento().then((terminaaliKomento) => {
            const poistaTietyot = '"DELETE FROM tietyoilmoituksen_email_lahetys; DELETE FROM tietyoilmoitus"';
            cy.exec(terminaaliKomento + 'psql -h localhost -U harja harja -c ' + poistaTietyot)
        });

        cy.visit('http://localhost:3000/#ilmoitukset/tietyo?');

        // Luo tietyöilmoitus
        cy.get('.tietyoilmoitukset > button').click();
    });

    function valitseValikosta(painike, arvo) {
        painike.parent().within(() => {
            cy.get('button').click();
            cy.contains(arvo).click();
        });
    }

    function muotoilePvm(pvm, nollaa) {
        let muotoiltuPvm = pvm.getDate() + '.' + (pvm.getMonth() + 1) + '.' + pvm.getFullYear();

        if (nollaa === true) {
            muotoiltuPvm += ' 0:00';
        } else if (nollaa === false) {
            muotoiltuPvm += ' ' + pvm.getHours() + ':' + ('0' + pvm.getMinutes()).slice(-2);
        }

        return muotoiltuPvm;
    }

    function validoiKentta(tunniste, arvo, span) {
        cy.get('[for=' + tunniste + ']').parent()
            .find('div' + (span ? ' > span' : '')).should('have.text', arvo);
    }

    function validoiIlmoituksenPakollisetKentat() {
        function validoiIlmoitusRivi(indeksi, arvo, span) {
            cy.get('td:nth-child(' + indeksi + ') > span' + (span ? ' > span' : ''))
                .should('have.text', arvo);
        }

        cy.get('.ajax-loader', {timeout: 10000}).should('not.be.visible')
        cy.get('#tietyoilmoitushakutulokset').as('ilmoitusTaulukko');

        cy.get('@ilmoitusTaulukko').find('tr.klikattava').then(($rivit) => {
            expect($rivit.length).to.be.equal(1);

            cy.wrap($rivit).within(() => {
                validoiIlmoitusRivi(2, urakanNimi, true);
                validoiIlmoitusRivi(3, tienNumero + ' ' + tienNimi, true);
                validoiIlmoitusRivi(4, muotoilePvm(new Date(), false), false);
                validoiIlmoitusRivi(6, muotoilePvm(tanaan, true), false);

                // Avaa ilmoitus
                cy.get('td.vetolaatikon-tila.klikattava').click();
            });
        });

        cy.get('@ilmoitusTaulukko').find('.vetolaatikko').as('vetolaatikko').within(() => {
            validoiKentta('urakka_nimi', urakanNimi, true);
            validoiKentta('osoite', 'Tie ' + tienNumero + ' / ' + aosa + ' / ' + aet, false);
            validoiKentta('loppu', muotoilePvm(tanaan, true), true);
        });
    }

    function taytaPakollisetKentat() {
        function haeKentta(tunniste) {
            return cy.get('[for=' + tunniste + ']').parent().find('input');
        }

        // Projektin tai urakan nimi
        haeKentta('urakan-nimi').type(urakanNimi);

        // Koordinaatit
        cy.get('.tierekisteriosoite-kentta td > input').then(($sarakkeet) => {
            const koordinaatit = [tienNumero, aosa, aet];

            koordinaatit.forEach((arvo, indeksi) => {
                cy.wrap($sarakkeet[indeksi]).type(arvo).blur();
                cy.wait(240);
            });
        });

        // Tien nimi
        haeKentta('tien-nimi').type(tienNimi);

        // Työn aloituspäivämäärä
        cy.get('[for="alku"]').parent().within(() => {
            cy.get('input').click();
            cy.get('.pvm-valinta .pvm-paiva.klikattava:first').click();
        });

        // Työn lopetuspäivämäärä
        haeKentta('loppu').type(muotoilePvm(tanaan));

        // Vaikutussuunta
        valitseValikosta(cy.get('[for=vaikutussuunta]'), 'Haittaa molemmissa ajosuunnissa');

        // Matkapuhelin
        haeKentta('-matkapuhelin').last().type('0400123456');
    }

    it('Luo taulukoilla ja pakollisilla kentillä', () => {
        function getTallennaPainike() {
            return cy.get('.lomake-footer button:first');
        }

        function syotaTaulukkoKenttaan(kentta, arvo) {
            cy.wrap(kentta).find('input').clear().type(arvo);
        }

        function poistaEnsimmainenTaulukkoRivi(taulukko) {
            taulukko.find('.livicon-trash:first').click();
        }

        function haeUusiTaulukkoRivi(taulukko, indeksi, callback) {
            taulukko.within(() => {
                cy.get('.grid-lisaa').click();
                cy.get('tr.muokataan:nth-of-type(' + indeksi + ') > td').then(callback)
            });
        }

        function haeTaulukko(tunniste) {
            return cy.get('[for=' + tunniste + ']').parent();
        }

        function luoRiviTaulukkoille(tunniste, indeksi, listaValinta, matka) {
            haeUusiTaulukkoRivi(haeTaulukko(tunniste), indeksi, ($taulukkoRivi) => {
                valitseValikosta(cy.wrap($taulukkoRivi[0]), listaValinta);
                syotaTaulukkoKenttaan($taulukkoRivi[1], matka);
            });
        }

        function luoTyoaika(indeksi, valitutPaivat) {
            haeUusiTaulukkoRivi(haeTaulukko('tyoajat'), indeksi, ($tyoaikaRivi) => {
                valitutPaivat.forEach((paiva) => {
                    // Päivät
                    cy.wrap($tyoaikaRivi[0]).contains(paiva).find('input').check();
                });

                // Alkaa
                syotaTaulukkoKenttaan($tyoaikaRivi[1], 1200);

                // Päättyy
                syotaTaulukkoKenttaan($tyoaikaRivi[2], 1800);
            });
        }

        function validoiTyoaikaTaulukko(valittu) {
            // Päivät
            cy.get('td:first input').each(($kentta, indeksi) => {
                cy.wrap($kentta).should((valittu[indeksi] ? '' : 'not.') + 'be.checked');
            });

            // Alkaen
            cy.get('td:nth-of-type(2) > span > input').should('have.value', '12:00');

            // Päättyen
            cy.get('td:nth-of-type(3) > span > input').should('have.value', '18:00');
        }

        function validoiTaulukkoRivi(rivi) {
            // Valintalistan arvo
            cy.get('button > div').should('have.text', rivi.lista);

            // Syötetty arvo (metri)
            cy.get('input').should('have.value', rivi.arvo)
        }

        function palautaMetreina(arvo) {
            return '(' + arvo + ' metriä)'
        }

        getTallennaPainike().should('be.disabled');

        taytaPakollisetKentat();

        // Työaika
        luoTyoaika(1, ['MA', 'KE']);
        luoTyoaika(2, ['TI', 'PE']);
        poistaEnsimmainenTaulukkoRivi(haeTaulukko('tyoajat'));
        luoTyoaika(2, ['LA', 'SU']);

        haeTaulukko('tyoajat').find('table:first').within((() => {
            cy.get('tr.muokataan').as('tyoaikaRivit').should('have.length', 2);
            cy.get('@tyoaikaRivit').first().within(() => {
                // Checkboxit
                validoiTyoaikaTaulukko([false, true, false, false, true, false, false]);
            });

            cy.get('@tyoaikaRivit').last().within(() => {
                // Checkboxit
                validoiTyoaikaTaulukko([false, false, false, false, false, true, true]);
            });
        }));

        // Nopeusrajoitukset, tienpinnat ja kiertotien tienpinntar
        taulukkoTiedot.forEach((taulukko) => {
            const tunniste = taulukko.tunniste;
            const rivit = taulukko.rivit;

            luoRiviTaulukkoille(tunniste, 1, rivit[0].lista, rivit[0].arvo);
            luoRiviTaulukkoille(tunniste, 2, rivit[1].lista, rivit[1].arvo);
            poistaEnsimmainenTaulukkoRivi(haeTaulukko(tunniste));
            luoRiviTaulukkoille(tunniste, 2, rivit[2].lista, rivit[2].arvo);

            haeTaulukko(tunniste).find('tr.muokataan').as('rivit');
            cy.get('@rivit').first().within(() => {
                validoiTaulukkoRivi(rivit[1]);
            });

            cy.get('@rivit').last().within(() => {
                validoiTaulukkoRivi(rivit[2]);
            });
        });

        // Tallenna lomake
         getTallennaPainike().should('not.be.disabled').click();
         validoiIlmoituksenPakollisetKentat();

         // Validoi taulukoit
        cy.get('@vetolaatikko').within(() => {
            const nr = taulukkoTiedot[0];
            const tp = taulukkoTiedot[1];
            const kt = taulukkoTiedot[2];

            validoiKentta(
                nr.tunniste,
                nr.rivit[1].lista + 'km/h  ' + palautaMetreina(nr.rivit[1].arvo) + ', ' +
                nr.rivit[2].lista + 'km/h  ' + palautaMetreina(nr.rivit[2].arvo),
                false
            );

            validoiKentta(
                tp.tunniste,
                tp.rivit[1].lista.toLowerCase() + ' ' + palautaMetreina(tp.rivit[1].arvo) + ', ' +
                tp.rivit[2].lista.toLowerCase() + ' ' + palautaMetreina(tp.rivit[2].arvo),
                false
            );

            validoiKentta(
                'kiertotie',
                kt.rivit[1].lista.toLowerCase() + ' ' + palautaMetreina(kt.rivit[1].arvo) + ', ' +
                kt.rivit[2].lista.toLowerCase() + ' ' + palautaMetreina(kt.rivit[2].arvo),
                false
            );
        });
    });

    it('Luo pakollisilla kentillä ja lähetä lomake sähköpostiin', () => {
        function getLahetaPainike() {
            return cy.get('.lomake-footer button:last');
        }

        function getModalPainikkeet() {
            return cy.get('.modal-footer button');
        }

        getLahetaPainike().should('be.disabled');
        taytaPakollisetKentat();

        // Tallenna ja lähetä lomake
        getLahetaPainike().should('not.be.disabled').click();

        // Modalin painikkeet
        getModalPainikkeet().then(($painikkeet) => {
            cy.wrap($painikkeet[0]).should('not.be.disabled');
            cy.wrap($painikkeet[1]).should('be.disabled');
        });

        // Vastaanottaja
        valitseValikosta(cy.get('[for=vastaanottaja]'), 'Oulu');

        // Lähetä lomake
        getModalPainikkeet().last().should('not.be.disabled').click();

        // TODO: tilapäinen korjaus, emailit eivät käy ilmoituksen tiedoissa ilman uudelleenlatausta
        cy.wait(6400);
        cy.reload();
        //cy.contains('Palaa ilmoitusluetteloon').click();

        validoiIlmoituksenPakollisetKentat();

        //Validoi sähköpostitaulukon
        cy.get('@vetolaatikko').find('[for=email-lahetykset-tloikiin]')
            .parent()
            .find('td')
            .then(($sarakkeet) => {
                /*
                cy.wrap($sarakkeet[0])
                    .find('span')
                    .should(
                        'have.text',
                        muotoilePvm(tanaan, false)
                    );
                */

                cy.wrap($sarakkeet[1])
                    .find('span > span')
                    .should('have.text', ilmoittaja);
            });
    });
});