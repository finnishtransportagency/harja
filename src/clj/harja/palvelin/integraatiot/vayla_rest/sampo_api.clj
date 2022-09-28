(ns harja.palvelin.integraatiot.vayla-rest.sampo-api
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut julkaise-palvelu]]
            [harja.palvelin.integraatiot.sampo.tuonti :as tuonti]
            [harja.palvelin.integraatiot.api.tyokalut.xml-skeemat :as xml-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :as kutsukasittely]
            [taoensso.timbre :as log]))

(defprotocol Maksueralahetys
  (laheta-maksuera-sampoon [this numero]))

(defrecord ApiSampo []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (log/debug "Käynnistetään SampoAPI-komponentti")
    (julkaise-reitti
      http :sampo-vastaanotto
      (POST "/api/sampo" request
        (kutsukasittely/kasittele-sampo-kutsu db integraatioloki :sisaanluku
          request xml-skeemat/+sampo-kutsu+
          (fn [kutsun-parametrit kutsun-data kayttaja db]
            (tuonti/kasittele-api-viesti db integraatioloki kutsun-data))
          "sampo"))))
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :sampo-vastaanotto)
    this)

  Maksueralahetys
  (laheta-maksuera-sampoon [{:keys [sonja db integraatioloki]} numero]
    #_ (let [urakkaid (q-maksuerat/hae-maksueran-urakka db numero)
          summat (q-maksuerat/hae-urakan-maksueran-summat db urakkaid)
          ;maksueran-lahetys (maksuerat/laheta-maksuera sonja integraatioloki db lahetysjono-ulos numero summat)
          ;kustannussuunnitelman-lahetys
          #_ (kustannussuunnitelmat/laheta-kustannussuunitelma sonja
            integraatioloki
            db
            lahetysjono-ulos
            numero)]
      #_ {:maksuera maksueran-lahetys
       :kustannussuunnitelma kustannussuunnitelman-lahetys})))
