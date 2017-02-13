(ns harja.views.urakka.muut-kustannukset
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.toteumat.tiemerkinta-muut-tyot :refer [tallenna-toteuma hae-toteuma]]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce kustannukset-atom (atom {}))

;; Ylläpitokohteiden sarakkeiden leveydet
(def kustannus-nimi-leveys 5)
(def kustannus-summa-leveys 3)

;; muokkaus yllapitokohteet-viewin pohjalla wip

(def grid-opts {:otsikko "Muut kustannukset"
                :tyhja (if (nil? @kustannukset-atom) [ajax-loader "Haetaan kustannuksia..."] "Ei kustannuksia")
                :muutos (fn [grid]
                          #(log "muut-kustannukset: muutos kutsuttu"))
                :voi-lisata? true
                :voi-muokata-rivia? :muokattava
                :esta-poistaminen? (fn [rivi] (or (not (nil? (:paallystysilmoitus-id rivi))) ;; <- tahan tsekkaus onko kohdistamaton sanktio vai suoraan syötetty?
                                                  (not (nil? (:paikkausilmoitus-id rivi)))))
                :esta-poistaminen-tooltip
                (fn [_] "Kohteeseen liittymättömästä sanktiosta johtuvaa kustannusta ei voi poistaa.")})

(def grid-skeema
  (into [] (concat  [{:otsikko "Kustannus" :nimi :kustannus-nimi
                      :tyyppi :string :leveys kustannus-nimi-leveys
                      :validoi [[:uniikki "Sama kohdenumero voi esiintyä vain kerran."]]}
                     {:otsikko "Hinta" :nimi :hinta
                      :tyyppi :string :leveys kustannus-summa-leveys}]
                    [])))

(defn grid-tiedot []
  [{:id "foo" :alkupvm #inst "2015-12-12T12:12:12" :kustannus-nimi "ruoka" :muokattava true :hinta 42.50 }
   {:id "zorp" :alkupvm #inst "2014-12-12T12:12:12" :kustannus-nimi "ffff" :muokattava true :hinta 44.50 }
   {:id "bar" :alkupvm #inst "2011-12-12T12:12:12" :kustannus-nimi "juoma" :muokattava false :hinta 234.00}])

(defn tallenna-lomake [urakka data-atomi grid-data] ;; XXX siirrä tämä tiedot-namespaceen
  ;; kustannukset tallennetaan ilman laskentakohdetta yllapito_toteuma-tauluun,
  ;; -> backin palvelut.yllapito-toteumat/tallenna-yllapito-toteuma

  (let [toteuman-avaimet-gridista #(select-keys % [:toteuma :alkupvm :loppupvm :uusi-laskentakohde])]
    (go
      (mapv #(-> % toteuman-avaimet-gridista (assoc :urakka (:id urakka) :sopimus @tiedot-urakka/valittu-sopimusnumero) (tallenna-toteuma []))
            grid-data)
      (println "tallennettiin lomakedataan" grid-data))))

(defn muut-kustannukset [urakka muut-kustannukset-lomakedata]
  (komp/luo
   (fn [urakka]
     [:div.muut-kustannukset
      [grid/grid (assoc grid-opts :tallenna #(tallenna-lomake urakka muut-kustannukset-lomakedata %)) grid-skeema (grid-tiedot)]])))
