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
  "Muodostaa tietokannasta haetuista urakka-, paikkauskohde-, paikkaus- ja paikkauksen_materiaali-tiedoista
  paikkauksen-vienti-request-skeeman mukaisen sanoman."
  [kohde-id paikkaukset]
    (cheshire/encode (conj {:poistettavat-paikkauskohteet kohde-id}
                           {:poistettavat-paikkaukset (mapv
                                                        #(:harja.domain.paikkaus/id  %)
                                                        paikkaukset)})))

(defn muodosta
  "Muodostaa YHA:aan lähetettävän json-sanoman, jolla poistetaan paikkauskohteen kaikki paikkaukset YHA:sta.
  Yksittäiset paikkauskohteet poistetaan YHA:sta lähettämällä paikkauskohde YHA:aan uudelleen ilman poistettuja paikkauksia.
  Paikkauskohteen id ei riitä tiedoksi YHA:lle vaan mukana lähetetään myös paikkauskohteen kaikkien paikkausten id:t."
  [db urakka-id kohde-id]
  (let [paikkaukset (q-paikkaus/hae-paikkaukset db {:harja.domain.paikkaus/paikkauskohde-id kohde-id
                                                    :harja.domain.paikkaus/urakka-id urakka-id
                                                                :harja.domain.muokkaustiedot/poistettu? true}) ;; Paikkaukset merkitään ensin poistetuksi Harjassa. Sitten tieto siiryy YHA:aan.
        json (muodosta-sanoma-json kohde-id paikkaukset)]

    (if-let [virheet (json/validoi +paikkauksen-poisto+ json)]
      (let [virheviesti (format "Kohdetta ei voi lähettää YHAan. JSON ei ole validi. Validointivirheet: %s" virheet)]
        (log/error virheviesti)
        (throw+ {:type  :invalidi-yha-paikkaus-json
                 :error virheviesti}))
      json)))






