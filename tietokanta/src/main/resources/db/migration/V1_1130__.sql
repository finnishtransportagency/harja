-- Erotetaan T-Loikista tulevan ilmoituksen tallennus Harjaan siitä, onnistuuko 'välitetty'
-- kuittaus Harjasta T-Loikiin. Lisätään siis uusi tila ilmoituksille 'ei-valitetty'
-- Tilat tämän jälkeen ovat:
-- "ei-valitetty" = ilmoitus on tallennettu Harjaan, mutta T-Loikiin ei onnistuttu lähettämään tästä Ack-viestiä, aka "valitetty" kuittausta
-- Jos kaikki menee odotetusti, niin uudet ilmoitukset eivät käytännössä koskaan jää tähän 'ei-valitetty' tilaan. Jos jäävät, T-Loik lähettää ne uudelleen ja tällöin myös niiden Ack-viesti uudelleenlähetetään.
-- "kuittaamaton" = urakassa ei ole vielä kuitattu ilmoitusta
-- "vastaanotettu" - urakassa on tehty vastaanottokuittaus
-- "aloitettu" - urakassa on tehty aloituskuittaus
-- "lopetettu" - urakassa on tehty lopetuskuittaus
ALTER TYPE ilmoituksen_tila ADD VALUE 'ei-valitetty';
