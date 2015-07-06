(ns harja.palvelin.integraatiot.sampo.vienti
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clj-time.core :as t]
            [harja.kyselyt.maksuerat :as qm]
            [harja.kyselyt.kustannussuunnitelmat :as qk]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sampoon-sanoma]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuera]
            [harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat :as kustannussuunnitelma]
            [harja.palvelin.komponentit.sonja :as sonja]))

(defn laheta-sanoma-jonoon [sonja lahetysjono sanoma-xml]
  (sonja/laheta sonja lahetysjono sanoma-xml))

(defn kasittele-kuittaus [db viesti]
  (log/debug "Vastaanotettiin Sonjan kuittausjonosta viesti: " viesti)
  ;; todo: tee xsd-validointi kuittaukselle
  (let [kuittaus (kuittaus-sampoon-sanoma/lue-kuittaus (.getText viesti))]
    (log/debug "Luettiin kuittaus: " kuittaus)
    (if-let [viesti-id (:viesti-id kuittaus)]
      (if (= :maksuera (:viesti-tyyppi kuittaus))
        (maksuera/kasittele-maksuera-kuittaus db kuittaus viesti-id)
        (kustannussuunnitelma/kasittele-kustannussuunnitelma-kuittaus db kuittaus viesti-id))
      (log/error "Sampon kuittauksesta ei voitu hakea viesti-id:tä."))))

(defn laheta-kustannussuunitelma [sonja db lahetysjono-ulos numero]
  (log/debug "Lähetetään kustannussuunnitelma (numero: " numero ") Sampoon.")
  (if-let [kustannussuunnitelma-xml (kustannussuunnitelma/muodosta-kustannussuunnitelma db numero)]
    (if-let [lahetys-id (laheta-sanoma-jonoon sonja lahetysjono-ulos kustannussuunnitelma-xml)]
      (kustannussuunnitelma/merkitse-kustannussuunnitelma-odottamaan-vastausta db numero lahetys-id)
      (do
        (log/error "Kustannussuunnitelman (numero: " numero ") lähetys Sonjaan epäonnistui.")
        (kustannussuunnitelma/merkitse-kustannussuunnitelmalle-lahetysvirhe db numero)
        {:virhe :sonja-lahetys-epaonnistui}))
    (do
      (log/warn "Kustannussuunnitelman (numero: " numero ") lukitus epäonnistui.")
      {:virhe :kustannussuunnitelman-lukitseminen-epaonnistui})))

(defn laheta-maksuera [sonja db lahetysjono-ulos numero]
  (log/debug "Lähetetään maksuera (numero: " numero ") Sampoon.")
  (if-let [maksuera-xml (maksuera/muodosta-maksuera db numero)]
    (if-let [lahetys-id (laheta-sanoma-jonoon sonja lahetysjono-ulos maksuera-xml)]
      (maksuera/merkitse-maksuera-odottamaan-vastausta db numero lahetys-id)
      (do
        (log/error "Maksuerän (numero: " numero ") lähetys Sonjaan epäonnistui.")
        (maksuera/merkitse-maksueralle-lahetysvirhe db numero)
        {:virhe :sonja-lahetys-epaonnistui}))
    (do
      (log/warn "Maksuerän (numero: " numero ") lukitus epäonnistui.")
      {:virhe :maksueran-lukitseminen-epaonnistui})))

(defn aja-paivittainen-lahetys [sonja db lahetysjono-ulos]
  (log/debug "Maksuerien päivittäinen lähetys käynnistetty: " (t/now))
  (let [maksuerat (qm/hae-likaiset-maksuerat db)
        kustannussuunnitelmat (qk/hae-likaiset-kustannussuunnitelmat db)]
    (log/debug "Lähetetään " (count maksuerat) " maksuerää ja " (count kustannussuunnitelmat) " kustannussuunnitelmaa.")
    (doseq [maksuera maksuerat]
      (laheta-maksuera sonja db lahetysjono-ulos (:numero maksuera)))
    (doseq [kustannussuunnitelma kustannussuunnitelmat]
      (laheta-kustannussuunitelma sonja db lahetysjono-ulos (:maksuera kustannussuunnitelma)))))