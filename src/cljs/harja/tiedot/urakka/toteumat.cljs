(ns harja.tiedot.urakka.toteumat
  "Tämä nimiavaruus hallinnoi urakan toteumien tietoja."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.ui.protokollat :refer [Haku hae]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-tehtavat [urakka-id]
  (k/post! :hae-urakan-tehtavat urakka-id))

(defn hae-materiaalit [urakka-id]
  (k/post! :hae-urakan-materiaalit urakka-id))

(defn hae-urakassa-kaytetyt-materiaalit [urakka-id]
  (k/post! :hae-urakassa-kaytetyt-materiaalit urakka-id))

(defn hae-urakan-toteumat [urakka-id sopimus-id [alkupvm loppupvm]]
  (k/post! :urakan-toteumat
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm}))

(defn hae-urakan-tehtavat-toteumittain [urakka-id sopimus-id [alkupvm loppupvm] toimenpidekoodi]
  (k/post! :urakan-tehtavat-toteumittain
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm
            :toimenpidekoodi toimenpidekoodi}))

(defn hae-urakan-tehtavat-toimenpidekoodilla [urakka-id sopimus-id [alkupvm loppupvm] toimenpidekoodi]
  (k/post! :urakan-tehtavat-toimenpidekoodilla
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm
            :toimenpidekoodi toimenpidekoodi}))

(defn hae-urakan-toteuma-paivat [urakka-id sopimus-id [alkupvm loppupvm]]
  (k/post! :urakan-toteuma-paivat
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm}))

(defn tallenna-toteuma [toteuma]
  (k/post! :tallenna-urakan-toteuma toteuma))

(defn hae-urakan-erilliskustannukset [urakka-id [alkupvm loppupvm]]
  (k/post! :urakan-erilliskustannukset
    {:urakka-id urakka-id
     :alkupvm alkupvm
     :loppupvm loppupvm}))

(defn tallenna-erilliskustannus [ek]
  (log "Tiedot tallennea ek" (pr-str ek))
  (k/post! :tallenna-erilliskustannus ek))

(defn tallenna-toteuma-ja-toteumamateriaalit! [toteuma toteumamateriaalit]
  (k/post! :tallenna-toteuma-ja-toteumamateriaalit {:toteuma toteuma :toteumamateriaalit toteumamateriaalit}))
