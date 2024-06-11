-- Poista duplikaatti rahavaraus rivit kannasta 
DELETE FROM rahavaraus
      WHERE nimi IN ('Äkilliset hoitotyöt', 'Vahinkojen korvaukset', 'Kannustinjärjestelmä');
