(ns harja.palvelin.integraatiot.sms.sms-komponentti
  "Tekstiviestin lähetys SMS-integraation kautta. SMS-integraatio käyttää Väyläviraston integraatioväylää."
  (:require [cheshire.core :as cheshire]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [throw+]])
  (:import (java.util UUID)))


(defprotocol Sms
  (rekisteroi-kuuntelija! [this kasittely-fn])
  (laheta [this numero viesti korrelaatio-id otsikot]))


;; -- SMS-lähetys --
(defn kasittele-vastaus [body headers]
  (log/debug (format "SMS-palvelu vastasi: sisältö: %s, otsikot: %s" body headers))

  ;; Integraatiotapahtuma/laheta käsittelee sisäisesti HTTP statuskoodit ja heittää poikkeuksen, jos statuskoodi ei ole 2xx
  ;; Käsitellään tässä vain onnistuneet vastausviestit
  (let [dekoodattu-body (cheshire/decode body true)]
    {:sisalto dekoodattu-body :otsikot headers}))

(defn laheta-sms [db integraatioloki url apiavain puhelinnumero viesti korrelaatio-id otsikot]
  (if (or (empty? apiavain) (empty? url))
    (log/warn "Tunnistautumistietoja tai URLia SMS-palveluun ei ole annettu. Viestiä ei voida lähettää.")
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "sms" "laheta"
      (fn [konteksti]
        (let [otsikot (merge
                        {"Content-Type" "application/json"
                         "x-api-key" apiavain}
                        otsikot)
              payload {:viesti-id (str (UUID/randomUUID))
                       :korrelaatio-id (or korrelaatio-id "")
                       :vastaanottaja puhelinnumero
                       :sisalto viesti}
              http-asetukset {:metodi :POST
                              :url url
                              :otsikot otsikot}
              {body :body headers :headers status :status}
              (integraatiotapahtuma/laheta konteksti :http http-asetukset (cheshire/encode payload))]
          (kasittele-vastaus body headers))))))


(defrecord Tekstiviesti [sms-asetukset kuuntelijat]
  component/Lifecycle
  (start [this]
    this)

  (stop [this]
    (reset! kuuntelijat #{})
    this)

  Sms
  (rekisteroi-kuuntelija! [this kuuntelija-fn]
    (swap! kuuntelijat conj kuuntelija-fn)
    #(swap! kuuntelijat disj kuuntelija-fn))

  (laheta [this numero viesti korrelaatio-id otsikot]
    (laheta-sms (:db this)
      (:integraatioloki this)
      (:url sms-asetukset)
      (:apiavain sms-asetukset)
      numero
      viesti
      korrelaatio-id
      otsikot)))


(defn luo-tekstiviesti-komponentti [sms-asetukset]
  (->Tekstiviesti sms-asetukset (atom #{})))

(defrecord FeikkiTekstiviesti []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  Sms
  (rekisteroi-kuuntelija! [this kasittelija]
    (log/info "Feikki SMS-palvelu EI tue kuuntelijan rekisteröintiä")
    #(log/info "Poistetaan Feikki SMS-palvelun kuuntelija"))
  (laheta [this numero viesti korrelaatio-id otsikot]
    (log/info "Feikki SMS-palvelu lähettää muka viestin numeroon " numero ": " viesti)))

(defn luo-feikki-tekstiviesti-komponentti []
  (->FeikkiTekstiviesti))
