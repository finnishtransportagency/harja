describe('Tehtäväluettelon järjestäminen', function () {
    it('Mene Rovaniemen MHU tehtäväluetteloon', function () {
        cy.visit('/#urakat/suunnittelu/tehtavat?&hy=13&u=32');
    })
    it('Klikkaile taulukko auki', function () {
        cy.get('[data-cy=taulukko] .jana').not('.piillotettu').should('have.length', 2);
        cy.get('[data-cy=rivin-id-1-laajenna]').click();
        cy.get('[data-cy=rivin-id-2-laajenna]').click();
        cy.get('[data-cy=taulukko] .jana').not('.piillotettu').should('have.length', 7);
    })
    it('Jarjestys ennen', function() {
        cy.contains('[data-cy=rivin-id-3-tehtava]', 'Teksti 2');
        cy.contains('[data-cy=rivin-id-4-tehtava]', 'Teksti 1');
        cy.get('[data-cy=rivin-id-3-maara]').should('have.value', '30');
        cy.get('[data-cy=rivin-id-4-maara]').should('have.value', '100');

        cy.get('[data-cy=taulukko] .jana').not('.piillotettu').then(($janat) => {
            expect($janat.eq(0)).to.have.attr('data-cy', 'tehtavataulukon-otsikko');
            expect($janat.eq(1)).to.have.attr('data-cy', 'rivin-id-1');
            expect($janat.eq(2)).to.have.attr('data-cy', 'rivin-id-2');
            expect($janat.eq(3)).to.have.attr('data-cy', 'rivin-id-3');
            expect($janat.eq(4)).to.have.attr('data-cy', 'rivin-id-4');
            expect($janat.eq(5)).to.have.attr('data-cy', 'rivin-id-5');
            expect($janat.eq(6)).to.have.attr('data-cy', 'rivin-id-7');
        })
    })
    it('Jarjesta tekstin mukaan', function () {
        cy.get('[data-cy=\'tehtava otsikko\'] .klikattava').click();

        cy.get('[data-cy=taulukko] .jana').not('.piillotettu').then(($janat) => {
            expect($janat.eq(0)).to.have.attr('data-cy', 'tehtavataulukon-otsikko');
            expect($janat.eq(1)).to.have.attr('data-cy', 'rivin-id-1');
            expect($janat.eq(2)).to.have.attr('data-cy', 'rivin-id-2');
            expect($janat.eq(3)).to.have.attr('data-cy', 'rivin-id-4');
            expect($janat.eq(4)).to.have.attr('data-cy', 'rivin-id-3');
            expect($janat.eq(5)).to.have.attr('data-cy', 'rivin-id-5');
            expect($janat.eq(6)).to.have.attr('data-cy', 'rivin-id-7');
        })
    })
    it('Jarjesta määrän mukaan', function () {
        cy.get('[data-cy=\'maara otsikko\'] .klikattava').click();

        cy.get('[data-cy=taulukko] .jana').not('.piillotettu').then(($janat) => {
            expect($janat.eq(0)).to.have.attr('data-cy', 'tehtavataulukon-otsikko');
        expect($janat.eq(1)).to.have.attr('data-cy', 'rivin-id-1');
        expect($janat.eq(2)).to.have.attr('data-cy', 'rivin-id-2');
        expect($janat.eq(3)).to.have.attr('data-cy', 'rivin-id-3');
        expect($janat.eq(4)).to.have.attr('data-cy', 'rivin-id-4');
        expect($janat.eq(5)).to.have.attr('data-cy', 'rivin-id-5');
        expect($janat.eq(6)).to.have.attr('data-cy', 'rivin-id-7');
    })
    })
})