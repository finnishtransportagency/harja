(ns harja.kyselyt.toimenpideinstanssit
  "Toimenpideinstansseihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/toimenpideinstanssit.sql"
  {:positional? true})

(declare hae-urakan-toimenpideinstanssi-toimenpidekoodilla onko-tuotu-samposta onko-urakalla-toimenpide
  sallitaanko-urakassa-toimenpidekoodille-useita-toimenpideinstansseja hae-hoidon-maksuerattomat-toimenpideistanssit
  luo-yllapidon-toimenpideinstanssi<! paivita-urakka-sampoidlla! hae-id-sampoidlla luo-toimenpideinstanssi<!
  paivita-toimenpideinstanssi!)

(defn onko-tuotu-samposta? [db sampo_toimenpidekoodi sampo-toimenpide-id urakka_sampoid]
  (:exists (first (onko-tuotu-samposta db sampo_toimenpidekoodi sampo-toimenpide-id urakka_sampoid))))

(defn onko-urakalla-toimenpide? [db urakkaid toimenpide]
  (:exists (first (onko-urakalla-toimenpide db urakkaid toimenpide))))

(defn sallitaanko-urakassa-toimenpidekoodille-useita-toimenpideinstansseja?
  "Jos urakkaa ei ole tuotu Harjaan, toimenpideinstansseja saa olla vain yksi.
   Jos urakka on tuotu ja se on sopivaa kyselyssä määriteltyä urakkatyyppiä (kuten päällystys), useampi tpi on sallittu."
  [db sampo-urakka-id]
  (:exists (first (sallitaanko-urakassa-toimenpidekoodille-useita-toimenpideinstansseja db sampo-urakka-id))))
