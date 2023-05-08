(ns harja.views.urakka.tyomaapaivakirja
  "Työmaapäiväkirja urakka välilehti"
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]))

(def valittu-hakumuoto (atom :kaikki))

(defonce haun-valinnat
  {:kaikki "Kaikki"
   :myohastyneet "Myöhästyneet"
   :puuttuvat "Puuttuvat"
   :kommentoidut "Kommentoidut"})


(defn nakyma [_ tiedot]
  (let [aikavali-atom (atom ())

        ;; Mikäli kirjaus puuttuu, tee oranssi tausta
        ;; TODO
        ;; Lisää tähän tieto mikäli toimitus puuttuu
        solu-fn (fn [arvo rivi]
                  (when (some? (:sopimustyyppi rivi)) "puuttuu-tausta"))

        ;; Toimituksen tila (Ok / Puuttuu / Myöhästynyt)
        toimituksen-tila-fn (fn [_ _]
                              ;; TODO 
                              ;; Lisää tähän toimituksen tilan tiedot
                              (let [random-1 (> (rand-int 3) 1) ;; Mock
                                    random-2 (> (rand-int 2) 1) ;; Mock
                                    toimitus-tiedot (cond
                                                      random-1 {:class "ok" :selitys "Ok"}
                                                      random-2 {:class "myohassa" :selitys "Myöhässä"}
                                                      :else {:class "puuttuu" :selitys "Puuttuu"})]

                                [:span {:class "paivakirja-toimitus"}
                                 [:div {:class (str "pallura " (:class toimitus-tiedot))}]
                                 [:span {:class "kohta"} (:selitys toimitus-tiedot)]]))]

    [:div
     [:div.row.filtterit {:style {:padding "16px"}}
      [valinnat/aikavali aikavali-atom {:otsikko "Aikaväli"
                                        :for-teksti "filtteri-aikavali"
                                        :luokka #{"label-ja-aikavali " "ei-tiukkaa-leveytta "}
                                        :ikoni-sisaan? true
                                        :vayla-tyyli? true}]
      
      ;; TODO
      ;; Ei toimi
      [kentat/tee-kentta {:tyyppi :radio-group
                          :vaihtoehdot (into [] (keys haun-valinnat))
                          :vayla-tyyli? true
                          :nayta-rivina? true
                          :valitse-fn #(do
                                         (println "Valittu hakumuoto: " %)
                                         (reset! valittu-hakumuoto %))
                          :vaihtoehto-nayta haun-valinnat}
       valittu-hakumuoto]]

     [grid/grid {:tyhja "Ei Tietoja."
                 :tunniste :id
                 :voi-kumota? false
                 :piilota-toiminnot? true
                 :jarjesta :id}

      [{:otsikko-komp (fn [_ _]
                        [:div "Työpäivä"
                         [ikonit/action-sort-descending]])
        :tyyppi :komponentti
        :komponentti (fn [arvo rivi]
                       (println "Arvo " arvo "Rivi " rivi)
                       (str (pvm/pvm (:alkupvm arvo))))
        :luokka "bold"
        :leveys 0.5
        :solun-luokka solu-fn}
       {:otsikko "Saapunut"
        :tyyppi :komponentti
        :komponentti (fn [arvo rivi]
                       (println "Arvo " arvo "Rivi " rivi)
                       (str (pvm/pvm-aika-klo (:loppupvm arvo))))
        :leveys 1
        :solun-luokka solu-fn}
       {:otsikko "Viim. muutos"
        :tyyppi :string
        :nimi :nimi
        :leveys 1
        :solun-luokka solu-fn}
       {:otsikko "Urakka"
        :tyyppi :string
        :nimi :nimi
        :leveys 1
        :solun-luokka solu-fn}
       {:otsikko "Toimituksen tila"
        :tyyppi :komponentti
        :komponentti toimituksen-tila-fn
        :leveys 0.5
        :solun-luokka solu-fn}
       {:otsikko "Kommentit"
        :tyyppi :komponentti
        :komponentti (fn [arvo rivi]
                       ;; TODO
                       ;; Lisää kommenttien määrä tähän
                       [:div 
                        [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "1"]])
        :leveys 1
        :solun-luokka solu-fn}]
      tiedot]]))

(defn tyomaapiavakirja* [e! _]
  (komp/luo
   (komp/sisaan
    #(do
       (e! (tiedot/->HaeTiedot))))
   
   (fn [e! {:keys [tiedot]}]
     [:div
      [:h3 {:class "header-yhteiset"} "Työmaapäiväkirja"]
      [nakyma e! tiedot]])))

(defn tyomaapiavakirja [ur]
  [tuck tiedot/tila tyomaapiavakirja*])
