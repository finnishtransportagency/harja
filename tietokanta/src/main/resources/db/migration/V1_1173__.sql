-- Lisätään urakka -taulun käyttämään sopimustyyppi tietueeseen tiedot mhu ja mhu+ hoitourakoiden erottelemiseksi.
ALTER TYPE sopimustyyppi ADD VALUE 'mhu';
ALTER TYPE sopimustyyppi ADD VALUE 'mhu+';
