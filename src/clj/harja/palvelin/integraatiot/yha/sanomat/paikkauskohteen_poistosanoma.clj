(ns harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-poistosanoma
  (:require [cheshire.core :as cheshire]
            [harja.tyokalut.json-validointi :as json]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))



(def +paikkauksen-poisto+ "json/yha/paikkausten-poisto-request.schema.json")

(defn muodosta-sanoma-json
  "Muodostaa tietokannasta haetuista urakka-, paikkauskohde-, paikkaus- ja paikkauksen_materiaali-tiedoista
  paikkauksen-vienti-request-skeeman mukaisen sanoman."
  [kohde-idt paikkaus-idt]
    (cheshire/encode (conj {:poistettavat-paikkauskohteet kohde-idt}
                           {:poistettavat-paikkaukset paikkaus-idt})))

;(defn muodosta
;  "Muodostaa YHA:aan lähetettävän json-sanoman, jolla poistetaan paikkauskohteen kaikki paikkaukset YHA:sta.
;  Yksittäiset paikkauskohteet poistetaan YHA:sta lähettämällä paikkauskohde YHA:aan uudelleen ilman poistettuja paikkauksia.
;  Paikkauskohteen id ei riitä tiedoksi YHA:lle vaan mukana lähetetään myös paikkauskohteen kaikkien paikkausten id:t."
;  [db urakka-id kohde-id]
;  (let [urakka (first (q-urakka/hae-urakan-nimi db {:urakka urakka-id}))
;        kohde (first (q-paikkaus/hae-paikkauskohteet db {:harja.domain.paikkaus/id               kohde-id ;; hakuparametrin nimestä huolimatta haku tehdään paikkauskohteen id:llä - haetaan siis yksittäisen paikkauskohteen tiedot
;                                                         :harja.domain.muokkaustiedot/poistettu? false}))
;        paikkaukset (q-paikkaus/hae-paikkaukset-materiaalit db {:harja.domain.paikkaus/paikkauskohde-id kohde-id
;                                                                :harja.domain.muokkaustiedot/poistettu? false})
;        json (muodosta-sanoma-json urakka kohde paikkaukset)]
;
;    (if-let [virheet (json/validoi +paikkauksen-vienti+ json)]
;      (let [virheviesti (format "Kohdetta ei voi lähettää YHAan. JSON ei ole validi. Validointivirheet: %s" virheet)]
;        (log/error virheviesti)
;        (throw+ {:type  :invalidi-yha-paikkaus-json
;                 :error virheviesti}))
;      json)))
;





