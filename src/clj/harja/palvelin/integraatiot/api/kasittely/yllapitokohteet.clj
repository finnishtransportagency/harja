(ns harja.palvelin.integraatiot.api.kasittely.yllapitokohteet
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn paivita-alikohteet [db kohde alikohteet]
  (q-yllapitokohteet/poista-yllapitokohteen-kohdeosat! db {:id (:id kohde)})
  (mapv
    (fn [alikohde]
      (let [sijainti (:sijainti alikohde)
            parametrit {:yllapitokohde (:id kohde)
                        :nimi (:nimi alikohde)
                        :tunnus (:tunnus alikohde)
                        :tr_numero (:numero sijainti)
                        :tr_alkuosa (:aosa sijainti)
                        :tr_alkuetaisyys (:aet sijainti)
                        :tr_loppuosa (:losa sijainti)
                        :tr_loppuetaisyys (:let sijainti)
                        :tr_ajorata (:tr-ajorata kohde)
                        :tr_kaista (:tr-kaista kohde)
                        :toimenpide (:toimenpide alikohde)
                        :ulkoinen-id (:ulkoinen-id alikohde)}]
        (assoc alikohde :id (:id (q-yllapitokohteet/luo-yllapitokohdeosa<! db parametrit)))))
    alikohteet))

(defn paivita-kohde [db kohde-id kohteen-sijainti]
  (q-yllapitokohteet/paivita-yllapitokohteen-sijainti!
    db (assoc (clojure.set/rename-keys
                kohteen-sijainti
                {:aosa :tr_alkuosa
                 :aet :tr_alkuetaisyys
                 :losa :tr_loppuosa
                 :let :tr_loppuetaisyys})
         :id
         kohde-id)))