(ns harja.palvelin.integraatiot.sahkoposti
  "Määrittelee sähköpostin lähetyksen yleisen rajapinnan")

(defprotocol Sahkoposti
  (rekisteroi-kuuntelija!
   [this kuuntelija-fn]
   "Rekisteröi funktion, joka vastaanottaa sähköpostiviestit.")
  (laheta-viesti!
   [this lahettaja vastaanottaja otsikko sisalto]
   "Lähettää viestin vastaanottajalle annetulla otsikolla ja sisällöllä")
  (vastausosoite
   [this]
   "Palauttaa oletus vastausosoitteen, jota voi käyttää lähettäjänä ja johon lähetetyt viestit
    tulevat takaisin tälle kuuntelijalle"))
