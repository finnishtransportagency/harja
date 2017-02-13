(ns harja.palvelin.integraatiot.api.validointi.parametrit
  "API-kutsujen parametrien validointi"

  (:require [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.domain.oikeudet :as oikeudet]
)
  (:use [slingshot.slingshot :only [throw+]]))

(defn heita-virheelliset-parametrit-poikkeus [viesti]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (throw+ {:type    virheet/+viallinen-kutsu+
           :virheet [{:koodi  virheet/+puutteelliset-parametrit+
                      :viesti viesti}]}))

(defn tarkista-parametri [parametrit avain viesti]
  (when (nil? (avain parametrit))
    (heita-virheelliset-parametrit-poikkeus viesti)))

(defn tarkista-parametrit [parametrit pakolliset]
  (doseq [pakollinen (seq pakolliset)]
    (tarkista-parametri parametrit (first pakollinen) (second pakollinen))))
