(ns harja.palvelin.integraatiot.turi.sanomat.tyotunnit
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tyojakso [sampoid vuosi vuosikolmannes tunnit]

  [:tyot:tyoaikajakso
   {:xmlns:tyot "http://restimport.xml.turi.oikeatoliot.fi/tyotunnit"}
   [:sampohankenimi "string"]
   [:sampohankeid "string"]
   [:tilaajanvastuuhenkilokayttajatunnus "string"]
   [:tilaajanvastuuhenkiloetunimi "string"]
   [:tilaajanvastuuhenkilosukunimi "string"]
   [:tilaajanvastuuhenkilosposti "string"]
   [:sampourakkanimi "string"]
   [:sampourakkaid "string"]
   [:urakanpaattymispvm "2008-09-29"]
   [:urakkavaylamuoto "Tie"]
   [:urakkatyyppi "muut"]
   [:elyalue "KES ELY"]
   [:alueurakkanro "string"]
   [:lahdejarjestelma "string"]
   [:urakkasampoid sampoid]
   [:vuosi vuosi]
   [:vuosikolmannes vuosikolmannes]
   [:tyotunnit tunnit]])

(defn muodosta [sampoid vuosi vuosikolmannes tunnit]
  (let [sisalto (tyojakso sampoid vuosi vuosikolmannes tunnit)
        xml (xml/tee-xml-sanoma sisalto)]
    (if-let [virheet (xml/validoi-xml "xsd/turi/" "tyotunnit-rest.xsd" xml)]
      (let [virheviesti (format "Työtuntien TURI-lähetyksen XML ei ole validia.\n
                                 Validointivirheet: %s\n
                                 Muodostettu sanoma:\n
                                 %s" virheet xml)]
        (log/error virheviesti)
        (throw+ {:type :invalidi-tyotunti-xml
                 :error virheviesti}))
      xml)))
