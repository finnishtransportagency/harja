(ns harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-lahetyssanoma
  (:require [harja.kyselyt.urakat :as q-urakka]
            [harja.kyselyt.paikkaus :as q-paikkaus]
            [harja.tyokalut.json-validointi :as json]
            [taoensso.timbre :as log]
            [cheshire.core :as cheshire]
            [namespacefy.core :refer [unnamespacefy]]
            [clojure.walk :refer [prewalk]]
            [clojure.set :refer [rename-keys]])
  (:use [slingshot.slingshot :only [throw+]]))

(def +paikkauksen-vienti+ "json/yha/paikkausten-vienti-request.schema.json")

(defn muodosta-sanoma-json
  "Muodostaa tietokannasta haetuista urakka-, paikkauskohde-, paikkaus- ja paikkauksen_materiaali-tiedoista
  paikkauksen-vienti-request-skeeman mukaisen sanoman."
  [urakka kohde paikkaukset]
  (let [urakka (prewalk #(if (map? %) (unnamespacefy %) %) urakka)
        paikkaukset (prewalk #(if (map? %) (unnamespacefy %) %) paikkaukset)
        kohde (prewalk #(if (map? %) (unnamespacefy %) %) kohde)
        urakka {:urakka (rename-keys urakka {:id :harja-id})}
        kasittele-materiaali (fn [m]
                               {:kivi-ja-sideaine (-> m
                                                      (dissoc :materiaali-id)
                                                      (rename-keys {:kuulamylly-arvo :km-arvo})
                                                      )})
        kasittele-paikkaus (fn [p] {:paikkaus (-> p
                                                  (dissoc :sijainti :urakka-id :paikkauskohde-id :ulkoinen-id)
                                                  (rename-keys {:tierekisteriosoite :sijainti})
                                                  (conj {:kivi-ja-sideaineet (mapv kasittele-materiaali (:materiaalit p))})
                                                  (dissoc :materiaalit))})
        kohteet {:paikkauskohteet [{:paikkauskohde (-> kohde
                                                       (dissoc :ulkoinen-id)
                                                       (rename-keys {:id :harja-id})
                                                       (conj {:paikkaukset (mapv kasittele-paikkaus paikkaukset)}))}]}
        sanomasisalto (merge urakka kohteet)]
    (cheshire/encode sanomasisalto)))

(defn muodosta [db urakka-id kohde-id]
  (let [urakka (first (q-urakka/hae-urakan-nimi db {:urakka urakka-id}))
        kohde (first (q-paikkaus/hae-paikkauskohteet db {:harja.domain.paikkaus/id               kohde-id ;; hakuparametrin nimestä huolimatta haku tehdään paikkauskohteen id:llä - haetaan siis yksittäisen paikkauskohteen tiedot
                                                         :harja.domain.muokkaustiedot/poistettu? false}))
        paikkaukset (q-paikkaus/hae-paikkaukset-materiaalit db {:harja.domain.paikkaus/paikkauskohde-id kohde-id
                                                                :harja.domain.muokkaustiedot/poistettu? false})
        json (muodosta-sanoma-json urakka kohde paikkaukset)]

    (if-let [virheet (json/validoi +paikkauksen-vienti+ json)]
      (let [virheviesti (format "Kohdetta ei voi lähettää YHAan. JSON ei ole validi. Validointivirheet: %s" virheet)]
        (log/error virheviesti)
        (throw+ {:type  :invalidi-yha-paikkaus-json
                 :error virheviesti}))
      json)))


