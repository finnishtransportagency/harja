(ns harja.palvelin.integraatiot.tierekisteri.vastauksenkasittely
  (:require [taoensso.timbre :as log]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus :as vastaussanoma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as http])

  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-vastaus-virheet [ virheet virhe-viesti virhe-koodi]
  (throw+ {:type    :tierekisteri-kutsu-epaonnistui
           :virheet [:viesti (str virhe-viesti " Virheet: " (string/join virheet))
                     :koodi virhe-koodi]}))

(defn kirjaa-vastaus-varoitukset [virheet varoitus-viesti]
  (log/warn (str varoitus-viesti " Virheet: " (string/join virheet))))

(defn kasittele-vastaus [vastausxml virhe-viesti virhe-koodi varoitus-viesti]
  (let [vastausdata (vastaussanoma/lue vastausxml)
        onnistunut (:onnistunut vastausdata)
        virheet (:virheet vastausdata)]
    (if (not onnistunut)
      (kasittele-vastaus-virheet virheet virhe-viesti virhe-koodi)
      (do
        (when (not-empty virheet)
          (kirjaa-vastaus-varoitukset virheet varoitus-viesti))
        vastausdata))))

