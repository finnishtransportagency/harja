(ns harja.palvelin.integraatiot.sampo.vienti
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clj-time.core :as t]
            [harja.kyselyt.maksuerat :as qm]
            [harja.kyselyt.kustannussuunnitelmat :as qk]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-samposta-sanoma :as kuittaus-sampoon-sanoma]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuera]
            [harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat :as kustannussuunnitelma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]))

(def +xsd-polku+ "resources/xsd/sampo/outbound/")

(defn laheta-sanoma-jonoon [sonja lahetysjono sanoma-xml]
  (sonja/laheta sonja lahetysjono sanoma-xml))

(defn kasittele-kuittaus [integraatioloki db viesti]
  (log/debug "Vastaanotettiin Sampon kuittausjonosta viesti: " viesti)
  (let [kuittaus-xml (.getText viesti)]
    ;todo: kytke päälle
    ;(if (xml/validoi +xsd-polku+ "status.xsd" kuittaus-xml)
    (let [kuittaus (kuittaus-sampoon-sanoma/lue-kuittaus kuittaus-xml)
          onnistunut (not (contains? kuittaus :virhe))]
      (log/debug "Luettiin kuittaus: " kuittaus)
      (if-let [viesti-id (:viesti-id kuittaus)]
        (if (= :maksuera (:viesti-tyyppi kuittaus))
          (do
            (integraatioloki/kirjaa-saapunut-jms-kuittaus integraatioloki kuittaus-xml viesti-id "maksuera-lähetys" onnistunut)
            (maksuera/kasittele-maksuera-kuittaus db kuittaus viesti-id))
          (do
            (integraatioloki/kirjaa-saapunut-jms-kuittaus integraatioloki kuittaus-xml viesti-id "kustannussuunnitelma-lahetys" onnistunut)
            (kustannussuunnitelma/kasittele-kustannussuunnitelma-kuittaus db kuittaus viesti-id)))
        (log/error "Sampon kuittauksesta ei voitu hakea viesti-id:tä.")))
    ;    (log/error "Samposta vastaanotettu kuittaus ei ole validia XML:ää."))
    ))

(defn laheta-kustannussuunitelma [sonja integraatioloki db lahetysjono-ulos numero]
  (log/debug "Lähetetään kustannussuunnitelma (numero: " numero ") Sampoon.")
  (if-let [kustannussuunnitelma-xml (kustannussuunnitelma/muodosta-kustannussuunnitelma db numero)]
    (if-let [viesti-id (laheta-sanoma-jonoon sonja lahetysjono-ulos kustannussuunnitelma-xml)]
      (do
        (integraatioloki/kirjaa-jms-viesti integraatioloki "sampo" "kustannussuunnitelma-lahetys" viesti-id "ulos" kustannussuunnitelma-xml)
        (kustannussuunnitelma/merkitse-kustannussuunnitelma-odottamaan-vastausta db numero viesti-id))
      (do
        (log/error "Kustannussuunnitelman (numero: " numero ") lähetys Sonjaan epäonnistui.")
        (kustannussuunnitelma/merkitse-kustannussuunnitelmalle-lahetysvirhe db numero)
        {:virhe :sonja-lahetys-epaonnistui}))
    (do
      (log/warn "Kustannussuunnitelman (numero: " numero ") lukitus epäonnistui.")
      {:virhe :kustannussuunnitelman-lukitseminen-epaonnistui})))

(defn laheta-maksuera [sonja integraatioloki db lahetysjono-ulos numero]
  (log/debug "Lähetetään maksuera (numero: " numero ") Sampoon.")
  (if-let [maksuera-xml (maksuera/muodosta-maksuera db numero)]
    (if-let [viesti-id (laheta-sanoma-jonoon sonja lahetysjono-ulos maksuera-xml)]
      (do
        (maksuera/merkitse-maksuera-odottamaan-vastausta db numero viesti-id)
        (integraatioloki/kirjaa-jms-viesti integraatioloki "sampo" "maksuera-lahetys" viesti-id "ulos" maksuera-xml))
      (do
        (log/error "Maksuerän (numero: " numero ") lähetys Sonjaan epäonnistui.")
        (maksuera/merkitse-maksueralle-lahetysvirhe db numero)
        {:virhe :sonja-lahetys-epaonnistui}))
    (do
      (log/warn "Maksuerän (numero: " numero ") lukitus epäonnistui.")
      {:virhe :maksueran-lukitseminen-epaonnistui})))

(defn aja-paivittainen-lahetys [sonja integraatioloki db lahetysjono-ulos]
  (log/debug "Maksuerien päivittäinen lähetys käynnistetty: " (t/now))
  (let [maksuerat (qm/hae-likaiset-maksuerat db)
        kustannussuunnitelmat (qk/hae-likaiset-kustannussuunnitelmat db)]
    (log/debug "Lähetetään " (count maksuerat) " maksuerää ja " (count kustannussuunnitelmat) " kustannussuunnitelmaa.")
    (doseq [maksuera maksuerat]
      (laheta-maksuera sonja integraatioloki db lahetysjono-ulos (:numero maksuera)))
    (doseq [kustannussuunnitelma kustannussuunnitelmat]
      (laheta-kustannussuunitelma sonja integraatioloki db lahetysjono-ulos (:maksuera kustannussuunnitelma)))))