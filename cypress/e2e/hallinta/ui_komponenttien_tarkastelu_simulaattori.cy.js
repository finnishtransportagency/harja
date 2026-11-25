const verkkovirheet = [];

function valitseEsimerkkisivu(nimi) {
  painaJaTarkistaValinta('[data-cy=ui-simulaattori-esimerkki]', nimi, true);
  cy.get('[data-cy=ui-simulaattori-esikatselu]')
    .should('be.visible')
    .find('[role=alert]')
    .should('not.exist');
}

function tarkistaAnkkurit(ankkurit) {
  ankkurit.forEach((ankkuri) => {
    cy.get(`[data-cy=${ankkuri}]`)
      .first()
      .scrollIntoView()
      .should('be.visible');
  });
}

function painaJaTarkistaValinta(valinta, teksti, vaylaTyyli = false) {
  if (vaylaTyyli) {
    cy.get(`${valinta}.select-default, ${valinta} .select-default`).should('be.visible');
  }
  cy.get(valinta).within(() => {
    cy.get('button').click();
    cy.contains('a', teksti).click();
    cy.get('button').should('contain', teksti);
    if (vaylaTyyli) {
      cy.get('button').click();
      cy.get('.listan-arvo-valittu').should('be.visible');
      cy.get('button').click();
    }
  });
}

