describe('POT-lomake', function () {
    it('Avaa vanha POT-lomake', function () {
        cy.visit("/")
        cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa ja Kainuu').click()
        cy.get('[data-cy=murupolku-urakkatyyppi] button').click()
        // Pudotusvalikoissa pitää tarkistaa ensin, että onhan ne vaihtoehdot näkyvillä. Tämä siksi, että valikon
        // painaminen, jolloin lista vaihtoehtoja tulee näkyviin re-renderaa listan. Tämä taasen aiheuttaa sen,
        // että Cypress saattaa keretä napata tuolla seuraavalla 'contains' käskyllä elementin, jonka React
        // poistaa DOM:ista.
        cy.contains('[data-cy=murupolku-urakkatyyppi] ul li a', 'Päällystys').should('be.visible').click()
        cy.contains('[data-cy=urakat-valitse-urakka] li', 'Muhoksen päällystysurakka').click()
        cy.get('[data-cy=tabs-taso1-Kohdeluettelo]').click()
        //cy.server()
        // cy.route('POST', '_/urakan-yllapitokohteet').as('haeUrakanYllapitokohteet')
        cy.get('[data-cy=tabs-taso2-Päällystysilmoitukset]').click()
        //cy.wait('@haeUrakanYllapitokohteet')
        // Piillotetaan kartta, jotta 2017 valinta on näkyvillä valinnassa
        //cy.get('[data-cy=piilota-kartta]').click()
        cy.get('[data-cy=valinnat-vuosi] button').click()
        cy.get('[data-cy=valinnat-vuosi] .dropdown-menu').should('have.css', 'display').and('match', /block/)
        cy.contains('[data-cy=valinnat-vuosi] ul li a', '2017').click({force: true})
        //cy.wait('@haeUrakanYllapitokohteet')
        cy.get('[data-cy=paallystysilmoitukset-grid] tr')
            .contains('Kuusamontien testi')
            .parentsUntil('tbody')
            .contains('button', 'Aloita päällystysilmoitus').click()
    })
    it('Oikeat aloitustiedot', function () {
        cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] th').then(($otsikot) => {
            let tienumeroIndex;
            let ajorataIndex;
            let kaistaIndex;
            for (let i = 0; i < $otsikot.length; i++) {
                let otsikonTeksti = $otsikot[i].textContent.trim()
                if (otsikonTeksti.localeCompare('Tienumero') === 0) {
                    tienumeroIndex = i;
                } else if (otsikonTeksti.localeCompare('Ajorata') === 0) {
                    ajorataIndex = i;
                } else if (otsikonTeksti.localeCompare('Kaista') === 0) {
                    kaistaIndex = i;
                }
            }
            return [tienumeroIndex, ajorataIndex, kaistaIndex]
        }).then((eiMuokattavatSarakkeet) => {
            cy.log("tienumeroIndex: " + eiMuokattavatSarakkeet)
            cy.get('[data-cy=yllapitokohdeosat-Tierekisteriosoitteet] tbody tr').then(($rivit) => {
                if (NodeList.prototype.isPrototypeOf($rivit)) {
                    for (const $rivi of $rivit) {
                        eiMuokattavatSarakkeet.forEach((i) => {
                            expect($rivi.children().get(i)).to.have.class('ei-muokattava');
                        });
                    }
                }  else {
                    eiMuokattavatSarakkeet.forEach((i) => {
                        expect($rivit.children().get(i)).to.have.class('ei-muokattava');
                    });
                }
            })
        })
    })
})