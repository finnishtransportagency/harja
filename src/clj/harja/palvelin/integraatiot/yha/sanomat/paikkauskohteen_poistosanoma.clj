(ns harja.palvelin.integraatiot.yha.sanomat.paikkauskohteen-poistosanoma
  (:require [harja.tyokalut.xml :as xml]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))




(defn muodosta
  "Muodostaa YHA:aan lähetettävän json-sanoman, jolla poistetaan paikkauskohteen kaikki paikkaukset YHA:sta."
  [db kohde-id])


