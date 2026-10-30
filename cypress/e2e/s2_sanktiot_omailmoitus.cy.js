import {
    avaaSanktiotJaBonuksetNakyma,
    evkLappi,
    testiurakkaMhu26
} from '../support/sanktiotJaBonuksetFns';

const omailmoituksenTeksti = 'Kyseessä on urakoitsijan itse ilmoittama laadunalitus tai tehtävän laiminlyönti, joka alentaa sanktiota 50 %';

describe('Sanktion omailmoitus MHU26-urakassa', function () {
    [1100, 1440].forEach((leveys) => {
        it(`näyttää omailmoituksen omalla rivillään ja puolittaa summan, kun näkymän leveys on ${leveys} px`, function () {
            cy.viewport(leveys, 1200);
            avaaSanktiotJaBonuksetNakyma(testiurakkaMhu26, evkLappi);
            cy.contains('button', 'Lisää uusi').click();
            cy.contains('label', 'Sanktio').click();
            cy.contains('label', omailmoituksenTeksti).should('not.exist');
            cy.contains('button', 'Muistutus').click();
            cy.contains('a', 'B - Vakava laiminlyönti').click();
            cy.contains('button', 'Valitse sanktiotyyppi').click();
            cy.contains('a', 'Talvihoito Ise/Is/L').click();
            cy.contains('Urakoitsijan omailmoitus').should('not.exist');

            cy.contains('label', omailmoituksenTeksti).should('be.visible').then(($label) => {
                cy.contains('label', 'Perustelu').then(($perustelu) => {
                    cy.contains('label', 'Sanktion suuruus').then(($summa) => {
                        const teksti = $label[0].getBoundingClientRect();
                        const perustelu = $perustelu[0].ownerDocument.getElementById($perustelu.attr('for')).getBoundingClientRect();
                        const summa = $summa[0].getBoundingClientRect();

                        expect(teksti.width, 'omailmoituksen leveys').to.be.at.least(perustelu.width * 0.8);
                        expect(teksti.top, 'omailmoitus perustelun jälkeen').to.be.at.least(perustelu.bottom);
                        expect(teksti.bottom, 'omailmoitus ennen sanktion suuruutta').to.be.at.most(summa.top);
                    });
                });
            });

            cy.contains('label', 'Sanktion suuruus').closest('.form-group')
                .find('.lomake-arvo').invoke('text').should('match', /14\s?000/);
            cy.contains('label', omailmoituksenTeksti).click();
            cy.contains('label', 'Sanktion suuruus').closest('.form-group')
                .find('.lomake-arvo').invoke('text').should('match', /7\s?000/);
            cy.contains('label', omailmoituksenTeksti).click();
            cy.contains('label', 'Sanktion suuruus').closest('.form-group')
                .find('.lomake-arvo').invoke('text').should('match', /14\s?000/);
        });
    });
});
