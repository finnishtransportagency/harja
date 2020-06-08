(ns harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-poistosanoma
  (:require [cheshire.core :as cheshire]
            [harja.tyokalut.json-validointi :as json]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.kyselyt.paikkaus :as q-paikkaus])
  (:use [slingshot.slingshot :only [throw+]]))



(def +paikkauksen-poisto+ "json/yha/paikkausten-poisto-request.schema.json")

(defn muodosta-sanoma-json
  "Muodostaa tietokannasta haetuista tiedoista paikkauksen-poisto-request-skeeman mukaisen sanoman."
  [kohde-id]
  (cheshire/encode {:poistettavat-paikkauskohteet [kohde-id]}))

(defn muodosta
  "Muodostaa YHA:aan lähetettävän json-sanoman, jolla poistetaan paikkauskohteen kaikki paikkaukset YHA:sta.
  Yksittäiset paikkaukset poistetaan YHA:sta paikkus/paivitys API:n kautta, päivityksinä."
  [db urakka-id kohde-id]
  (let [kohteet (q-paikkaus/hae-paikkauskohteet db {:harja.domain.paikkaus/id               kohde-id
                                                    :harja.domain.paikkaus/urakka-id        urakka-id
                                                    :harja.domain.muokkaustiedot/poistettu? true})
        ei-poistettavaa? (when (empty? kohteet)
                          (format "Paikkauskohteen poistoa ei voi lähettää YHAan. Kohde %s ei kuulu urakkaan %s tai se ei ole poistettu-tilassa." kohde-id urakka-id))
        poisto-json (if ei-poistettavaa?
                      (do (log/error ei-poistettavaa?)
                          (throw+ {:type  :invalidi-yha-paikkaus-kohde
                                   :error ei-poistettavaa?}))
                      (muodosta-sanoma-json kohde-id))]

    (if-let [virheet (json/validoi +paikkauksen-poisto+ poisto-json)]
      (let [virheviesti (format "Kohdetta ei voi lähettää YHAan. JSON ei ole validi. Validointivirheet: %s" virheet)]
        (log/error virheviesti)
        (throw+ {:type  :invalidi-yha-paikkaus-json
                 :error virheviesti}))
      poisto-json)))






