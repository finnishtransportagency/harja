// E2E   
// Reikäpaikkaukset 
//

let clickTimeout = 6000;
let loaderTimeout = 30000;


describe('Reikäpaikkausnäkymä toimii', function () 
{
  it('Pitäisi avata reikäpaikkaukset, asettaa aikavälin ja löytää toteumat', function () 
  {
    cy.viewport(1100, 2000);
    cy.server();
    cy.route('POST', '_/hae-reikapaikkaukset').as('reikapaikkaukset'); 

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

    // Avaa reikäpaikkausnäkymä
    cy.get('[data-cy=tabs-taso1-Reikapaikkaukset]').click(); 

    // Kutsu pitäisi triggeraa
    cy.wait('@reikapaikkaukset', {timeout: clickTimeout}) 

    // Hyrrää ei pitäisi olla
    cy.get('.ajax-loader', {timeout: clickTimeout}).should('not.exist'); 

    // Aseta alkupvm suodatin
    cy.get('[data-cy="reikapaikkaus-aikavali"] input', {timeout: 50000}).eq(0).clear().type('25.02.2024').blur(); 

    // Aseta loppupvmsuodatin
    cy.get('[data-cy="reikapaikkaus-aikavali"] input', {timeout: 50000}).eq(1).clear().type('31.03.2024').blur();

    // Paina Hae 
    cy.get('[data-cy="hae-reikapaikkauskohteita"]').click();

    // Pitäisi tulla 5 tulosta, (+ otsikot, eli 6) 
    cy.get('table.grid').find('tr').should('have.length', 6, { timeout: 10000 });

    // Tällainen tr osoite pitäsi olla ensimmäisessä toteumassa
    cy.get('.grid tr').eq(1).find('td').contains('Tie 20 / 1 / 1 / 1 / 120');
  });


  it('Pitäisi avata toteuman muokkauksen sekä muokata tr-osoitetta', function () 
  {
    cy.get('.grid tr').eq(1).click();   // Klikkaa gridin ensimäistä toteumaa  
    cy.get('h2.header-yhteiset[data-cy="reikapaikkaus-muokkauspaneeli"]', {timeout: clickTimeout}).contains('Muokkaa toteumaa'); // Muokkauspaneeli aukesi

    // Tarkistetaan inputtien arvot, odotettu ensimmäinen TR osoite on Tie 20 / 1 / 1 / 1 / 120
    cy.get('.form-control.lomake-tr-valinta').eq(0).should('have.value', '20');
    cy.get('.form-control.lomake-tr-valinta').eq(1).should('have.value', '1');
    cy.get('.form-control.lomake-tr-valinta').eq(2).should('have.value', '1');
    cy.get('.form-control.lomake-tr-valinta').eq(3).should('have.value', '1');
    cy.get('.form-control.lomake-tr-valinta').eq(4).should('have.value', '120');

    // Muokkaa hieman alku ja loppuetäisyyksiä
    cy.get('.form-control.lomake-tr-valinta').eq(0).clear().type('20');
    cy.get('.form-control.lomake-tr-valinta').eq(1).clear().type('1');
    cy.get('.form-control.lomake-tr-valinta').eq(2).clear().type('1');
    cy.get('.form-control.lomake-tr-valinta').eq(3).clear().type('1');
    cy.get('.form-control.lomake-tr-valinta').eq(4).clear().type('121');

    // Tallenna toteuma 
    cy.get('[data-cy="tallena-reikapaikkaus"]').click();
    // Tallennuksen pitäisi onnistua 
    cy.contains('Toteuma tallennettu onnistuneesti', { timeout: clickTimeout }).should('be.visible');

    // Muokkauspaneeli menee tallennuksessa kiinni
    cy.get('.grid tr').eq(1).click();   // Klikkaa gridin ensimäistä toteumaa  
    cy.get('h2.header-yhteiset[data-cy="reikapaikkaus-muokkauspaneeli"]', {timeout: clickTimeout}).contains('Muokkaa toteumaa'); // Muokkauspaneeli aukesi

    // Muokkauspaneelin inputit pitäisi olla nyt vastaavat 
    cy.get('.form-control.lomake-tr-valinta').eq(0).should('have.value', '20');
    cy.get('.form-control.lomake-tr-valinta').eq(1).should('have.value', '1');
    cy.get('.form-control.lomake-tr-valinta').eq(2).should('have.value', '1');
    cy.get('.form-control.lomake-tr-valinta').eq(3).should('have.value', '1');
    cy.get('.form-control.lomake-tr-valinta').eq(4).should('have.value', '121');

    // Samoin taulukon ensimmäinen sarake pitäisi muuttua 
    cy.get('.form-control.lomake-tr-valinta').eq(0).should('have.value', '20');
    cy.get('.form-control.lomake-tr-valinta').eq(1).should('have.value', '1');
    cy.get('.form-control.lomake-tr-valinta').eq(2).should('have.value', '1');
    cy.get('.form-control.lomake-tr-valinta').eq(3).should('have.value', '1');
    cy.get('.form-control.lomake-tr-valinta').eq(4).should('have.value', '121');

    // Aseta arvot takaisin (tämä helpottaa testaajan elämää)
    cy.get('.form-control.lomake-tr-valinta').eq(0).clear().type('20');
    cy.get('.form-control.lomake-tr-valinta').eq(1).clear().type('1');
    cy.get('.form-control.lomake-tr-valinta').eq(2).clear().type('1');
    cy.get('.form-control.lomake-tr-valinta').eq(3).clear().type('1');
    cy.get('.form-control.lomake-tr-valinta').eq(4).clear().type('120');

    // Tallenna toteuma 
    cy.get('[data-cy="tallena-reikapaikkaus"]').click();
    // Tallennuksen pitäisi onnistua 
    cy.contains('Toteuma tallennettu onnistuneesti', { timeout: clickTimeout }).should('be.visible');

    // Muokkauspaneeli menee tallennuksessa kiinni
    cy.get('.grid tr').eq(1).click();   // Klikkaa gridin ensimäistä toteumaa  
    cy.get('h2.header-yhteiset[data-cy="reikapaikkaus-muokkauspaneeli"]', {timeout: clickTimeout}).contains('Muokkaa toteumaa'); // Muokkauspaneeli aukesi
  });

  
  it('Pitäisi testata muokkauspaneelin lomakkeen validoinnin', function () {
    const fields = [
      { message: 'Syötä tienumero', value: '20' },
      { message: 'Syötä alkuosa', value: '1' },
      { message: 'Syötä alkuetäisyys', value: '1' },
      { message: 'Syötä loppuosa', value: '1' },
      { message: 'Syötä loppuetäisyys', value: '120' }
    ];

    fields.forEach((field, index) => {
      // Tyhjennä kenttä, ja katso että validointiviesti tulee näkyviin, sekä Tallenna- nappi on pois käytöstä
      cy.get('.form-control.lomake-tr-valinta').eq(index).clear();
      cy.get('[data-cy="tallena-reikapaikkaus"]').should('be.disabled');
      cy.contains(field.message, { timeout: clickTimeout }).should('be.visible');
  
      // Aseta arvo takaisin, katso että validointiviesti häviää, ja Tallenna- nappi on esillä 
      cy.get('.form-control.lomake-tr-valinta').eq(index).clear().type(field.value);
      cy.contains(field.message, { timeout: clickTimeout }).should('not.exist');
      cy.get('[data-cy="tallena-reikapaikkaus"]').should('not.be.disabled');
    });

    
    // Tyhjennä määrä 
    cy.get('.input-default.komponentin-input').eq(0).clear();
    cy.get('[data-cy="tallena-reikapaikkaus"]').should('be.disabled');
    cy.contains('Syötä määrä', { timeout: clickTimeout }).should('be.visible');

    // Tyhjennä kustannus 
    cy.get('.input-default.komponentin-input.veda-oikealle').eq(0).clear();
    cy.get('[data-cy="tallena-reikapaikkaus"]').should('be.disabled');
    cy.contains('Syötä kustannusarvo', { timeout: clickTimeout }).should('be.visible');

    // Syötä uudet arvot valmiiksi
    cy.get('.input-error-default.komponentin-input').eq(0).clear().type('82').blur(); // Määrä 
    cy.get('.valittu.overflow-ellipsis').eq(0).click(); // Menetelmä
    cy.contains('AB-paikkaus levittäjällä').click(); // Menetemä -> AB-paikkaus levittäjällä
    cy.get('.valittu.overflow-ellipsis').eq(1).click(); // Yksikkö 
    cy.contains('jm').click(); // Menetemä -> AB-paikkaus levittäjällä
    cy.get('.input-error-default.komponentin-input.veda-oikealle').eq(0).clear().type('216000.0').blur(); // Kustannus 

    // Pvm 
    cy.get('[data-cy="reikapaikkaus-muokkaa-pvm"] input', {timeout: 50000}).eq(0).clear().type('06.03.2024').blur();
    
    // Tallenna nappi on näkyvissä
    cy.get('[data-cy="tallena-reikapaikkaus"]').should('not.be.disabled');

    // ________________________________
    // Tarkista arvot ennen tallennusta 
    // Ensimmäisen toteuman neljäs sarake (Määrä) on tällä hetkellä 81
    cy.get('.grid tr').eq(1).find('td').eq(3).contains('66 kpl');

    // Kustannus on 215 000
    cy.get('.grid tr').eq(1).find('td').eq(4).contains('1 500');

    // Menetelmä 
    cy.get('.grid tr').eq(1).find('td').eq(2).contains('Jyrsintäkorjaukset (HJYR/TJYR)');
    
    // Pvm 
    cy.get('.grid tr').eq(1).find('td').eq(0).contains('05.03.2024');

    // ________________________
    // Tallenna muokatut tiedot 
    cy.get('[data-cy="tallena-reikapaikkaus"]').click();

    // Tallennuksen pitäisi onnistua 
    cy.contains('Toteuma tallennettu onnistuneesti', { timeout: clickTimeout }).should('be.visible');

    // Ensimmäisen toteuman Määrä muttui
    cy.get('.grid tr').eq(1).find('td').eq(3).contains('82 jm');
    // Ensimmäisen toteuman Kustannus muuttui
    cy.get('.grid tr').eq(1).find('td').eq(4).contains('216 000');
    // Ensimmäisen toteuman Menetelmä muuttui
    cy.get('.grid tr').eq(1).find('td').eq(2).contains('AB-paikkaus levittäjällä');
    // Pvm muuttui
    cy.get('.grid tr').eq(1).find('td').eq(0).contains('06.03.2024');

    // Muokkauspaneeli menee tallennuksessa kiinni
    cy.get('.grid tr').eq(1).click();   // Klikkaa gridin ensimäistä toteumaa  
    cy.get('h2.header-yhteiset[data-cy="reikapaikkaus-muokkauspaneeli"]', {timeout: clickTimeout}).contains('Muokkaa toteumaa'); // Muokkauspaneeli aukesi

    // Asetetaan vielä arvot takaisin 
    cy.get('.input-default.komponentin-input').eq(0).clear().type('66').blur(); // Määrä 

    // Tarkistetaan vielä alasvedon validointi
    cy.get('.valittu.overflow-ellipsis').eq(0).click(); // Menetelmä
    cy.contains('- Valitse -').click();
    cy.contains('Valitse menetelmä', { timeout: clickTimeout }).should('be.visible');
    cy.get('.valittu.overflow-ellipsis').eq(0).click(); // Menetelmä
    cy.contains('Jyrsintäkorjaukset (HJYR/TJYR)').click(); // Menetemä -> Jyrsintäkorjaukset (HJYR/TJYR)

    cy.get('.valittu.overflow-ellipsis').eq(1).click(); // Yksikkö 
    cy.contains('kpl').click(); // Yksikkö -> kpl
    cy.get('.input-default.komponentin-input.veda-oikealle').eq(0).clear().type('1500.0').blur(); // Kustannus
    cy.get('[data-cy="reikapaikkaus-muokkaa-pvm"] input', {timeout: 50000}).eq(0).clear().type('05.03.2024').blur(); // Pvm

    // Tallenna muokatut tiedot 
    cy.get('[data-cy="tallena-reikapaikkaus"]').click();
    cy.contains('Toteuma tallennettu onnistuneesti', { timeout: clickTimeout }).should('be.visible');

    // Ensimmäisen toteuman Määrä muttui
    cy.get('.grid tr').eq(1).find('td').eq(3).contains('66 kpl');
    // Ensimmäisen toteuman Kustannus muuttui
    cy.get('.grid tr').eq(1).find('td').eq(4).contains('1 500');
    // Ensimmäisen toteuman Menetelmä muuttui
    cy.get('.grid tr').eq(1).find('td').eq(2).contains('Jyrsintäkorjaukset (HJYR/TJYR)');
    // Pvm muuttui
    cy.get('.grid tr').eq(1).find('td').eq(0).contains('05.03.2024');
  });

  
  it('Pitäisi poistaa toteuma onnistuneesti', function () 
  {
    cy.get('.grid tr').eq(1).click();   // Klikkaa gridin ensimäistä toteumaa  
    cy.get('h2.header-yhteiset[data-cy="reikapaikkaus-muokkauspaneeli"]', {timeout: clickTimeout}).contains('Muokkaa toteumaa'); // Muokkauspaneeli aukesi
    
    cy.get('[data-cy="poista-reikapaikkaus"]').click();
    cy.contains('Toteuma poistettu onnistuneesti', { timeout: clickTimeout }).should('be.visible');
    // Pitäisi tulla 4 tulosta, (+ otsikot, eli 5) 
    cy.get('table.grid').find('tr').should('have.length', 5, { timeout: 10000 });
    // Odotettu taulukon ensimmäisen toteuman TR osoite poiston jälkeen
    cy.get('.grid tr').eq(1).find('td').contains('Tie 20 / 1 / 140 / 1 / 360');

    cy.get('.grid tr').eq(1).click();   // Klikkaa gridin ensimäistä toteumaa  
    cy.get('h2.header-yhteiset[data-cy="reikapaikkaus-muokkauspaneeli"]', {timeout: clickTimeout}).contains('Muokkaa toteumaa'); // Muokkauspaneeli aukesi
    cy.get('[data-cy="sulje-muokkauspaneeli"]').click();
    cy.get('[data-cy="reikapaikkaus-muokkauspaneeli"]').should('not.exist'); // Muokkauspaneeli meni kiinni
  });


  it('Pitäisi suodattaa tuloksia TR-osoitteella', function () 
  {
    // Suodata tulokset 
    cy.get('.tierekisteriosoite-flex input', {timeout: 50000}).eq(0).clear().type('20').blur();
    cy.get('.tierekisteriosoite-flex input', {timeout: 50000}).eq(1).clear().type('1').blur();
    cy.get('.tierekisteriosoite-flex input', {timeout: 50000}).eq(2).clear().type('0').blur();
    cy.get('.tierekisteriosoite-flex input', {timeout: 50000}).eq(3).clear().type('1').blur();
    cy.get('.tierekisteriosoite-flex input', {timeout: 50000}).eq(4).clear().type('405').blur();

    // Paina Hae 
    cy.get('[data-cy="hae-reikapaikkauskohteita"]').click();

    // Pitäisi 1 tulos, (+ otsikot, eli 2) 
    cy.get('table.grid').find('tr').should('have.length', 2, { timeout: 10000 });
  });
});
