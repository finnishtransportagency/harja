(ns harja.palvelin.integraatiot.sahkoposti
  "Määrittelee sähköpostin lähetyksen yleisen rajapinnan"
  (:require [postal.core :as postal]
            [taoensso.timbre :as log]
            [clojure.string :as s]
            [harja.palvelin.tapahtuma-protokollat :refer [Kuuntele]]
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
    tulevat takaisin tälle kuuntelijalle")
  (laheta-viesti-ja-liite!
    [this lahettaja vastaanottajat otsikko sisalto tiedosto-nimi]
    "Lähettää viestin vastaanottajalle annetulla otsikolla ja sisällöllä. Sisällön pitäisi sisältää myös liite"))

(defn sanitoi-otsikko [otsikko]
  ;; Javan regexpien \p -luokat vastaavat Unicoden tai ASCII:n merkkikategorioita,
  ;; ks http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html
  ;; L on sanakirjaimet, Punct on ASCII:n välimerkit. Space ja 0-9 sallitaan myös,
  ;; muut korvataan alaviiva-merkillä.
  (s/replace otsikko #"[^\p{L} \d\p{Punct}]" "_"))

(defrecord VainLahetys [palvelin vastausosoite]
  Sahkoposti
  (rekisteroi-kuuntelija! [_ _]
    (log/info "Vain lähetys sähköposti ei tue kuuntelijan rekisteröintiä!"))
  (laheta-viesti! [_ lahettaja vastaanottaja otsikko sisalto]
    (postal/send-message {:host palvelin}
                         {:from lahettaja
                          :to vastaanottaja
                          :subject (sanitoi-otsikko otsikko)
                          :body [{:type "text/html; charset=UTF-8"
                                  :content sisalto}]}))
  (vastausosoite [_] vastausosoite)
  (laheta-viesti-ja-liite! [this lahettaja vastaanottajat otsikko sisalto tiedosto-nimi]
    ;; Ei implementoida ainakaan vielä
    nil)
  Kuuntele
  (kuuntele! [this jono kuuntelija-fn]
    nil)
  
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn luo-vain-lahetys
  "Luo sähköpostirajapinnan, joka tukee vain lähetystä."
  [palvelin vastausosoite]
  (->VainLahetys palvelin vastausosoite))