describe('UI-komponenttien sivusimulaattorin renderöijät', () => {
  beforeEach(() => {
    verkkovirheet.length = 0;
    cy.intercept('**/*', (request) => {
      request.on('response', (response) => {
        if (response.statusCode >= 400) {
          verkkovirheet.push(`${response.statusCode} ${request.url}`);
        }
      });
    });
    cy.visit('/#/hallinta/hallinta-tyokalut/ui-komponenttien-tarkastelu', {
      onBeforeLoad(win) {
        cy.stub(win.console, 'error').as('konsolivirhe');
      },
    });
    cy.get('[data-cy=ui-komponenttien-tarkastelu-sivu]').should('be.visible');
    cy.get('[data-cy=ui-komponenttien-tarkastelu-simulaattori-valinta]').click();
  });

  it('renderöi kaikki esimerkkisivut ja säilyttää komponenttien vuorovaikutuksen', () => {
    valitseEsimerkkisivu('Listaussivu: suodattimet ja taulukko');
    tarkistaAnkkurit([
      'ui-simulaattori-pino',
      'ui-simulaattori-suodatinrivi',
      'ui-simulaattori-hoitokausi',
      'ui-simulaattori-livi-pudotusvalikko',
      'ui-simulaattori-nappi',
      'ui-simulaattori-panel',
      'ui-simulaattori-perusgrid',
    ]);
    cy.get('[data-cy=ui-simulaattori-nappi]').click();
    cy.get('[data-cy=ui-simulaattori-tila]')
      .should('contain', 'Muutokset ovat päivittyneet esikatseluun');
    painaJaTarkistaValinta('[data-cy=ui-simulaattori-livi-pudotusvalikko]', 'Raportit', true);
    cy.get('[data-cy=ui-simulaattori-hoitokausi]').within(() => {
      cy.get('button').click();
      cy.get('li').first().click();
    });
    cy.get('[data-cy=ui-simulaattori-perusgrid]')
      .find('th')
      .contains('Nimi')
      .click();

    valitseEsimerkkisivu('Lomake: valinta ja tallennus');
    tarkistaAnkkurit([
      'ui-simulaattori-lomake',
      'ui-simulaattori-lomake-tyyppi',
      'ui-simulaattori-nappi',
    ]);
    painaJaTarkistaValinta('[data-cy=ui-simulaattori-lomake-tyyppi]', 'Päällyste', true);
    cy.get('[data-cy=ui-simulaattori-nappi]').click();

    valitseEsimerkkisivu('Napit ja kentät');
    tarkistaAnkkurit([
      'ui-simulaattori-pino',
      'ui-simulaattori-toimintoryhma',
      'nappi-ensisijainen',
      'ui-simulaattori-linkki',
      'ui-simulaattori-ikoni-ja-teksti',
      'ui-simulaattori-lomake',
      'ui-simulaattori-tekstikentta',
      'ui-simulaattori-valintaruutu',
      'ui-simulaattori-nappi-valinta-tyyppi',
      'ui-simulaattori-nappi-valinta-toinen',
      'ui-simulaattori-info-laatikko',
    ]);
    cy.get('[data-cy=nappi-ensisijainen]').click();
    cy.get('[data-cy=ui-simulaattori-linkki]').contains('a', 'Linkki').click();
    cy.get('[data-cy=ui-simulaattori-linkki]').should('contain', 'Klikkauksia: 1');
    cy.get('[data-cy=ui-simulaattori-tekstikentta]').find('input').type('Testi');
    cy.get('[data-cy=ui-simulaattori-valintaruutu]').find('input').check();
    painaJaTarkistaValinta('[data-cy=ui-simulaattori-nappi-valinta-tyyppi]', 'Päällyste', true);
    painaJaTarkistaValinta('[data-cy=ui-simulaattori-nappi-valinta-toinen]', 'B', true);

    valitseEsimerkkisivu('Renderöijien kattavuus');
    tarkistaAnkkurit([
      'ui-simulaattori-lomake',
      'ui-simulaattori-kenttaryhma',
      'ui-simulaattori-suodatin',
      'ui-simulaattori-numerokentta',
      'ui-simulaattori-sahkopostikentta',
      'ui-simulaattori-puhelinkentta',
      'ui-simulaattori-raksiboksi',
      'ui-simulaattori-radiokentta',
      'ui-simulaattori-checkbox-ryhma',
      'ui-simulaattori-radio-group',
      'renderoijien-kattavuus-valinta',
      'ui-simulaattori-checkbox-pudotusvalikko',
      'ui-simulaattori-ajax-loader',
      'ui-simulaattori-vihje',
      'ui-simulaattori-tooltip',
      'ui-simulaattori-tila-indikaattori',
      'ui-simulaattori-tietoja',
      'ui-simulaattori-kaksi-palstaa-otsikkoja-ja-arvoja',
      'ui-simulaattori-taulukkotietonakyma',
      'ui-simulaattori-muokkausgrid',
      'ui-simulaattori-tabs',
      'ui-simulaattori-dropdown-panel',
    ]);
    cy.get('[data-cy=ui-simulaattori-kenttaryhma]').find('input').type('Harja');
    cy.get('[data-cy=ui-simulaattori-suodatin]').find('input').type('urakka');
    cy.get('[data-cy=ui-simulaattori-numerokentta]').find('input').type('12.5');
    cy.get('[data-cy=ui-simulaattori-sahkopostikentta]').find('input').type('user-1@example.invalid');
    cy.get('[data-cy=ui-simulaattori-puhelinkentta]').find('input').type('0401234567');
    cy.get('[data-cy=ui-simulaattori-raksiboksi]').find('input').check();
    cy.get('[data-cy=ui-simulaattori-radiokentta]').find('input').last().check();
    cy.get('[data-cy=ui-simulaattori-checkbox-ryhma]').find('input').last().check();
    cy.get('[data-cy=ui-simulaattori-radio-group]').contains('label', 'Raportti').click();
    painaJaTarkistaValinta('[data-cy=renderoijien-kattavuus-valinta]', 'Toinen');
    cy.get('[data-cy=ui-simulaattori-checkbox-pudotusvalikko]').within(() => {
      cy.get('button').click();
      cy.contains('label', 'Urakka B').click();
      cy.get('input').last().should('be.checked');
    });
    cy.get('[data-cy=ui-simulaattori-tooltip-kohde]').trigger('mouseover');
    cy.get('[data-cy=ui-simulaattori-muokkausgrid]')
      .find('input')
      .first()
      .should('have.value', 'Sorastus')
      .clear()
      .type('Hiekoitus');
    cy.get('[data-cy=ui-simulaattori-tabs]')
      .find('[role=tab]')
      .contains('Historia')
      .click();
    cy.get('[data-cy=ui-simulaattori-tabs]').should('contain', 'Historian sisältö');
    cy.get('[data-cy=ui-simulaattori-dropdown-panel]').find('h3').click();

    valitseEsimerkkisivu('Tilaviestit ja toiminnot');
    tarkistaAnkkurit([
      'ui-simulaattori-pino',
      'ui-simulaattori-tilaviesti',
      'ui-simulaattori-toimintoryhma',
      'ui-simulaattori-nappi',
    ]);
    cy.get('[data-cy=ui-simulaattori-nappi]').click();
    cy.get('[data-cy=ui-simulaattori-tila]')
      .should('contain', 'Muutokset ovat päivittyneet esikatseluun');

    cy.get('[data-cy=ui-simulaattori-syote]')
      .clear()
      .type('[{:komponentti :tabs :asetukset {:valilehdet []}}]', {
        parseSpecialCharSequences: false,
      });
    cy.get('[data-cy=ui-simulaattori-tabs]').should('contain', 'Ei välilehtiä.');
    cy.get('[data-cy=ui-simulaattori-esikatselu]').find('[role=alert]').should('not.exist');

    cy.get('@konsolivirhe').should('not.have.been.called');
    cy.then(() => {
      expect(verkkovirheet, 'odottamattomat HTTP-virheet').to.deep.equal([]);
    });
  });
});
