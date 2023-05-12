(ns harja.views.urakka.tyomaapaivakirja
  "Työmaapäiväkirja urakka välilehti"
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.tyomaapaivakirja-nakyma :as nakyma]
            [harja.ui.raportti :refer [muodosta-html]]
            [harja.tiedot.raportit :as raportit]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm])
  (:require-macros [harja.atom :refer [reaction<! reaction-writable]]
                   [reagent.ratom :refer [reaction]]))

;; TODO 
(defonce haun-valinnat
  {:kaikki "Kaikki (0123)"
   :myohastyneet "Myöhästyneet (0123)"
   :puuttuvat "Puuttuvat (0123)"
   :kommentoidut "Kommentoidut (0123)"})

(defonce raportti-avain :tyomaapaivakirja-nakyma)
(def valittu-hakumuoto (reaction-writable :kaikki))

(defn tyomaapaivakirja-listaus [e! {:keys [nayta-rivit valinnat] :as tiedot}]
  (let [aikavali-atom (atom (:aikavali valinnat))
        
        ;; Mikäli kirjaus puuttuu, tee oranssi tausta
        ;; TODO
        ;; Lisää tähän tieto mikäli toimitus puuttuu
        solu-fn (fn [arvo _rivi]
                  (when (= (:tila _rivi) 2) "puuttuu-tausta"))

        ;; Toimituksen tila
        toimituksen-tila-fn (fn [arvo _rivi]
                              ;; TODO 
                              ;; Lisää tähän toimituksen tilan tiedot
                              ;; LESS class sekä selitys->toimituksen-tila
                              (let [toimitus-tiedot (get nakyma/toimituksen-tila (:tila arvo))]
                                [:span {:class "paivakirja-toimitus"}
                                 [:div {:class (str "pallura " (:class toimitus-tiedot))}]
                                 [:span {:class "kohta"} (:selitys toimitus-tiedot)]]))]

    (add-watch aikavali-atom
      :aikavali-haku
      (fn [_ _ vanha uusi]
        (when-not (and (pvm/sama-pvm? (first vanha) (first uusi))
                    (pvm/sama-pvm? (second vanha) (second uusi)))
          (e! (tiedot/->PaivitaAikavali {:aikavali uusi})))))
    
    (add-watch valittu-hakumuoto
      :aikavali-haku
      (fn [_ _ vanha uusi]
        (when-not (= vanha uusi)
          (e! (tiedot/->PaivitaHakumuoto uusi)))))

    [:div {:style {:padding "48px 60px"}}
     [:h1 {:class "header-yhteiset"} "Työmaapäiväkirja"]

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
                           :vaihtoehto-nayta haun-valinnat}
        valittu-hakumuoto]]]

     [grid/grid {:tyhja "Ei Tietoja."
                 :tunniste :id
                 :voi-kumota? false
                 :piilota-toiminnot? true
                 :jarjesta :id
                 :mahdollista-rivin-valinta? true
                 :rivi-klikattu #(e! (tiedot/->ValitseRivi %))}

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
                       [:a
                        [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "1"]])
        :leveys 1
        :solun-luokka solu-fn}]
      nayta-rivit]]))

(defonce raportin-parametrit
  (reaction (let [ur @nav/valittu-urakka]
              (raportit/urakkaraportin-parametrit
                (:id ur)
                raportti-avain
                {:urakkatyyppi (:tyyppi ur)
                 :valittu-rivi (:valittu-rivi @tiedot/tila)
                 }))))

(defonce raportin-tiedot
  (reaction<! [p @raportin-parametrit]
    {:nil-kun-haku-kaynnissa? true}
    (when p
      (raportit/suorita-raportti p))))

(defn suorita-tyomaapaivakirja-raportti [e!]
  (if-let [tiedot @raportin-tiedot]
    [:<>
     ;; Takaisin nappi 
     [napit/takaisin "Takaisin" #(e! (tiedot/->PoistaRiviValinta)) {:luokka "nappi-reunaton"}]
     ;; Päiväkirjanäkymän padding
     [:div {:style {:padding "48px 92px 72px"}}
      ;; Raportin html
      [muodosta-html (assoc-in tiedot [1 :tunniste] raportti-avain)]]]
    [yleiset/ajax-loader "Ladataan tietoja..."]))

(defn tyomaapiavakirja* [e! _]
  (komp/luo
   (komp/sisaan
    #(do
       (e! (tiedot/->HaeTiedot))))
   
   (fn [e! {:keys [valittu-rivi] :as tiedot}]
     [:div
      (if valittu-rivi
        [suorita-tyomaapaivakirja-raportti e!]
        [tyomaapaivakirja-listaus e! tiedot])])))

(defn tyomaapiavakirja [ur]
  [tuck tiedot/tila tyomaapiavakirja*])
