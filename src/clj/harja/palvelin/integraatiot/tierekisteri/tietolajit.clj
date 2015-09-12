(ns harja.palvelin.integraatiot.tierekisteri.tietolajit
  (:require [taoensso.timbre :as log]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus :as vastaussanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :refer [+tietolajien-haku+]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-virheet [url tunniste muutospvm virheet]
  (throw+ {:type  :tierekisteri-kutsu-epaonnistui
           :error (str "Tietolajin haku epäonnistui (URL: " url ") tunnisteella: " tunniste
                       " & muutospäivämäärällä: " muutospvm "."
                       "Virheet: " (string/join virheet))}))

(defn kirjaa-varoitukset [url tunniste muutospvm virheet]
  (log/warn (str "Tietolajin haku palautti virheitä (URL: " url ") tunnisteella: " tunniste
                 " & muutospäivämäärällä: " muutospvm "."
                 "Virheet: " (string/join virheet))))

(defn kasittele-vastaus [url tunniste muutospvm vastausxml]
  (let [vastausdata (vastaussanoma/lue vastausxml)
        onnistunut (:onnistunut vastausdata)
        virheet (:virheet vastausdata)]
    (if (not onnistunut)
      (kasittele-virheet url tunniste muutospvm virheet)
      (do
        (when (not-empty virheet)
          (kirjaa-varoitukset url tunniste muutospvm virheet))
        vastausdata))))

(defn hae-tietolajit [integraatioloki url tunniste muutospvm]
  (log/debug "Hae tietolajin: " tunniste " ominaisuudet muutospäivämäärällä: " muutospvm " Tierekisteristä")
  (let [kutsudata (kutsusanoma/muodosta tunniste muutospvm)
        palvelu-url (str url "/haetietolajit")
        otsikot {"Content-Type" "text/xml"}
        vastausdata (http/laheta-post-kutsu
                      integraatioloki
                      "hae-tietolaji"
                      "tierekisteri"
                      palvelu-url
                      otsikot
                      nil
                      kutsudata
                      (fn [vastaus-xml]
                        (kasittele-vastaus palvelu-url tunniste muutospvm vastaus-xml)))]
    vastausdata))