(ns harja.palvelin.integraatiot.velho.velho-komponentti
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.yha :as q-yha-tiedot]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.integraatiot.yha.yllapitokohteet :as yllapitokohteet]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma :as kohteen-lahetyssanoma]
            [harja.tyokalut.xml :as xml])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defprotocol VelhoIntegraatio
  (laheta-paallystysilmoitukset [this urakka-id kohde-idt]))

(defn tee-sanoma [urakka kohteet]
  (let [sisalto (kohteen-lahetyssanoma/muodosta-sanoma "http://www.liikennevirasto.fi/xsd/velho" urakka kohteet)
        xml (xml/tee-xml-sanoma sisalto)]
    (if-let [virheet (xml/validoi-xml "xsd/velho/" "velho.xsd" xml)]
      (let [virheviesti (format "Kohdetta ei voi lähettää Velhoon. XML ei ole validia. Validointivirheet: %s" virheet)]
        (log/error virheviesti))
      xml)))

(defn laheta-paallystysilmoitukset-velhoon [integraatioloki db {:keys [paallystetoteumat-url kayttajatunnus salasana]} urakka-id kohde-idt]
  (log/debug (format "Lähetetään urakan (id: %s) kohteiden: %s paallystystoteumat Velhoon URL:lla: %s." urakka-id kohde-idt paallystetoteumat-url))
  (when paallystetoteumat-url
    (try+
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "velho" "laheta-paallystystoteumat" nil
        (fn [konteksti]
          (when-let [urakka (first (q-yha-tiedot/hae-urakan-yhatiedot db {:urakka urakka-id}))]
            (let [urakka (assoc urakka :harjaid urakka-id :sampoid (q-urakat/hae-urakan-sampo-id db {:urakka urakka-id}))
                  kohteet (mapv #(yllapitokohteet/hae-kohteen-tiedot db % true) kohde-idt)
                  kutsudata (tee-sanoma urakka kohteet)
                  otsikot {"Content-Type" "application/json; charset=utf-8"}
                  http-asetukset {:metodi :POST
                                  :url paallystetoteumat-url
                                  :kayttajatunnus kayttajatunnus
                                  :salasana salasana
                                  :otsikot otsikot}]
              (when kutsudata
                (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata))))))
      (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
        false))))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  VelhoIntegraatio
  (laheta-paallystysilmoitukset [this urakka-id kohde-idt]
    (laheta-paallystysilmoitukset-velhoon (:integraatioloki this) (:db this) asetukset urakka-id kohde-idt)))
