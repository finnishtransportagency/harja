(ns harja.palvelin.integraatiot.paikkatietojarjestelma.alk-komponentti
  (:require
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]
    [harja.palvelin.integraatiot.integraatiopisteet.http :as http]

    ;; poista
    [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
    [harja.palvelin.komponentit.tietokanta :as tietokanta]
    [harja.testi :as testi]
    [clj-time.coerce :as time-coerce]))

(defprotocol AlkPalvelut
  (hae-tiedoston-muutospaivamaara [this url])
  (hae-tiedosto [this url]))

(defn kasittele-tiedoston-muutospaivamaaran-hakuvastaus [otsikko]
  (let [muutospaivamaara (:last-modified otsikko)]
    (if muutospaivamaara
      (println
        (time-coerce/to-sql-time (java.util.Date. muutospaivamaara)))
      nil)))

(defn kasittele-tiedoston-hakuvastaus [vastaus]
  (println "Vastaus: " vastaus))

(defn kysy-tiedoston-muutospaivamaara [integraatioloki url]
  (log/debug "Haetaan tiedoston muutospäivämäärä ALK:sta URL:lla: " url)
  (http/laheta-head-kutsu integraatioloki
                          "hae-tiedoston-muutospaivamaara"
                          "alk"
                          url
                          nil
                          nil
                          (fn [_ otsikko] (kasittele-tiedoston-muutospaivamaaran-hakuvastaus otsikko))))

(defn lataa-tiedosto [integraatioloki url]
  (log/debug "Haetaan tiedosto ALK:sta URL:lla: " url)
  (http/laheta-get-kutsu integraatioloki
                         "hae-tiedosto"
                         "alk"
                         url
                         nil
                         nil
                         (fn [vastaus _] (kasittele-tiedoston-hakuvastaus vastaus))))

(defrecord Alk []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  AlkPalvelut
  (hae-tiedoston-muutospaivamaara [this url]
    (when (not (empty? url))
      (kysy-tiedoston-muutospaivamaara (:integraatioloki this) url)))

  (hae-tiedosto [this url]
    (when (not (empty? url))
      (lataa-tiedosto (:integraatioloki this) url))))

;; todo: poista
(defn aja-tiedoston-muutospaivamaara-kysely []
  (let [testitietokanta (apply tietokanta/luo-tietokanta testi/testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (kysy-tiedoston-muutospaivamaara integraatioloki "http://harja-test.solitaservices.fi/index.html")))

(defn aja-tiedoston-haku []
  (let [testitietokanta (apply tietokanta/luo-tietokanta testi/testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (lataa-tiedosto integraatioloki "http://harja-test.solitaservices.fi/index.html")))