-- Tehtävä: Muut tavoitehintaan vaikuttavat rahavaraukset on virheellisesti siirtynyt tehtäväryhmän: Tilaajan rahavaraus (T3) alle.
-- Siirretään se siis oikean tehtävärymän eli Muut, MHU Ylläpito (F) alle.
-- Käytetään yksilöiviä tunnisteita varmistaaksemme, että käsitellään oikeaa tehtäväryhmää ja tehtävää. ID voi vaihdella eri ympäristöissä.
UPDATE tehtava
SET tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '4e3cf237-fdf5-4f58-b2ec-319787127b3e')
WHERE yksiloiva_tunniste = '548033b7-151d-4202-a2d8-451fba284d92';
