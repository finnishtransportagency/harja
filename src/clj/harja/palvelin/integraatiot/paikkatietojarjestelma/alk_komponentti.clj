(ns harja.palvelin.integraatiot.paikkatietojarjestelma.alk-komponentti
  (:require
    [taoensso.timbre :as log]
    [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
    [harja.palvelin.integraatiot.integraatiopisteet.tiedosto :as tiedosto]
    [clj-time.coerce :as time-coerce]))

(defn kasittele-tiedoston-muutospaivamaaran-hakuvastaus [otsikko]
  (let [muutospaivamaara (:last-modified otsikko)]
    (if muutospaivamaara
      (time-coerce/to-sql-time (java.util.Date. muutospaivamaara))
      nil)))

(defn hae-tiedoston-muutospaivamaara [integraatioloki integraatio url]
  (log/debug "Haetaan tiedoston muutospäivämäärä ALK:sta URL:lla: " url)
  (http/laheta-head-kutsu integraatioloki
                          integraatio
                          "ptj"
                          url
                          nil
                          nil
                          (fn [_ otsikko] (kasittele-tiedoston-muutospaivamaaran-hakuvastaus otsikko))))

(defn hae-tiedosto [integraatioloki integraatio url kohde]
  (log/debug "Haetaan tiedosto ALK:sta URL:lla: " url " kohteeseen: " kohde)
  (tiedosto/lataa-tiedosto integraatioloki "ptj" integraatio url kohde))