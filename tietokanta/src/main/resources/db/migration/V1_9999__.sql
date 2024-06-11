-- Poista duplikaatti rahavaraus rivit kannasta 
-- Näille olemassa jo rivit : Rahavaraus B, C ja K
DELETE FROM rahavaraus
      WHERE nimi IN ('Äkilliset hoitotyöt', 'Vahinkojen korvaukset', 'Kannustinjärjestelmä');
