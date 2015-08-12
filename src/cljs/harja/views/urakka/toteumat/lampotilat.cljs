(ns harja.views.urakka.toteumat.lampotilat
  "Urakan toteumat: lämpötilat"
  (:require [reagent.core :refer [atom wrap]]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.tiedot.urakka.lampotilat :as lampotilat]
            [cljs.core.async :refer [<!]]
            [harja.ui.komponentti :as komp]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.urakka :as u]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tiedot.istunto :as oikeudet]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.napit :refer [palvelinkutsu-nappi]]
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.ikonit :as ikonit]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.roolit :as roolit]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

;; Nykyiset lämpötilat hoitokausittain, mäppäys [alku loppu] => {:id ... :keskilampo ... :pitkalampo ...}
(defonce nykyiset-lampotilat (reaction<! [ur @nav/valittu-urakka]
                                         (when ur
                                           (go
                                             (let [lampotilat (<! (lampotilat/hae-urakan-lampotilat (:id ur)))]
                                               (log "LAMPOTILAT HAETTU: " (pr-str lampotilat))
                                               (zipmap (map (juxt :alkupvm :loppupvm) lampotilat)
                                                       lampotilat))))))

(defonce muokatut-lampotilat (reaction @nykyiset-lampotilat))


(defn tallenna-muutos [hoitokausi tulos]
  (let [uusi-lampo tulos]
    (when-not (k/virhe? uusi-lampo)
      (swap! nykyiset-lampotilat
             assoc hoitokausi tulos))))


(defn lampotila-lomake
  [urakka lampotilat]
  (let [saa-muokata?  (roolit/rooli-urakassa? roolit/urakanvalvoja
                                              (:id urakka))]
    
    (fn [urakka lampotilat]
      (let [hoitokausi @u/valittu-hoitokausi]
        [lomake {:luokka   :horizontal
                 :muokkaa! (fn [uusi]
                             (reset! lampotilat uusi))
                 :footer-fn (fn [virheet _]
                              (log "virheet: " (pr-str virheet) ", muokattu? " (::muokattu @lampotilat))
                              (if saa-muokata?
                                [:div.form-group
                                 [:div.col-md-4
                                  [napit/palvelinkutsu-nappi
                                   "Tallenna"
                                   #(lampotilat/tallenna-lampotilat!
                                     (:id @lampotilat)
                                     (:id urakka)
                                     hoitokausi
                                     (:keskilampo @lampotilat)
                                     (:pitkalampo @lampotilat))
                                   {:luokka       "nappi-ensisijainen"
                                    :disabled     (or (not= true (::muokattu @lampotilat))
                                                      (not (empty? virheet)))
                                    :ikoni        (ikonit/tallenna)
                                    :kun-onnistuu #(do
                                                     (viesti/nayta! "Tallentaminen onnistui" :success 1500)
                                                     (tallenna-muutos hoitokausi %))}]
                                  
                                  [:button.nappi-toissijainen {:name "peruuta"
                                                             :disabled (= @muokatut-lampotilat @nykyiset-lampotilat)
                                                             :on-click #(do
                                                                          (.preventDefault %)
                                                                          (reset! muokatut-lampotilat @nykyiset-lampotilat))}
                                   (ikonit/remove) " Kumoa"]]]))
                 }
         [{:otsikko "Keskilämpötila" :nimi :keskilampo :tyyppi :numero :leveys-col 2
           :validoi [[:lampotila]]}
          {:otsikko "Pitkän aikavälin keskilämpötila" :nimi :pitkalampo :tyyppi :numero :leveys-col 2
           :validoi [[:lampotila]]}]
         @lampotilat]))))

(defn lampotilat [ur]
  (let [urakka (atom nil)
        aseta-urakka (fn [ur] (reset! urakka ur))]

    (aseta-urakka ur)
    
    (komp/luo
     {:component-will-receive-props
      (fn [_ & [_ ur]]
        (aseta-urakka ur))}
     
     (fn [ur]
       (let [hoitokausi @u/valittu-hoitokausi]
         (if (nil? @nykyiset-lampotilat)
           [ajax-loader]
           [:span
            [valinnat/urakan-hoitokausi ur]
            (when (roolit/rooli-urakassa? roolit/urakanvalvoja
                                          (:id @urakka))
              [napit/palvelinkutsu-nappi "Hae ilmatieteenlaitokselta"
               #(lampotilat/hae-lampotilat-ilmatieteenlaitokselta (:id @urakka) (pvm/vuosi (first hoitokausi)))
               {:ikoni true
                :kun-onnistuu (fn [{:keys [keskilampotila ilmastollinen-keskiarvo]}]
                                (if (and keskilampotila ilmastollinen-keskiarvo)
                                  (swap! muokatut-lampotilat update-in [hoitokausi]
                                         (fn [lampotilat]
                                           (assoc lampotilat
                                                  :keskilampo keskilampotila
                                                  :pitkalampo ilmastollinen-keskiarvo
                                                  ::muokattu true)))
                                  (viesti/nayta! (str "Talvikauden "
                                                      (pvm/vuosi (first hoitokausi)) " \u2014 " (pvm/vuosi (second hoitokausi))
                                                      " lämpötiloja ei löytynyt.") :warning)))}])
            
            (if @u/valittu-hoitokausi
              [lampotila-lomake
               @urakka
               (wrap (get @muokatut-lampotilat @u/valittu-hoitokausi)
                     #(swap! muokatut-lampotilat
                             assoc @u/valittu-hoitokausi
                             (assoc % ::muokattu true)))])]))))))
