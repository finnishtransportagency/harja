(ns harja.jms
  "JMS testit: hornetq"
  (:require [harja.palvelin.komponentit.sonja :as sonja]
            [clojure.core.async :refer [<! go] :as async])
  (:import (javax.jms Message Session TextMessage)))



(defn feikki-sonja []
  ;; Kuuntelijat on atomi nimestä kuuntelijajoukkoon
  (let [kuuntelijat (atom nil)
        viesti-id (atom 0)

        ;; Täysin feikki JMS istunto, joka osaa luoda text messagen
        istunto (reify javax.jms.Session
                  (createTextMessage [this]
                    (let [txt (atom nil)
                          id (str "ID:" (swap! viesti-id inc))]
                      (reify TextMessage
                        (getJMSMessageID [_] id)
                        (setText [_ t] (reset! txt t))
                        (getText [_] @txt)))))]
    
    (reify sonja/Sonja
      (kuuntele [_ nimi kuuntelija]
        (swap! kuuntelijat
               update-in [nimi]
               (fn [vanhat-kuuntelijat]
                 (if (nil? vanhat-kuuntelijat)
                   #{kuuntelija}
                   (conj vanhat-kuuntelijat kuuntelija))))
        #(swap! kuuntelijat update-in [nimi] disj kuuntelija))
      
      (laheta [_ nimi viesti]
        (let [msg (sonja/luo-viesti viesti istunto)]
          (go (<! (async/timeout 100)) ;; sadan millisekunnin päästä lähetys
              (doseq [k (get @kuuntelijat nimi)]
                (k msg)))
          (.getJMSMessageID msg))))))

        
      
    
