(ns harja.palvelin.integraatiot.sahkoposti
  "Määrittelee sähköpostin lähetyksen yleisen rajapinnan"
  (:require [postal.core :as postal]
            [taoensso.timbre :as log]
            [clojure.string :as s]
            [harja.palvelin.tapahtuma-protokollat :refer [Kuuntele]]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io])
  (:import (java.io File)))

(defprotocol Sahkoposti
  (rekisteroi-kuuntelija!
   [this kuuntelija-fn]
   "Rekisteröi funktion, joka vastaanottaa sähköpostiviestit.")
  (laheta-viesti!
   [this lahettaja vastaanottaja otsikko sisalto headers]
   "Lähettää viestin vastaanottajalle annetulla otsikolla ja sisällöllä")
  (laheta-ulkoisella-jarjestelmalla-viesti!
    [this lahettaja vastaanottaja otsikko sisalto headers username password port]
    "Lähettää viestin vastaanottajalle annetulla otsikolla ja sisällöllä käyttäen ulkoista järjestelmää, joka vaatii kirjautumistiedot.")
  (vastausosoite
   [this]
   "Palauttaa oletus vastausosoitteen, jota voi käyttää lähettäjänä ja johon lähetetyt viestit
    tulevat takaisin tälle kuuntelijalle tai api rajapinnalle.")
  (laheta-viesti-ja-liite!
    [this lahettaja vastaanottajat otsikko sisalto headers tiedosto-nimi]
    "Lähettää viestin vastaanottajalle annetulla otsikolla ja sisällöllä. Sisällön pitäisi sisältää myös liite"))

(defn sanitoi-otsikko [otsikko]
  ;; Javan regexpien \p -luokat vastaavat Unicoden tai ASCII:n merkkikategorioita,
  ;; ks http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html
  ;; L on sanakirjaimet, Punct on ASCII:n välimerkit. Space ja 0-9 sallitaan myös,
  ;; muut korvataan alaviiva-merkillä.
  (s/replace otsikko #"[^\p{L} \d\p{Punct}]" "_"))

(defn- laheta-postal-viesti-ja-liite [{:keys [palvelin lahettaja vastaanottajat otsikko sisalto tiedosto-nimi]}]
  (let [temp-file (when (:pdf-liite sisalto)
                    (File/createTempFile ^String tiedosto-nimi ".pdf"))
        temp-file (when temp-file
                    (do
                      (io/copy (:pdf-liite sisalto) temp-file)
                      (.deleteOnExit temp-file)
                      temp-file))]
    (postal/send-message {:host palvelin}
      {:from lahettaja
       :to vastaanottajat
       :subject (sanitoi-otsikko otsikko)
       :body (cond-> [{:type "text/html; charset=UTF-8"
                       :content (:viesti sisalto)}]
               ;; Jos liitetiedosto löytyy, otetaan se mukaan sähköpostiin liitteeksi.
               temp-file
               (conj
                 {:type :attachment
                  :content-type "application/pdf; charset=utf-8"
                  :file-name tiedosto-nimi
                  :content temp-file}))})

    ;; Poista väliaikainen tiedosto heti lähetyksen jälkeen.
    (when temp-file
      (.delete temp-file))))

(defrecord VainLahetys [palvelin vastausosoite]
  Sahkoposti
  (rekisteroi-kuuntelija! [_ _]
    (log/info "Vain lähetys sähköposti ei tue kuuntelijan rekisteröintiä!"))
  (laheta-viesti! [_ lahettaja vastaanottaja otsikko sisalto headers]
    (do
     (log/info "VainLahetys :: postal - Lähetettiin sähköposti. Tarkista tietokannasta yksityiskohdat.")
     (log/debug "VainLahetys :: Lähettäjä:" (pr-str lahettaja) "Vastaanottaja:" (pr-str vastaanottaja))
     (postal/send-message {:host palvelin}
       {:from lahettaja
        :to vastaanottaja
        :subject (sanitoi-otsikko otsikko)
        :body [{:type "text/html; charset=UTF-8"
                :content sisalto}]})))
  (laheta-ulkoisella-jarjestelmalla-viesti! [_ lahettaja vastaanottaja otsikko sisalto headers username password port]
    (let [_ (log/debug "VainLahetys :: postal :: Lähetetään ulkoisen smtp palvelimen kautta")
          smtp-asetukset {:host palvelin
                          :user username
                          :pass password
                          :port port
                          :tls true}
          viesti-asetukset {:from lahettaja
                            :to vastaanottaja
                            :subject (sanitoi-otsikko otsikko)
                            :body [{:type "text/html; charset=UTF-8"
                                    :content sisalto}]}
          _ (log/debug "VainLahetys :: postal :: smtp-asetukset:" smtp-asetukset)
          _ (log/debug "VainLahetys :: postal :: viesti-asetukset:" viesti-asetukset)
          vastaus (postal/send-message smtp-asetukset viesti-asetukset)
          _ (println "VainLahetys :: posta ::  vastaus:" vastaus)]
      vastaus))
  (vastausosoite [_] vastausosoite)
  (laheta-viesti-ja-liite! [_ lahettaja vastaanottajat otsikko sisalto headers tiedosto-nimi]
    (laheta-postal-viesti-ja-liite {:palvelin palvelin
                                    :lahettaja lahettaja
                                    :vastaanottajat vastaanottajat
                                    :otsikko otsikko
                                    :sisalto sisalto
                                    :tiedosto-nimi tiedosto-nimi}))
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
