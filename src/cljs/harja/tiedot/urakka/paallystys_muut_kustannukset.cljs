(ns harja.tiedot.urakka.paallystys-muut-kustannukset
  "Päällystysurakan Muut kustannukset -taulukon tiedot.

  Tässä taulukossa näytetään sanktiot jotka eivät liity mihinkään ylläpitokohteeseen,
  sekä käsin syötetyt vapaamuotoiset muut kustannukset. Sanktioita ei voi muokata tai lisätä.

  Nämä kustannukset lasketaan mukaan kustannusyhteenvetotaulukkoon käyttäen kohteet-reaktiota."
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.loki :refer [log tarkkaile!]]
    [harja.tiedot.urakka :as tiedot-urakka]
    [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot-sanktiot]
    [harja.tiedot.navigaatio :as nav]
    [harja.atom :refer [paivita!]]
    [cljs.core.async :refer [<! pipe chan]]
    [harja.asiakas.kommunikaatio :as k]
    [harja.pvm :as pvm]
    [clojure.string :as s]
    [clojure.set :refer [rename-keys]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom nil)) ;; komp/lippu päivittää tätä

(defn hae-muiden-kustannusten-tiedot! [urakka-id sopimus-id [alkupvm loppupvm]]
  (k/post! :hae-yllapito-toteumat {:urakka urakka-id :sopimus sopimus-id :alkupvm alkupvm :loppupvm loppupvm}))

(def muiden-kustannusten-tiedot
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @tiedot-urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @tiedot-urakka/valittu-sopimusnumero
               nakymassa? @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa? vuosi)
                (hae-muiden-kustannusten-tiedot!
                  valittu-urakka-id valittu-sopimus-id
                  (pvm/vuoden-aikavali vuosi)))))

(def kohdistamattomien-sanktioiden-tiedot
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @tiedot-urakka/valittu-urakan-vuosi
               [valittu-sopimus-id _] @tiedot-urakka/valittu-sopimusnumero
               nakymassa? @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa? vuosi)
                (tiedot-sanktiot/hae-urakan-sanktiot {:urakka-id valittu-urakka-id
                                                      :alku (first (pvm/vuoden-aikavali vuosi))
                                                      :loppu (second (pvm/vuoden-aikavali vuosi))
                                                      :vain-yllapitokohteettomat? true}))))

(defn- grid-tiedot* [muut-kustannukset-tiedot kohdistamattomat-tiedot]
  (let [mk-id #(str "ypt-" (:id %))
        ks-id #(str "sanktio-" (:id %))
        ks->grid (fn [ks] {:hinta (-> ks :summa -) ;; käännetään sanktioiden etumerkki ladattaessa (näitä ei tallenneta, ovat read-only gridissä)
                           :pvm (-> ks :laatupoikkeama :aika)
                           :selite (-> ks :tyyppi :nimi)
                           :id (-> ks :id)})]
    (concat
      (map #(assoc % :muokattava true :id (mk-id %)) muut-kustannukset-tiedot)
      (map #(-> % ks->grid (assoc :muokattava false :id (ks-id %))) kohdistamattomat-tiedot))))

(def grid-tiedot
  (reaction (grid-tiedot* @muiden-kustannusten-tiedot @kohdistamattomien-sanktioiden-tiedot)))

(defn- kohteet* [tiedot]
  (map #(rename-keys % {:hinta :muut-hinta})
       tiedot))

(defonce kohteet (reaction (kohteet* @grid-tiedot)))

(defn tallenna-toteumat! [{:keys [urakka-id sopimus-id toteumat] :as tiedot}]
  (k/post! :tallenna-yllapito-toteumat {:urakka-id urakka-id
                                        :sopimus-id sopimus-id
                                        :toteumat toteumat}))

(defn tallenna-muut-kustannukset! [urakka data-atomi grid-data]
  (let [toteuman-avaimet-gridista #(select-keys % [:id :poistettu :toteuma :alkupvm :loppupvm :selite :pvm :hinta])
        [sopimus-id sopimus-nimi] @tiedot-urakka/valittu-sopimusnumero
        ;; tulee vain ypt-id:llä olevia, koska muut eivät ole muokattavia
        palauta-ypt-id #(if (neg? %)
                          nil
                          (-> % (s/replace "ypt-" "") js/parseInt))
        grid-data-ilman-poistettuja-lisayksia (remove #(and (-> % :id neg?) (-> % :poistettu))
                                                      grid-data)]
    (go
      (let [tallennettavat-toteumat (mapv #(-> %
                                               toteuman-avaimet-gridista
                                               (update :id palauta-ypt-id))
                                          grid-data-ilman-poistettuja-lisayksia)
            vastaus (<! (tallenna-toteumat! {:urakka-id (:id urakka)
                                             :sopimus-id sopimus-id
                                             :toteumat tallennettavat-toteumat}))]
        (log "Tallennettu, haetaan tiedot uudelleen")
        (paivita! muiden-kustannusten-tiedot)
        (paivita! kohdistamattomien-sanktioiden-tiedot)))))
