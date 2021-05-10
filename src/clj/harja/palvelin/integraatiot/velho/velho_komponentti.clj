(ns harja.palvelin.integraatiot.velho.velho-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.kyselyt.yha :as q-yha-tiedot]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma :as kohteen-lahetyssanoma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defprotocol VelhoRajapinnat
  (laheta-kohteet [this urakka-id kohde-idt]))

(defn laheta-kohteet-velhoon [integraatioloki db {:keys [paallystetoteuma-url autorisaatio]} urakka-id kohde-idt]
  (log/debug (format "Lähetetään urakan (id: %s) kohteet: %s Velhoon URL:lla: %s." urakka-id kohde-idt paallystetoteuma-url))
  (when (not (str/blank? paallystetoteuma-url))
    (try+
     (integraatiotapahtuma/suorita-integraatio
       db integraatioloki "velho" "kohteiden-lahetys" nil
       (fn [konteksti]
         (if-let [urakka (first (q-yha-tiedot/hae-urakan-yhatiedot db {:urakka urakka-id}))]
           (let [urakka (assoc urakka :harjaid urakka-id
                                      :sampoid (yha/yhaan-lahetettava-sampoid urakka))
                 kohteet (mapv #(yha/hae-kohteen-tiedot db %) kohde-idt)
                 kutsudata (kohteen-lahetyssanoma/muodosta urakka kohteet) ; petar use velho-specific 'muodosta' function
                 otsikot {"Content-Type" "text/xml; charset=utf-8"
                          "Authorization" autorisaatio}
                 http-asetukset {:metodi :POST
                                 :url paallystetoteuma-url
                                 :otsikot otsikot}]
             (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata))
           (log/error (format "Päällystysilmoitusta ei voida lähettää Velhoon: Urakan (id: %s) YHA-tietoja ei löydy." urakka-id))))
       {:virhekasittelija (fn [_ _] (log/error "Päällystysilmoituksen lähetys Velhoon epäonnistui"))})
     (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
       (log/error "Päällystysilmoituksen lähetys Velhoon epäonnistui. Virheet: " virheet)
       false))))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  VelhoRajapinnat
  (laheta-kohteet [this urakka-id kohde-idt]
    (laheta-kohteet-velhoon (:integraatioloki this) (:db this) asetukset urakka-id kohde-idt)))
