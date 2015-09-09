(ns harja.palvelin.integraatiot.tierekisteri.tietueet
  (:require [taoensso.timbre :as log]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-hakukutsu :as kutsusanoma]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus :as vastaussanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http]

    ;todo: poista
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :as testi]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-virheet [url tunniste tietolajitunniste virheet]
  (throw+ {:type  :tierekisteri-kutsu-epaonnistui
           :error (str "Tietueen haku epäonnistui (URL: " url ") tunnisteella: " tunniste
                       " & tietolajitunnisteella: " tietolajitunniste "."
                       "Virheet: " (string/join virheet))}))

(defn kirjaa-varoitukset [url tunniste tietolajitunniste virheet]
  (log/warn (str "Tietueen haku palautti virheitä (URL: " url ") tunnisteella: " tunniste
                 " & muutospäivämäärällä: " tietolajitunniste "."
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

(defn hae-tietueet [integraatioloki url id tietolaji]
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

(defn kutsu [tietolaji tunniste]
  (let [testitietokanta (apply tietokanta/luo-tietokanta testi/testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (hae-tietueet integraatioloki "http://harja-test.solitaservices.fi/harja/integraatiotesti/tierekisteri/haetietolajit" tunniste tietolaji)))
