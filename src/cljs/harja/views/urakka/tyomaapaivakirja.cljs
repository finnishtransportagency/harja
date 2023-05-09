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
            [harja.pvm :as pvm])
  (:require-macros [harja.atom :refer [reaction-writable]]))

(def valittu-hakumuoto (reaction-writable :kaikki))

(defonce haun-valinnat
  {:kaikki "Kaikki"
   :myohastyneet "Myöhästyneet"
   :puuttuvat "Puuttuvat"
   :kommentoidut "Kommentoidut"})

(def toimituksen-tila [{:class "ok" :selitys "Ok"}
                       {:class "myohassa" :selitys "Myöhässä"}
                       {:class "puuttuu" :selitys "Puuttuu"}])

(defn nakyma [e! {:keys [valitut-rivit]}]
  (let [aikavali-atom (atom ())

        ;; Mikäli kirjaus puuttuu, tee oranssi tausta
        ;; TODO
        ;; Lisää tähän tieto mikäli toimitus puuttuu
        solu-fn (fn [arvo _rivi]
                  (when (= (:tila _rivi) 2) "puuttuu-tausta"))

        ;; Toimituksen tila
        toimituksen-tila-fn (fn [arvo _rivi]
                              ;; TODO 
                              ;; Lisää tähän toimituksen tilan tiedot
                              (let [toimitus-tiedot (get toimituksen-tila (:tila arvo))]

                                [:span {:class "paivakirja-toimitus"}
                                 [:div {:class (str "pallura " (:class toimitus-tiedot))}]
                                 [:span {:class "kohta"} (:selitys toimitus-tiedot)]]))]

    [:<>
     [:div.row.filtterit {:style {:padding "16px"}}
      [valinnat/aikavali aikavali-atom {:otsikko "Aikaväli"
                                        :for-teksti "filtteri-aikavali"
                                        :luokka #{"label-ja-aikavali " "ei-tiukkaa-leveytta "}
                                        :ikoni-sisaan? true
                                        :vayla-tyyli? true}]

      [:div {:class "tyomaa-haku-suodatin"}
       [kentat/tee-kentta {:tyyppi :radio-group
                           :vaihtoehdot (into [] (keys haun-valinnat))
                           :vayla-tyyli? true
                           :nayta-rivina? true
                           :valitse-fn #(tiedot/suodata-rivit %)
                           :vaihtoehto-nayta haun-valinnat}
        valittu-hakumuoto]]]

     [grid/grid {:tyhja "Ei Tietoja."
                 :tunniste :id
                 :voi-kumota? false
                 :piilota-toiminnot? true
                 :jarjesta :id}

      [{:otsikko-komp (fn [_ _]
                        [:div {:class "tyopaiva"} "Työpäivä"
                         [:div {:on-click #(println "Painettu: " %)} [ikonit/action-sort-descending]]])
        :tyyppi :komponentti
        :komponentti (fn [arvo rivi]
                       (str (pvm/pvm (:alkupvm arvo))))
        :luokka "bold"
        :leveys 0.5
        :solun-luokka solu-fn}

       {:otsikko "Saapunut"
        :tyyppi :komponentti
        :komponentti (fn [arvo rivi]
                       (str (pvm/pvm-aika-klo (:loppupvm arvo))))
        :leveys 1
        :solun-luokka solu-fn}

       {:otsikko "Viim. muutos"
        :tyyppi :komponentti
        :komponentti (fn [arvo rivi]
                       (str (pvm/pvm-aika-klo (:loppupvm arvo))))
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
      valitut-rivit]]))

(defn tyomaapiavakirja* [e! _]
  (komp/luo
   (komp/sisaan
    #(do
       (e! (tiedot/->HaeTiedot))))
   
   (fn [e! tiedot]
     [:div
      [:h3 {:class "header-yhteiset"} "Työmaapäiväkirja"]
      [nakyma e! tiedot]])))

(defn tyomaapiavakirja [ur]
  [tuck tiedot/tila tyomaapiavakirja*])
