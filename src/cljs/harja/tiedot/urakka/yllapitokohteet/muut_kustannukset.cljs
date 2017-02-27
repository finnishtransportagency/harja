(ns harja.tiedot.urakka.yllapitokohteet.muut-kustannukset
  "Päällystyskohteiden Muut kustannukset-taulukon tiedot.

  Tässä taulukossa näytetään sanktiot jotka eivät liity mihinkään ylläpitokohteeseen,
  sekä käsin syötetyt vapaamuotoiset muut kustannukset. Sanktioita ei voi muokata tai lisätä.

  Nämä kustannukset lasketaan mukaan kustannusyhteenvetotaulukkoon käyttäen kohteet-reaktiota."
  (:require
   [reagent.core :refer [atom] :as r]
   [harja.loki :refer [log tarkkaile!]]
   [harja.tiedot.urakka :as tiedot-urakka]
   [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot-sanktiot]
   [harja.tiedot.navigaatio :as nav]

   [cljs.core.async :refer [<!]]
   [harja.asiakas.kommunikaatio :as k]
   [harja.pvm :as pvm]
   [clojure.string :as s]
   [clojure.set :refer [rename-keys]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce muiden-kustannusten-tiedot (atom nil))
(defonce kohdistamattomien-sanktioiden-tiedot (atom nil))

(defn- grid-tiedot* [muut-kustannukset-tiedot kohdistamattomat-tiedot]
  (let [mk-id #(str "ypt-" (:id %))
        ks-id #(str "sanktio-" (:id %))
        ks->grid (fn [ks] {:hinta  (-> ks :summa)
                           :pvm    (-> ks :laatupoikkeama :aika)
                           :selite (-> ks :tyyppi :nimi)
                           :id     (-> ks :id)})]
    (concat
     (map #(assoc % :muokattava true :id (mk-id %)) muut-kustannukset-tiedot)
     (map #(-> % ks->grid (assoc :muokattava false :id (ks-id %))) kohdistamattomat-tiedot))))

(def grid-tiedot
  (reaction (grid-tiedot* @muiden-kustannusten-tiedot @kohdistamattomien-sanktioiden-tiedot)))

(defn- kohteet* [tiedot]
  (map #(rename-keys % {:hinta :muut-hinta})
       tiedot))

(defonce kohteet (reaction (kohteet* @grid-tiedot)))

(defn hae-muiden-kustannusten-tiedot! [urakka-id sopimus-id [alkupvm loppupvm]]
  (k/post! :hae-yllapito-toteumat {:urakka urakka-id :sopimus sopimus-id :alkupvm alkupvm :loppupvm loppupvm}))

(defn tallenna-toteuma! [toteuman-tiedot]
  (k/post! :tallenna-yllapito-toteuma toteuman-tiedot))

(defn lataa-tiedot! [urakan-tiedot]
  (go
    (reset! kohdistamattomien-sanktioiden-tiedot
            (filter #(-> % :yllapitokohde :id nil?)
                    (<! (tiedot-sanktiot/hae-urakan-sanktiot
                         (:id urakan-tiedot) (pvm/vuoden-aikavali @tiedot-urakka/valittu-urakan-vuosi)))))
    (let [ch (hae-muiden-kustannusten-tiedot!
              (:id urakan-tiedot) (first @tiedot-urakka/valittu-sopimusnumero)
              (pvm/vuoden-aikavali @tiedot-urakka/valittu-urakan-vuosi))
          vastaus (and ch (<! ch))]
      (reset! muiden-kustannusten-tiedot vastaus))))


(defn tallenna-lomake! [urakka data-atomi grid-data]
  (let [toteuman-avaimet-gridista #(select-keys % [:id :poistettu :toteuma :alkupvm :loppupvm :selite :pvm :hinta])
        [sopimus-id sopimus-nimi] @tiedot-urakka/valittu-sopimusnumero
        ;; tulee vain ypt-id:llä olevia, koska muut eivät ole muokattavia
        palauta-ypt-id #(if (neg? %)
                          nil
                          (-> % (s/replace "ypt-" "") js/parseInt))
        grid-data-ilman-poistettuja-lisayksia (remove #(and (-> % :id neg?) (-> % :poistettu))
                                                      grid-data)]
    (go
      (mapv #(-> %
                 toteuman-avaimet-gridista
                 (assoc :urakka (:id urakka)
                        :sopimus sopimus-id)
                 (update :id palauta-ypt-id)
                 tallenna-toteuma!)
            grid-data-ilman-poistettuja-lisayksia)))
  (lataa-tiedot! urakka))
