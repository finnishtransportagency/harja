(ns harja.palvelin.integraatiot.vayla-rest.sampo-api
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST]]
            [taoensso.timbre :as log]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut julkaise-palvelu]]
            [harja.palvelin.integraatiot.sampo.tuonti :as tuonti]
            [harja.palvelin.integraatiot.sampo.vienti :as vienti]
            [harja.palvelin.integraatiot.api.tyokalut.xml-skeemat :as xml-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :as kutsukasittely]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.maksuerat :as q-maksuerat]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuerat]
            [harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat :as kustannussuunnitelmat]
            [harja.pvm :as pvm]))

(defprotocol Maksueralahetys
  (laheta-maksuera-sampoon [this numero]))

(defn tee-paivittainen-lahetys-tehtava [{:keys [db integraatioloki]} {:keys [paivittainen-lahetysaika] :as api-sampo-asetukset}]
  (if paivittainen-lahetysaika
    (do
      (log/debug "Ajastetaan maksuerien ja kustannussuunnitelmien lähetys ajettavaksi joka päivä kello: "
        paivittainen-lahetysaika)
        (ajastettu-tehtava/ajasta-paivittain paivittainen-lahetysaika
        (fn [_] (vienti/aja-paivittainen-api-lahetys db integraatioloki api-sampo-asetukset))))
      (constantly nil)))

(defrecord ApiSampo [api-sampo-asetukset]
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (log/debug "Käynnistetään SampoAPI-komponentti")
    (when (ominaisuus-kaytossa? :api-sampo)
      (julkaise-reitti
        http :sampo-vastaanotto
        (POST "/sampo/api/harja" request
          (kutsukasittely/kasittele-sampo-kutsu db integraatioloki :sisaanluku
            request xml-skeemat/+sampo-kutsu+
            (fn [db kutsun-data tapahtuma-id]
              (tuonti/kasittele-api-viesti db integraatioloki kutsun-data tapahtuma-id))
            "sampo-api"))))
    (if (ominaisuus-kaytossa? :api-sampo)
      (assoc this :paivittainen-lahetys-tehtava (tee-paivittainen-lahetys-tehtava this api-sampo-asetukset))
      this))
  (stop [{http :http-palvelin :as this}]
    (when (ominaisuus-kaytossa? :sampo)
      (poista-palvelut http
        :sampo-vastaanotto))
    (if (ominaisuus-kaytossa? :sampo)
      (dissoc this :paivittainen-lahetys-tehtava)
      this))

  Maksueralahetys
  (laheta-maksuera-sampoon [{:keys [sonja db integraatioloki]} numero]
    (let [urakkaid (q-maksuerat/hae-maksueran-urakka db numero)
          summat (q-maksuerat/hae-urakan-maksueran-summat db urakkaid)
          kustannussuunnitelman-lahetys (kustannussuunnitelmat/laheta-api-kustannusuunnitelma db api-sampo-asetukset integraatioloki numero)
          maksueran-lahetys (maksuerat/laheta-api-maksuera db api-sampo-asetukset integraatioloki numero summat)]
      {:maksuera maksueran-lahetys
       :kustannussuunnitelma kustannussuunnitelman-lahetys})))
