(ns harja.tiedot.urakka.muut-kustannukset
  (:require
   [reagent.core :refer [atom] :as r]
   [harja.loki :refer [log tarkkaile!]]
   [harja.tiedot.urakka :as tiedot-urakka]
   [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot-sanktiot]
   [harja.tiedot.navigaatio :as nav]

   [cljs.core.async :refer [<!]]
   [harja.asiakas.kommunikaatio :as k]
   [harja.pvm :as pvm]
   [clojure.string :as s])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce muiden-kustannusten-tiedot (atom nil))
(defonce kohdistamattomien-sanktioiden-tiedot (atom nil))

(defn grid-tiedot* [mk-tiedot ks-tiedot]
  (let [mk-id #(str "ypt-" (:id %))
        ks-id #(str "sanktio-" (:id %))
        ks->grid (fn [ks] {:hinta  (-> ks :summa)
                           :pvm    (-> ks :laatupoikkeama :aika)
                           :selite (-> ks :tyyppi :nimi)
                           :id     (-> ks :id)})]
    (concat
     (map #(assoc % :muokattava true :id (mk-id %)) mk-tiedot)
     (map #(-> % ks->grid (assoc :muokattava false :id (ks-id %))) ks-tiedot))))

(def grid-tiedot
  (reaction (grid-tiedot* @muiden-kustannusten-tiedot @kohdistamattomien-sanktioiden-tiedot)))

(defn hae-muiden-kustannusten-tiedot! [urakka-id sopimus-id [alkupvm loppupvm]]
  (k/post! :hae-yllapito-toteumat {:urakka urakka-id :sopimus sopimus-id :alkupvm alkupvm :loppupvm loppupvm}))

(defn tallenna-toteuma! [toteuman-tiedot]
  (k/post! :tallenna-yllapito-toteuma toteuman-tiedot))

(defn lataa-tiedot! [urakan-tiedot]
  (log "lataa-tiedot!")
  (go
    (log "lataa-tiedot! / sanktiot")
    (reset! kohdistamattomien-sanktioiden-tiedot
            (filter #(-> % :yllapitokohde :id nil?)
                    (<! (tiedot-sanktiot/hae-urakan-sanktiot
                         (:id urakan-tiedot) (pvm/vuoden-aikavali @tiedot-urakka/valittu-urakan-vuosi)))))
    (log "lataa-tiedot! / muut kustannukset")
    (let [ch (hae-muiden-kustannusten-tiedot!
              (:id urakan-tiedot) (first @tiedot-urakka/valittu-sopimusnumero)
              (pvm/vuoden-aikavali @tiedot-urakka/valittu-urakan-vuosi))
          vastaus (and ch (<! ch))]
      (log "saatiin muut-kustannukset vastaus, laitetaan atomiin:" (pr-str vastaus))
      (reset! muiden-kustannusten-tiedot vastaus))))


(defn tallenna-lomake! [urakka data-atomi grid-data]
  (let [toteuman-avaimet-gridista #(select-keys % [:id :poistettu :toteuma :alkupvm :loppupvm :selite :pvm :hinta])
        [sopimus-id sopimus-nimi] @tiedot-urakka/valittu-sopimusnumero
        ;; tulee vain ypt-id:llä olevia, koska muut eivät ole muokattavia
        palauta-ypt-id #(if (neg? %)
                          nil
                          (-> % (s/replace "ypt-" "") js/parseInt))
        dump #(do (log "tallenna-toteuma saa:" (pr-str %)) %)]
    (go
      (mapv #(-> %
                 toteuman-avaimet-gridista
                 (assoc :urakka (:id urakka)
                        :sopimus sopimus-id)
                 (update :id palauta-ypt-id)
                 dump
                 tallenna-toteuma!)
            grid-data)
      (println "tallennettiin" grid-data)))

  (lataa-tiedot! urakka))
