-- Aseta 2025 vuoden urakoiden tehtävä kuljetusten avustaminen määrämitattavaksi
UPDATE tehtava
SET "maaramitattava?"      = true,
    muokattu               = current_timestamp,
    muokkaaja              = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Ennalta arvaamattomien kuljetusten avustaminen (materiaali)';