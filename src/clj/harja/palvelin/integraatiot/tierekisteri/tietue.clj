(ns harja.palvelin.integraatiot.tierekisteri.tietue
  (:require [taoensso.timbre :as log]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus :as vastaussanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-virheet [url tunniste tietolajitunniste virheet]
  (throw+ {:type    :tierekisteri-kutsu-epaonnistui
           :virheet [:viesti (str "Tietueen haku epäonnistui (URL: " url ") tunnisteella: " tunniste
                                  " & tietolajitunnisteella: " tietolajitunniste "."
                                  "Virheet: " (string/join virheet))
                     :koodi :tietueen-haku-epaonnistui]}))

(defn kirjaa-varoitukset [url tunniste tietolajitunniste virheet]
  (log/warn (str "Tietueen haku palautti virheitä (URL: " url ") tunnisteella: " tunniste
                 " & tietolajitunnisteella: " tietolajitunniste "."
                 "Virheet: " (string/join virheet))))


(defn kasittele-vastaus [url tunniste tietolajitunniste vastausxml]
  (let [vastausdata (vastaussanoma/lue vastausxml)
        onnistunut (:onnistunut vastausdata)
        virheet (:virheet vastausdata)]
    (if (not onnistunut)
      (kasittele-virheet url tunniste tietolajitunniste virheet)
      (do
        (when (not-empty virheet)
          (kirjaa-varoitukset url tunniste tietolajitunniste virheet))
        vastausdata))))

(defn hae-tietue [integraatioloki url id tietolaji]
  (log/debug "Haetaan tietue: " id ", joka kuuluu tietolajiin " tietolaji " Tierekisteristä.")
  (let [kutsudata (kutsusanoma/muodosta id tietolaji)
        palvelu-url (str url "/haetietue")
        otsikot {"Content-Type" "text/xml"}
        vastausdata (http/laheta-post-kutsu
                      integraatioloki
                      "hae-tietue"
                      "tierekisteri"
                      palvelu-url
                      otsikot
                      nil
                      kutsudata
                      (fn [vastaus-xml] (kasittele-vastaus palvelu-url id tietolaji vastaus-xml)))]
    vastausdata))