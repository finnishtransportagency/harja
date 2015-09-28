(ns harja.palvelin.integraatiot.tierekisteri.tietueet
  (:require [taoensso.timbre :as log]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueiden-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus :as vastaussanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]

            ;; TODO remove
            [harja.testi :as testi])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-virheet [url tr tietolajitunniste muutospvm virheet]
  (throw+ {:type    :tierekisteri-kutsu-epaonnistui
           :virheet [{:viesti (str "Tietueiden haku epäonnistui (URL: " url ") tr-osoitteella: " (pr-str tr)
                                  " & tietolajitunnisteella: " tietolajitunniste
                                  " & muutospäivämäärällä: " muutospvm "."
                                  "Virheet: " (string/join virheet))
                     :koodi :tietueiden-haku-epaonnistui}]}))

(defn kirjaa-varoitukset [url tr tietolajitunniste muutospvm virheet]
  (log/warn (str "Tietueiden haku palautti virheitä (URL: " url ") tr-osoitteella: " (pr-str tr)
                 " & tietolajitunnisteella: " tietolajitunniste
                 " & muutospäivämäärällä: " muutospvm "."
                 "Virheet: " (string/join virheet))))


(defn kasittele-vastaus [url tr tietolajitunniste muutospvm vastausxml]
  (let [vastausdata (vastaussanoma/lue vastausxml)
        onnistunut (:onnistunut vastausdata)
        virheet (:virheet vastausdata)]
    (if (not onnistunut)
      (kasittele-virheet url tr tietolajitunniste muutospvm virheet)
      (do
        (when (not-empty virheet)
          (kirjaa-varoitukset url tr tietolajitunniste muutospvm virheet))
        vastausdata))))

(defn hae-tietueet [integraatioloki url tr tietolaji muutospvm]
  (log/debug "Haetaan tietue tierekisteriosoitteella: " (pr-str tr) ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [kutsudata (kutsusanoma/muodosta-kutsu tr tietolaji muutospvm)
        palvelu-url (str url "/haetietueet")
        otsikot {"Content-Type" "text/xml"}
        vastausdata (http/laheta-post-kutsu
                      integraatioloki
                      "hae-tietueet"
                      "tierekisteri"
                      palvelu-url
                      otsikot
                      nil
                      kutsudata
                      (fn [vastaus-xml] (kasittele-vastaus palvelu-url tr tietolaji muutospvm vastaus-xml)))]
    vastausdata))