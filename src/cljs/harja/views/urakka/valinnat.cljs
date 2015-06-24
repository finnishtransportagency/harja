(ns harja.views.urakka.valinnat
  "Yleiset urakkaan liittyvät valintakomponentit."
  (:require [harja.tiedot.urakka :as u]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko]]
            [harja.ui.valinnat :as valinnat]))

(defn urakan-sopimus [ur]
  (valinnat/urakan-sopimus ur u/valittu-sopimusnumero u/valitse-sopimusnumero!))

(defn urakan-hoitokausi [ur]
  (valinnat/urakan-hoitokausi ur (u/hoitokaudet ur) u/valittu-hoitokausi u/valitse-hoitokausi!))

(defn hoitokauden-aikavali [ur]
  (valinnat/hoitokauden-aikavali u/valittu-aikavali))

(defn urakan-toimenpide []
  (valinnat/urakan-toimenpide u/urakan-toimenpideinstanssit u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!))

(defn urakan-sopimus-ja-hoitokausi [ur]
  (valinnat/urakan-sopimus-ja-hoitokausi
    ur
    u/valittu-sopimusnumero u/valitse-sopimusnumero!
    (u/hoitokaudet ur) u/valittu-hoitokausi u/valitse-hoitokausi!))

(defn urakan-sopimus-ja-toimenpide [ur]
  (valinnat/urakan-sopimus-ja-toimenpide
    ur
    u/valittu-sopimusnumero u/valitse-sopimusnumero!
    u/urakan-toimenpideinstanssit u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!))

(defn urakan-sopimus-ja-hoitokausi-ja-toimenpide [ur]
  (valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide
    ur
    u/valittu-sopimusnumero u/valitse-sopimusnumero!
    (u/hoitokaudet ur) u/valittu-hoitokausi u/valitse-hoitokausi!
    u/urakan-toimenpideinstanssit u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!))

(defn urakan-hoitokausi-ja-aikavali [ur]
  (valinnat/urakan-hoitokausi-ja-aikavali
    ur
    (u/hoitokaudet ur) u/valittu-hoitokausi u/valitse-hoitokausi!
    u/valittu-aikavali))

(defn urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide [ur]
  (valinnat/urakan-sopimus-ja-hoitokausi-ja-aikavali-ja-toimenpide
    ur
    u/valittu-sopimusnumero u/valitse-sopimusnumero!
    (u/hoitokaudet ur) u/valittu-hoitokausi u/valitse-hoitokausi!
    u/valittu-aikavali
    u/urakan-toimenpideinstanssit u/valittu-toimenpideinstanssi u/valitse-toimenpideinstanssi!))
