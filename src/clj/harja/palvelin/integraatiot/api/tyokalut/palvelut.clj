(ns harja.palvelin.integraatiot.api.tyokalut.palvelut
  "Palvelutyökaluja"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [compojure.core :refer [POST GET DELETE PUT]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu kasittele-kutsu-async]]))

(defn- valita-kutsu [async db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn]
  (if async
    (kasittele-kutsu-async db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn)
    (kasittele-kutsu db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn)))

(defn julkaise [http db integraatioloki palvelut]
  (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn async]} palvelut :when (= tyyppi :GET)]
    (julkaise-reitti
      http palvelu
      (GET polku request
        (valita-kutsu async db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn))))

  (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn async]} palvelut :when (= tyyppi :POST)]
    (julkaise-reitti
      http palvelu
      (POST polku request
        (valita-kutsu async db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn))))

  (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn async]} palvelut :when (= tyyppi :PUT)]
    (julkaise-reitti
      http palvelu
      (PUT polku request
        (valita-kutsu async db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn))))

  (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn async]} palvelut :when (= tyyppi :DELETE)]
    (julkaise-reitti
      http palvelu
      (DELETE polku request
        (valita-kutsu async db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn)))))

(defn poista [http palvelut]
  (doseq [{:keys [palvelu]} palvelut]
    (poista-palvelut http palvelu)))