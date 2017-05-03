(ns harja.palvelin.integraatiot.turi.sanomat.tyotunnit
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liitteet])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/turi/")

(defn tyojakso [sampoid vuosi vuosikolmannes tunnit]
  [:p:tyoaikajakso
   {:xmlns:p "http://restimport.xml.turi.oikeatoliot.fi"
    :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
    :xsi:schemalocation "tyotunnit-rest ../../main/resources/xsd/tyotunnit-rest.xsd"}
   [:lahdejarjestelma "Harja"]
   [:urakkasampoid sampoid]
   [:vuosi vuosi]
   [:vuosikolmannes vuosikolmannes]
   [:tyotunnit tunnit]])

(defn muodosta [sampoid vuosi vuosikolmannes tunnit]
  (let [sisalto (tyojakso sampoid vuosi vuosikolmannes tunnit)
        xml (xml/tee-xml-sanoma sisalto)]
    xml
    ;; todo: lisää xsd-validointi, kun skeema saadaan
    #_(if-let [virheet (xml/validoi-xml +xsd-polku+ "poikkeama-rest.xsd" xml)]
        (let [virheviesti (format "Turvallisuuspoikkeaman TURI-lähetyksen XML ei ole validia.\n
                                 Validointivirheet: %s\n
                                 Muodostettu sanoma:\n
                                 %s" virheet xml)]
          (log/error virheviesti)
          (throw+ {:type :invalidi-turvallisuuspoikkeama-xml
                   :error virheviesti}))
        xml)))
