(ns harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tloik.sanomat.tloik-kuittaus-sanoma :as tloik-kuittaus-sanoma]
            [harja.palvelin.integraatiot.tloik.kasittely.toimenpide :as toimenpide]))

(defn laheta-ilmoitustoimenpide [jms-lahettaja db toimenpide-id]
  (try
    (if-let [xml (toimenpide/muodosta-toimenpide db toimenpide-id)]
      (do
        (jms-lahettaja xml)
        ;; todo: merkitse toimenpide odottamaan vastausta
        )
      ;; todo: merkitse toimenpiteelle lähetysvirhe
      )
    (catch Exception e
      ;; todo: merkitse toimenpiteelle lähetysvirhe
      )
    ))



(defn vastaanota-kuittaus [integraatioloki db viesti]
  (log/debug "Vastaanotettiin Sampon kuittausjonosta viesti: " viesti)
  (let [kuittaus-xml (.getText viesti)]
    (let [kuittaus (tloik-kuittaus-sanoma/lue-kuittaus kuittaus-xml)
          onnistunut (not (contains? kuittaus :virhe))]
      (log/debug "Luettiin kuittaus T-LOIK:sta: " kuittaus)
      (if-let [viesti-id (:viesti-id kuittaus)]
        (let [lahetystyyppi (if (= :maksuera (:viesti-tyyppi kuittaus)) "maksuera-lähetys" "kustannussuunnitelma-lahetys")]
          (integraatioloki/kirjaa-saapunut-jms-kuittaus integraatioloki kuittaus-xml viesti-id lahetystyyppi onnistunut)
          ;; todo: merkitse kuittaus ilmoitustoimenpiteelle
          )
        (log/error "Sampon kuittauksesta ei voitu hakea viesti-id:tä.")))))