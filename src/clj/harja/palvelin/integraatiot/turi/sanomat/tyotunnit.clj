(ns harja.palvelin.integraatiot.turi.sanomat.tyotunnit
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tyojakso [sampoid vuosi vuosikolmannes tunnit]
  [:t:tyoaikajakso
   {:xmlns:t "http://restimport.xml.turi.oikeatoliot.fi/tyotunnit"}
   [:lahdejarjestelma "Harja"]
   [:urakkasampoid sampoid]
   [:vuosi vuosi]
   [:vuosikolmannes vuosikolmannes]
   [:tyotunnit tunnit]])

(defn muodosta [sampoid vuosi vuosikolmannes tunnit]
  (let [sisalto (tyojakso sampoid vuosi vuosikolmannes tunnit)
        xml (xml/tee-xml-sanoma sisalto)]

    (println "--->" xml)

    (if-let [virheet (xml/validoi-xml "xsd/turi/" "tyotunnit-rest.xsd" xml)]
      (let [virheviesti (format "Työtuntien TURI-lähetyksen XML ei ole validia.\n
                                 Validointivirheet: %s\n
                                 Muodostettu sanoma:\n
                                 %s" virheet xml)]
        (log/error virheviesti)
        (println "--->" virheet)

        (throw+ {:type :invalidi-tyotunti-xml
                 :error virheviesti}))
      xml)))
