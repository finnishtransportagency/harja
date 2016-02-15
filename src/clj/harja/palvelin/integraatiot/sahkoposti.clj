(ns harja.palvelin.integraatiot.sahkoposti
  "Määrittelee sähköpostin lähetyksen yleisen rajapinnan"
  (:require [postal.core :as postal]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]))

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

(defrecord VainLahetys [palvelin vastausosoite]
  Sahkoposti
  (rekisteroi-kuuntelija! [_ _]
    (log/info "Vain lähetys sähköposti ei tue kuuntelijan rekisteröintiä!"))
  (laheta-viesti! [_ lahettaja vastaanottaja otsikko sisalto]
    (postal/send-message {:host palvelin}
                         {:from lahettaja
                          :to vastaanottaja
                          :subject otsikko
                          :body [{:type "text/html; charset=UTF-8"
                                  :content sisalto}]}))
  (vastausosoite [_] vastausosoite)
  
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn luo-vain-lahetys
  "Luo sähköpostirajapinnan, joka tukee vain lähetystä."
  [palvelin vastausosoite]
  (->VainLahetys palvelin vastausosoite))
