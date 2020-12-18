(ns harja.palvelin.integraatiot.jms
  (:require [clojure.core.async :as async]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.sonja :as sonja]))

(def aktiivinen "ACTIVE")
(def sammutettu "CLOSED")

(defmacro exception-wrapper [olio metodi]
  `(try (. ~olio ~metodi)
        aktiivinen
        ~(list 'catch 'javax.jms.IllegalStateException 'e
               sammutettu)
        ~(list 'catch 'Throwable 't
               nil)))

(defn aloita-sonja [jarjestelma]
  (async/go
    (log/info "Aloitaetaan Sonjayhteys")
      (let [{:keys [vastaus virhe kaskytysvirhe]} (async/<! (sonja/kasky (:sonja jarjestelma) {:aloita-yhteys nil}))]
        (when vastaus
          (log/info "Sonja yhteys aloitettu"))
        (when kaskytysvirhe
          (log/error "Sonjayhteyden aloittamisessa käskytysvirhe: " kaskytysvirhe))
        vastaus)))

(defn oletusjarjestelmanimi [jonon-nimi]
  (str "istunto-" jonon-nimi))

(defn yhteyden-tila [yhteys]
  (exception-wrapper yhteys getClientID))

(defn istunnon-tila
  [istunto]
  (exception-wrapper istunto getAcknowledgeMode))

(defn tuottajan-tila [tuottaja]
  (exception-wrapper tuottaja getDeliveryMode))

(defn vastaanottajan-tila [vastaanottaja]
  (exception-wrapper vastaanottaja getMessageListener))

(defn jms-jono-ok?
  ([jms-client jonon-nimi] (jms-jono-ok? jms-client jonon-nimi (oletusjarjestelmanimi jonon-nimi)))
  ([jms-client jonon-nimi jarjestelma]
   (let [jms-tila (-> jms-client :tila deref)
         {:keys [jonot istunto]} (-> jms-tila :istunnot (get jarjestelma))
         {:keys [tuottaja vastaanottaja]} (get jonot jonon-nimi)

         yhteys-ok? (= aktiivinen (yhteyden-tila (:yhteys jms-tila)))
         istunto-ok? (= aktiivinen (istunnon-tila istunto))
         jono-ok? (boolean (cond-> (or tuottaja vastaanottaja)
                                   tuottaja (and (= aktiivinen (tuottajan-tila tuottaja)))
                                   vastaanottaja (and (= aktiivinen (vastaanottajan-tila vastaanottaja)))))]
     (and yhteys-ok? istunto-ok? jono-ok?))))

(defn jms-jono-olemassa?
  ([jms-client jonon-nimi] (jms-jono-ok? jms-client jonon-nimi (oletusjarjestelmanimi jonon-nimi)))
  ([jms-client jonon-nimi jarjestelma]
   (boolean (-> jms-client :tila deref :istunnot (get jarjestelma) :jonot (get jonon-nimi)))))

(defn jms-jonolla-kuuntelija?
  ([jms-client jonon-nimi f-meta] (jms-jonolla-kuuntelija? jms-client jonon-nimi (oletusjarjestelmanimi jonon-nimi) f-meta))
  ([jms-client jonon-nimi jarjestelma f-meta]
   (let [jms-tila (-> jms-client :tila deref)
         {:keys [jonot]} (-> jms-tila :istunnot (get jarjestelma))
         {:keys [kuuntelijat]} (get jonot jonon-nimi)]
     (boolean (some (fn [f]
                      (= (-> f meta :jms-kuuntelija) f-meta))
                    kuuntelijat)))))
