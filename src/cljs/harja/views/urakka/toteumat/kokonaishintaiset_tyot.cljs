(ns harja.views.urakka.toteumat.kokonaishintaiset-tyot
  "Urakan 'Toteumat' välilehden 'Kokonaishintaiset työt' osio"
  (:require [reagent.core :refer [atom] :as r]

            [harja.atom :refer [paivita!] :refer-macros [reaction<!]]
            [harja.domain.roolit :as roolit]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.modal :refer [modal] :as modal]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                                  livi-pudotusvalikko +korostuksen-kesto+ kuvaus-ja-avainarvopareja]]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.indeksit :as i]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.urakka.muut-tyot :as muut-tyot]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.views.urakka.valinnat :as valinnat]

            [harja.ui.lomake :as lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [cljs.core.async :refer [<! timeout]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.views.kartta :as kartta])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defonce valittu-toteuma (atom nil))

(def korostettavan-rivin-id (atom nil))

(defn korosta-rivia
  ([id] (korosta-rivia id +korostuksen-kesto+))
  ([id kesto]
   (reset! korostettavan-rivin-id id)
   (go (<! (timeout kesto))
       (reset! korostettavan-rivin-id nil))))

(def +rivin-luokka+ "korosta")

(defn aseta-rivin-luokka [korostettavan-rivin-toteuman-id]
  (fn [rivi]
    (if (= korostettavan-rivin-toteuman-id (get-in rivi [:toteuma :id]))
      +rivin-luokka+
      "")))

(defn tee-tyorivien-reaktio [valitut-kokonaishintaiset-tyot]
  (reaction
    (let [muutoshintaiset-tyot @u/muutoshintaiset-tyot
          valitut-muut-tyot @valitut-kokonaishintaiset-tyot]
      (map (fn [muu-tyo]
             (let [muutoshintainen-tyo
                   (first (filter (fn [muutoshinta]
                                    (= (:tehtava muutoshinta)
                                       (get-in muu-tyo [:tehtava :toimenpidekoodi]))) muutoshintaiset-tyot))
                   yksikkohinta (:yksikkohinta muutoshintainen-tyo)]
               (assoc muu-tyo
                 :hinnoittelu (if (get-in muu-tyo [:tehtava :paivanhinta]) :paivanhinta :yksikkohinta)
                 :yksikko (:yksikko muutoshintainen-tyo)
                 :yksikkohinta yksikkohinta
                 :yksikkohinta-suunniteltu? yksikkohinta)))
           valitut-muut-tyot))))

(defn tee-taulukko [urakka valitut-kokonaishintaiset-tyot tyorivit]
  (komp/luo
    (fn []
      (let [aseta-rivin-luokka (aseta-rivin-luokka @korostettavan-rivin-id)]
        [:div.kokonaishintaiset-toteumat
         [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide urakka]
         [grid/grid
          {:otsikko       (str "Toteutuneet kokonaishintaiset työt ")
           :tyhja         (if (nil? @valitut-kokonaishintaiset-tyot)
                            [ajax-loader "Toteumia haetaan..."]
                            "Ei toteumia saatavilla.")
           :rivi-klikattu #(reset! valittu-toteuma %)
           :rivin-luokka  #(aseta-rivin-luokka %)
           :tunniste      #(get-in % [:toteuma :id])}
          [{:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm :nimi :alkanut :leveys "10%"}
           {:otsikko "Tehtävä" :tyyppi :string :nimi :tehtavan_nimi
            :hae     #(get-in % [:tehtava :nimi]) :leveys "25%"}
           {:otsikko "Määrä" :pakollinen? true :tyyppi :string :nimi :maara
            :hae     #(if (get-in % [:tehtava :maara]) (get-in % [:tehtava :maara]) "-")
            :leveys  "10%"}
           {:otsikko "Yksikkö"
            :nimi    :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "10%"}
           {:otsikko "Lähde"
            :nimi    :lahde
            :hae     #(if (:jarjestelmasta %) "Urak. järj." "Harja")
            :tyyppi  :string :muokattava? (constantly false) :leveys "10%"}]
          @tyorivit]]))))

(defn tee-valittujen-rivien-reaktio []
  (reaction (let [toimenpideinstanssi @u/valittu-toimenpideinstanssi
                  muut-tyot-hoitokaudella @u/muut-tyot-hoitokaudella]
              (reverse (sort-by :alkanut (filter #(= (get-in % [:tehtava :emo])
                                                     (:id toimenpideinstanssi))
                                                 muut-tyot-hoitokaudella))))))

(defn kokonaishintaisten-toteumien-listaus
  "Kokonaishintaisten töiden toteumat"
  []
  (let [urakka @nav/valittu-urakka
        valitut-kokonaishintaiset-tyot (tee-valittujen-rivien-reaktio)
        tyorivit (tee-tyorivien-reaktio valitut-kokonaishintaiset-tyot)]
    (tee-taulukko urakka valitut-kokonaishintaiset-tyot tyorivit)))

(defn kokonaishintaiset-toteumat []
  (fn []
    [:span
     [kartta/kartan-paikka]
     [kokonaishintaisten-toteumien-listaus]
     #_(if @valittu-toteuma
         [kokonaishintaisten-toteumien-listaus])]))
