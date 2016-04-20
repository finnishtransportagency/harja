-- Aseta toteuman NULL päätösaika aloitusajaksi
UPDATE toteuma SET paattynyt=alkanut WHERE paattynyt IS NULL AND alkanut IS NOT NULL;
