(ns harja.palvelin.integraatiot.api.tyokalut.palvelut
  "Palvelutyökaluja"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]))

(defn julkaise [http db integraatioloki palvelut]
  (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn]} palvelut :when (= tyyppi :GET)]
    (julkaise-reitti
      http palvelu
      (GET polku request
           (kasittele-kutsu db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn))))

  (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn]} palvelut :when (= tyyppi :POST)]
    (julkaise-reitti
      http palvelu
      (POST polku request
            (kasittele-kutsu db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn))))

  (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn]} palvelut :when (= tyyppi :DELETE)]
    (julkaise-reitti
      http palvelu
      (DELETE polku request
              (kasittele-kutsu db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn)))))

(defn poista [http palvelut]
  (doseq [{:keys [palvelu]} palvelut]
    (poista-palvelut http palvelu)))