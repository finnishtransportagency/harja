(ns harja.palvelin.integraatiot.sampo.vienti
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clj-time.core :as t]
            [harja.kyselyt.maksuerat :as qm]
            [harja.kyselyt.kustannussuunnitelmat :as qk]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-samposta-sanoma :as kuittaus-sampoon-sanoma]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuera]
            [harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat :as kustannussuunnitelma]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]))

(def +xsd-polku+ "xsd/sampo/outbound/")

(defn kasittele-kuittaus [integraatioloki db viesti jono]
  (log/warn "Vastaanotettiin Sampon kuittausjonosta viesti: " viesti)
  (let [kuittaus-xml (.getText viesti)]
    ;; Validointia ei tehdä, koska jostain syystä Sampon itsensä lähettämät kuittaukset eivät mene läpi validoinnista
    ;; (if (xml/validi-xml? +xsd-polku+ "status.xsd" kuittaus-xml)
    (let [kuittaus (kuittaus-sampoon-sanoma/lue-kuittaus kuittaus-xml)
          onnistunut (not (contains? kuittaus :virhe))]
      (log/debug "Luettiin kuittaus: " kuittaus)
      (if-let [viesti-id (:viesti-id kuittaus)]
        (let [lahetystyyppi (if (= :maksuera (:viesti-tyyppi kuittaus))
                              "maksuera-lähetys"
                              "kustannussuunnitelma-lahetys")]
          (integraatioloki/kirjaa-saapunut-jms-kuittaus
            integraatioloki
            kuittaus-xml
            viesti-id
            lahetystyyppi
            onnistunut jono)
          (if (= :maksuera (:viesti-tyyppi kuittaus))
            (maksuera/kasittele-maksuera-kuittaus db kuittaus viesti-id)
            (kustannussuunnitelma/kasittele-kustannussuunnitelma-kuittaus db kuittaus viesti-id)))
        (log/error "Sampon kuittauksesta ei voitu hakea viesti-id:tä.")))))

(defn aja-paivittainen-api-lahetys [db integraatioloki api-sampo-asetukset]
  (log/info "Maksuerien päivittäinen lähetys käynnistetty apin kautta: " (t/now))
  (let [maksuerat (qm/hae-likaiset-maksuerat db)
        kustannussuunnitelmat (qk/hae-likaiset-kustannussuunnitelmat db)
        urakkaidt (distinct (map :urakkaid maksuerat))
        urakoiden-summat (group-by :urakka_id (mapcat #(qm/hae-urakan-maksueran-summat db %) urakkaidt))
        _ (log/warn "Käsitellään " (count maksuerat) " maksuerää ja " (count kustannussuunnitelmat) " kustannussuunnitelmaa.")
        ;; Sampo ei kykene käsittelemään kustannussuunnitelmia, ennen maksueriä.
        ;; Sampo vastaa maksuerään xml viestillä, kun se on prosessoitu. Ja vastauksen jälkeen kustannussuunnitelmat voidaan lähettää.
        ;; Joten otetaan maksuerien lähetystiedot tässä erikseen ylös ja lähetetään kustannussuunnitelmat, kun maksueriin on saatu vastaukset
        maksuera-vastaukset (mapv (fn [{maksuera-numero :numero urakkaid :urakkaid}]
                                    (try
                                      (let [summat (urakoiden-summat urakkaid)]
                                        (maksuera/laheta-api-maksuera db api-sampo-asetukset integraatioloki maksuera-numero summat))
                                      (catch Exception e
                                        (log/warn e (format "Maksuerän (numero: %s) lähetyksessä tapahtui poikkeus: %s." maksuera-numero e)))))
                              maksuerat)]
    (when maksuera-vastaukset
      (doseq [kustannussuunnitelma kustannussuunnitelmat]
        (try
          (let [maksuera-numero (:maksuera kustannussuunnitelma)]
            (kustannussuunnitelma/laheta-api-kustannusuunnitelma db api-sampo-asetukset integraatioloki maksuera-numero))
          (catch Exception e
            (log/warn e (format "Kustannussuunnitelman lähetyksessä tapahtui poikkeus: %s." e))))))))
