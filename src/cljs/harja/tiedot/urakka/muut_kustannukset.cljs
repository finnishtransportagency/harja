(ns harja.tiedot.urakka.muut-kustannukset
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.loki :refer [log tarkkaile!]]
    [harja.tiedot.urakka :as tiedot-urakka]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))


(defonce lomakedata (atom nil))

(defn grid-tiedot []
  [{:id "foo" :alkupvm #inst "2015-12-12T12:12:12" :kustannus-nimi "ruoka" :selite "naksuja" :muokattava true :hinta 42.50 }
   {:id "zorp" :alkupvm #inst "2014-12-12T12:12:12" :kustannus-nimi "ffff" :selite "uuu" :muokattava true :hinta 44.50 }
   {:id "bar" :alkupvm #inst "2011-12-12T12:12:12" :kustannus-nimi "juoma" :selite "(*)" :muokattava false :hinta 234.00}])

(defn tallenna-toteuma! [toteuman-tiedot]
  (k/post! :tallenna-yllapito-toteuma toteuman-tiedot))

(defn tallenna-lomake [urakka data-atomi grid-data] ;; XXX siirrä tämä tiedot-namespaceen
  ;; kustannukset tallennetaan ilman laskentakohdetta yllapito_toteuma-tauluun,
  ;; -> backin palvelut.yllapito-toteumat/tallenna-yllapito-toteuma

  (let [toteuman-avaimet-gridista #(select-keys % [:toteuma :alkupvm :loppupvm :selite :pvm :hinta])
        [sopimus-id sopimus-nimi] @tiedot-urakka/valittu-sopimusnumero
        dump #(do (log "talenna-toteuma saa:" (pr-str %)) %)]
    (go
      (mapv #(-> % toteuman-avaimet-gridista (assoc :urakka (:id urakka) :sopimus sopimus-id) dump tallenna-toteuma!)
            grid-data)
      (println "tallennettiin lomakedataan" grid-data))))
