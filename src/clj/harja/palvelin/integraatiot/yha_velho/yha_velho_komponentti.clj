(ns harja.palvelin.integraatiot.yha-velho.yha-velho-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.kyselyt.koodistot :as koodistot]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.paallystys :as q-paallystys]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.yha :as q-yha-tiedot]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma :as kohteen-lahetyssanoma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :as paallystys]
            [harja.pvm :as pvm]
            [clojure.core.memoize :as memoize]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defprotocol YhaVelhoRajapinnat
  (laheta-pot [this urakka-id kohde-id]))

(defn laheta-pot-yhaan-ja-velhoon [integraatioloki db {:keys [paallystetoteuma-url token-url kayttajatunnus salasana]} urakka-id kohde-id] ; petar ovo je rutina koja generise http zahteve
  (log/debug (format "Lähetetään urakan (id: %s) kohteet: %s Yhaan ja Velhoon URL:lla: %s." urakka-id kohde-id paallystetoteuma-url))
  (when (not (str/blank? paallystetoteuma-url))
    (try+
      "nothing yet"
     (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
       (log/error "Päällystysilmoituksen lähetys Velhoon epäonnistui. Virheet: " virheet)
       false))))

(defrecord YhaVelho [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  YhaVelhoRajapinnat
  (laheta-pot [this urakka-id kohde-idt]
    (laheta-pot-yhaan-ja-velhoon (:integraatioloki this) (:db this) asetukset urakka-id kohde-idt)))
