(ns harja.jms-test
  "JMS testit: hornetq"
  (:require [harja.palvelin.komponentit.sonja :as sonja]
            [clojure.core.async :refer [<! go] :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log])
  (:import (javax.jms Message Session TextMessage)
           (java.util UUID)))

(defrecord FeikkiSonja [kuuntelijat viesti-id]
  component/Lifecycle
  (start [this]
    (log/info "Feikki Sonja käynnistetty")
    this)
  (stop [this]
    (log/info "Feikki Sonja lopetettu")
    this)

  sonja/Sonja
  (kuuntele! [_ nimi kuuntelija jarjestelma]
    (log/debug "Lisätään kuuntelija:" kuuntelija ", jonoon: " nimi ", jarjestelmaan: " jarjestelma)
    (swap! kuuntelijat
           update-in [nimi]
           (fn [vanhat-kuuntelijat]
             (if (nil? vanhat-kuuntelijat)
               #{kuuntelija}
               (conj vanhat-kuuntelijat kuuntelija))))
    #(swap! kuuntelijat update-in [nimi] disj kuuntelija))
  (kuuntele! [this nimi kuuntelija]
    (sonja/kuuntele! this nimi kuuntelija nil))

  (laheta [_ nimi viesti {:keys [correlation-id]} jarjestelma]
    (log/info "Feikki Sonja lähettää jonoon: " nimi)
    (let [msg (sonja/luo-viesti viesti (reify javax.jms.Session
                                         (createTextMessage [this]
                                           (let [txt (atom nil)
                                                 id (str "ID:" (swap! viesti-id inc))]
                                             (reify TextMessage
                                               (getJMSMessageID [_] id)
                                               (setText [_ t] (reset! txt t))
                                               (getText [_] @txt)
                                               (getJMSCorrelationID [_] correlation-id))))))]
      (go (<! (async/timeout 1))
          (log/debug "Lähetys menossa.")
          (doseq [k (get @kuuntelijat nimi)]
            (log/debug "Kutsutaan kuuntelijaa:" k)
            (try
              (k msg)
              (catch Exception e
                (log/warn "VIRHE KUUNTELIJASSA: " e)))))
      (.getJMSMessageID msg)))

  (laheta [this nimi viesti otsikot]
    (sonja/laheta this nimi viesti otsikot nil))

  (laheta [this nimi viesti]
    (sonja/laheta this nimi viesti nil))

  (aloita-yhteys [this]
    (log/debug "Feikki Sonja, aloita muka yhteys")))


(defn feikki-sonja []
  (->FeikkiSonja (atom nil) (atom 0)))

        
      
    
