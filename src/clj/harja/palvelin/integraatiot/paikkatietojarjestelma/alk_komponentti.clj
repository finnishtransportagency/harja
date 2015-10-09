(ns harja.palvelin.integraatiot.paikkatietojarjestelma.alk-komponentti
  (:require
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]
    [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
    [harja.palvelin.integraatiot.integraatiopisteet.tiedosto :as tiedosto]
    [clj-time.coerce :as time-coerce]))

(defprotocol AlkPalvelut
  (hae-tiedoston-muutospaivamaara [this integraatio url])
  (hae-tiedosto [this integraatio url kohde]))

(defn kasittele-tiedoston-muutospaivamaaran-hakuvastaus [otsikko]
  (let [muutospaivamaara (:last-modified otsikko)]
    (if muutospaivamaara
      (time-coerce/to-sql-time (java.util.Date. muutospaivamaara))
      nil)))

(defn kysy-tiedoston-muutospaivamaara [integraatioloki integraatio url]
  (log/debug "Haetaan tiedoston muutospäivämäärä ALK:sta URL:lla: " url)
  (http/laheta-head-kutsu integraatioloki
                          integraatio
                          "ptj"
                          url
                          nil
                          nil
                          (fn [_ otsikko] (kasittele-tiedoston-muutospaivamaaran-hakuvastaus otsikko))))

(defn lataa-tiedosto [integraatioloki integraatio url kohde]
  (log/debug "Haetaan tiedosto ALK:sta URL:lla: " url " kohteeseen: " kohde)
  (tiedosto/lataa-tiedosto integraatioloki "ptj" integraatio url kohde))

(defrecord Alk []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  AlkPalvelut
  (hae-tiedoston-muutospaivamaara [this integraatio url]
    (when (not (empty? url))
      (kysy-tiedoston-muutospaivamaara (:integraatioloki this) integraatio url)))

  (hae-tiedosto [this integraatio url kohde]
    (when (not (empty? url))
      (lataa-tiedosto (:integraatioloki this) integraatio url kohde))))