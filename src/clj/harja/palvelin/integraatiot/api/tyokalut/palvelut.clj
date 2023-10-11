(ns harja.palvelin.integraatiot.api.tyokalut.palvelut
  "Palvelutyökaluja"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [compojure.core :refer [make-route]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu kasittele-kutsu-async]]))

(defn- valita-kutsu [async db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn api-oikeus]
  (if async
    (kasittele-kutsu-async db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn api-oikeus)
    (kasittele-kutsu db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn api-oikeus)))

(defn julkaise [http db integraatioloki palvelut]
  (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn async api-oikeus]} palvelut]
    (julkaise-reitti
      http palvelu
      (make-route
       (case tyyppi
         :GET :get
         :POST :post
         :PUT :put
         :DELETE :delete)
       polku
       (fn [request]
         (valita-kutsu async db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn api-oikeus))))))

(defn poista [http palvelut]
  (doseq [{:keys [palvelu]} palvelut]
    (poista-palvelut http palvelu)))
