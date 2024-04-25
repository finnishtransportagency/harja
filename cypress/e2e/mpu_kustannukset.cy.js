// E2E   
// MPU Kustannukset 
//

let clickTimeout = 6000;
let loaderTimeout = 30000;


describe('MPU Kustannusnäkymä toimii', function () 
{
  it('Pitäisi löytää ja avata päällystysurakan Kustannukset', function () 
  {
    cy.viewport(1100, 2000);
    cy.server();
    cy.route('POST', '_/hae-mpu-kustannukset').as('kustannukset'); 
    cy.route('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('sanktiot'); 
    // Avaa päänäkymä
    cy.visit("/");

    // Avaa hallintayksikkö
    cy.contains('.haku-lista-item', 'Pohjois-Pohjanmaa').click(); 

    // Hyrrää ei pitäisi olla 
    cy.get('.ajax-loader', {timeout: loaderTimeout}).should('not.exist'); 

    // Valitaan urakkatyyppi
    cy.get('[data-cy=murupolku-urakkatyyppi]').valinnatValitse({valinta: 'Päällystys'}); 

    // Valitse oikea urakka 
    cy.contains('[data-cy=urakat-valitse-urakka] li', 'Muhoksen päällystysurakka', {timeout: clickTimeout}).click(); 

    // Avaa kustannukset
    cy.get('[data-cy=tabs-taso1-Kustannukset]').click(); 

    // Kutsu pitäisi triggeraa, odota että taulukko lataa ja sorttaa
    cy.wait('@kustannukset', {timeout: clickTimeout});
    cy.wait('@sanktiot', {timeout: clickTimeout});

    // Hyrrää ei pitäisi olla
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist'); 
    cy.wait(1000);

    // Klikkaa kalenterivuotta
    cy.get('.valittu.overflow-ellipsis').eq(0).click(); 
    
    // Valitse 2024 vuosi
    cy.contains('2024').click();
  });


  it('Pitäisi löytää oikeat testidata arvot taulukosta', function () 
  {
    // Kolmas rivi pitäisi olla "Arvomuutokset", kun taulukko on aakkosissa
    cy.get('.grid tr').eq(2).find('td').eq(0).contains('Arvonmuutokset');
    // Arvomuutoksien toinen sarake eli kustannus pitäisi olla 1337e
    cy.get('.grid tr').eq(2).find('td').eq(1).contains('1 337,00 €');

    // Indeksi
    cy.get('.grid tr').eq(5).find('td').eq(0).contains('Indeksi- ja kustannustason muutokset');
    cy.get('.grid tr').eq(5).find('td').eq(1).contains('80 085,00 €');

    // Testidata 
    cy.get('.grid tr').eq(7).find('td').eq(0).contains('Kalustokustannukset');
    cy.get('.grid tr').eq(7).find('td').eq(1).contains('75 000,00 €');

    // Yhteensä 
    cy.get('.grid .kustannukset-yhteenveto').contains('1 605 942,00 €');
  });


  it('Pitäisi lisätä uusi Arvomuutos ja tallentaa se onnistuneesti', function () 
  {
    // Klikkaa 'Lisää kustannus'
    cy.get('button.button-primary-default[type="button"]')
      .contains('span', 'Lisää kustannus')
      .click({ force: true });

    // Kustannuksen lomake aukesi
    cy.get('h2.header-yhteiset[data-cy="mpu-kustannus-lisays"]', {timeout: clickTimeout}).contains('Lisää kustannus');

    // Kustannuksen tyyppi -> Arvonmuutokset
    cy.get('.nappi-alasveto .valittu.overflow-ellipsis').eq(0).click({ force: true });
    cy.contains('Arvonmuutokset').click({ force: true });

    // Tallenna napin ei pitäisi olla vielä näkyvissä
    cy.get('[data-cy="tallena-mpu-kustannus"]').should('be.disabled');

    // Kustannus -> 88,060e
    cy.get('.form-group.maara-valinnat.required.sisaltaa-virheen', { timeout: clickTimeout })
      .find('input[type="text"]')
      .type('88060.0').blur();

    // Kaikki syötetty, tallenna napin pitäisi näkyä
    cy.get('[data-cy="tallena-mpu-kustannus"]', { timeout: clickTimeout })
      .should('be.visible')
      .should('not.be.disabled')

    cy.server();
    cy.route('POST', '_/hae-mpu-kustannukset').as('kustannukset'); 
    cy.route('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('sanktiot'); 

    // Tallenna 
    cy.get('[data-cy="tallena-mpu-kustannus"]').click();

    // Kutsu pitäisi triggeraa, odota että taulukko lataa ja sorttaa
    cy.wait('@kustannukset', {timeout: clickTimeout});
    cy.wait('@sanktiot', {timeout: clickTimeout});

    // Viesti onnistumisesta pitäisi näkyä
    cy.contains('Kustannus tallennettu onnistuneesti', { timeout: clickTimeout }).should('be.visible');

    // Lomakkeen pitäisi olla nyt kiinni 
    cy.get('body').find('h2.header-yhteiset[data-cy="mpu-kustannus-lisays"]').should('not.exist');
    cy.wait(1000);
  });


  it('Pitäisi löytää tallennettu arvo taulukosta', function () 
  {
    // Kolmas rivi pitäisi olla "Arvomuutokset", kun taulukko on aakkosissa
    cy.get('.grid tr').eq(2).find('td').eq(0).contains('Arvonmuutokset');
    // Arvomuutoksien toinen sarake eli kustannus pitäisi olla 1337e
    cy.get('.grid tr').eq(2).find('td').eq(1).contains('89 397,00 €');

    // Indeksi
    cy.get('.grid tr').eq(5).find('td').eq(0).contains('Indeksi- ja kustannustason muutokset');
    cy.get('.grid tr').eq(5).find('td').eq(1).contains('80 085,00 €');

    // Testidata 
    cy.get('.grid tr').eq(7).find('td').eq(0).contains('Kalustokustannukset');
    cy.get('.grid tr').eq(7).find('td').eq(1).contains('75 000,00 €');

    // Yhteensä 
    cy.get('.grid .kustannukset-yhteenveto').contains('1 694 002,00 €');
  });


  it('Pitäisi lisätä uusi oma selitteinen kustannus ja tallentaa se onnistuneesti', function () 
  {
    // Klikkaa 'Lisää kustannus'
    cy.get('button.button-primary-default[type="button"]')
      .contains('span', 'Lisää kustannus')
      .click({ force: true });

    // Kustannuksen lomake aukesi
    cy.get('h2.header-yhteiset[data-cy="mpu-kustannus-lisays"]', {timeout: clickTimeout}).contains('Lisää kustannus');

    // Kustannuksen tyyppi -> Muut kustannukset
    cy.get('.nappi-alasveto .valittu.overflow-ellipsis').eq(0).click({ force: true });
    cy.contains('Muut kustannukset').click({ force: true });

    // Selitteen pitäisi tulla näkyviin 
    cy.contains('Selite', {timeout: clickTimeout});

    // Selite -> Oma cypress selite
    cy.get('label[for="kustannus-selite"]').parent().find('input').type('Oma cypress selite').blur();

    // Tallenna napin ei pitäisi olla vielä näkyvissä
    cy.get('[data-cy="tallena-mpu-kustannus"]').should('be.disabled');

    // Kustannus -> 123456,00 e
    cy.get('label[for="kustannus"]').parent().find('input').type('123456').blur();

    // Kaikki syötetty, tallenna napin pitäisi näkyä
    cy.get('[data-cy="tallena-mpu-kustannus"]', { timeout: clickTimeout })
      .should('be.visible')
      .should('not.be.disabled');

    cy.server();
    cy.route('POST', '_/hae-mpu-kustannukset').as('kustannukset'); 
    cy.route('POST', '_/hae-urakan-sanktiot-ja-bonukset').as('sanktiot'); 

    // Tallenna 
    cy.get('[data-cy="tallena-mpu-kustannus"]').click();

    // Kutsu pitäisi triggeraa, odota että taulukko lataa ja sorttaa
    cy.wait('@kustannukset', {timeout: clickTimeout});
    cy.wait('@sanktiot', {timeout: clickTimeout});

    // Viesti onnistumisesta pitäisi näkyä
    cy.contains('Kustannus tallennettu onnistuneesti', { timeout: clickTimeout }).should('be.visible');

    // Lomakkeen pitäisi olla nyt kiinni 
    cy.get('body').find('h2.header-yhteiset[data-cy="mpu-kustannus-lisays"]').should('not.exist');
    cy.wait(1000);
  });


  it('Pitäisi löytää tallennettu arvo taulukosta', function () 
  {
    // Lisäämä oma selite pitäisi näkyä, ja yhteensä arvon muuttua
    cy.get('.grid tr').eq(15).find('td').eq(0).contains('Oma cypress selite');
    cy.get('.grid tr').eq(15).find('td').eq(1).contains('123 456,00 €');

    // Yhteensä 
    cy.get('.grid .kustannukset-yhteenveto').contains('1 817 458,00 €');
  });
}); 
