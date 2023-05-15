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
            [harja.ui.nakymasiirrin :as siirrin]
            [harja.pvm :as pvm])
  (:require-macros [harja.atom :refer [reaction<! reaction-writable]]
                   [reagent.ratom :refer [reaction]]))

;; TODO 
;; Lisää tähän oikeat arvot
(defonce haun-valinnat
  {:kaikki "Kaikki (123)"
   :myohastyneet "Myöhästyneet (123)"
   :puuttuvat "Puuttuvat (123)"
   :kommentoidut "Kommentoidut (123)"})

(defonce raportti-avain :tyomaapaivakirja-nakyma)
(def valittu-hakumuoto (reaction-writable :kaikki))

(defn tyomaapaivakirja-listaus [e! {:keys [nayta-rivit valinnat] :as tiedot}]
  (let [aikavali-atom (atom (:aikavali valinnat))

        ;; TODO
        ;; Lisää tähän oikea toiminnallisuus mikäli toimitus puuttuu (tekee "puuttuu-tausta" tekee oranssin solun taustan)
        ;; Tällä hetkellä :tila tulee tyomaapaivakirja.sql joka on randomisti generoitu
        solu-fn (fn [arvo _rivi]
                  (when (= (:tila _rivi) 2) "puuttuu-tausta"))

        ;; Toimituksen tila
        toimituksen-tila-fn (fn [arvo _rivi]
                              ;; TODO 
                              ;; Lisää tähän toimituksen tilan tiedot
                              ;; Tällä hetkellä :tila tulee tyomaapaivakirja.sql joka on randomisti generoitu
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

      [:div.tyomaa-haku-suodatin
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
                        [:div.tyopaiva "Työpäivä"
                         [:div [ikonit/action-sort-descending]]])
        :tyyppi :komponentti
        :komponentti (fn [arvo rivi]
                       (str (pvm/pvm (:alkupvm arvo))))
        :luokka "bold text-nowrap"
        :leveys 0.5
        :solun-luokka solu-fn}

       {:otsikko "Saapunut"
        :tyyppi :komponentti
        :komponentti (fn [arvo rivi]
                       (str (pvm/pvm-aika-klo (:loppupvm arvo))))
        :luokka "text-nowrap"
        :leveys 1
        :solun-luokka solu-fn}

       {:otsikko "Viim. muutos"
        :tyyppi :komponentti
        :komponentti (fn [arvo rivi]
                       (str (pvm/pvm-aika-klo (:loppupvm arvo))))
        :luokka "text-nowrap"
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
     ;; Päiväkirjanäkymän padding
     [:div {:style {:padding "48px 92px 72px"}}
      ;; Takaisin nappi 
      [:div.klikattava {:class "sulje" :on-click #(do
                                                    (e! (tiedot/->PoistaRiviValinta))
                                                    ;; Rullaa sivu ylös TODO: Tähän voi laittaa viimeksi klikatun elementin IDn
                                                    ;; jolloin rullataan käyttäjä listaukseen sille riville mistä viimeksi klikattiin
                                                    (.setTimeout js/window (fn [] (siirrin/kohde-elementti-id "")) 150))}
       [ikonit/harja-icon-navigation-close]]

      ;; Raportin html
      [muodosta-html (assoc-in tiedot [1 :tunniste] raportti-avain)]]
     
     ;; Sticky bar (Edellinen - Seuraava) Tallenna PDF 
     [:div.ala-valinnat-sticky

      [:div.napit.klikattava
       [:span.nuoli
        [ikonit/harja-icon-navigation-previous-page]]
       [:span "Edellinen"]]

      [:div.napit.klikattava
       [:span "Seuraava"]
       [:span.nuoli
        [ikonit/harja-icon-navigation-next-page]]]

      [:div.napit.ei-reunoja.klikattava
       [:span.nuoli
        [ikonit/livicon-download]]
       [:span "Tallenna PDF"]]

      [:div.napit.ei-reunoja.klikattava
       [:span.nuoli
        [ikonit/harja-icon-action-send-email]]
       [:span "Lähetä sähköpostilla"]]

      [:div.napit.ei-reunoja.klikattava {:on-click #(do
                                                      (e! (tiedot/->PoistaRiviValinta))
                                                      ;; Rullaa sivu ylös TODO: Tähän voi laittaa viimeksi klikatun elementin IDn
                                                      ;; jolloin rullataan käyttäjä listaukseen sille riville mistä viimeksi klikattiin
                                                      (.setTimeout js/window (fn [] (siirrin/kohde-elementti-id "")) 150))}
       [:span.nuoli [ikonit/harja-icon-navigation-close]]
       [:span "Sulje"]]]]

    [yleiset/ajax-loader "Ladataan tietoja..."]))

(defn tyomaapiavakirja* [e! _]
  (komp/luo
   (komp/sisaan
    #(do
       (e! (tiedot/->HaeTiedot))))
   
   (fn [e! {:keys [valittu-rivi] :as tiedot}]
     [:div
      (if valittu-rivi
        ;; Jos valittu rivi, näytä päiväkirjanäkymä (tehty raporttien puolelle)
        [suorita-tyomaapaivakirja-raportti e!]

        ;; Mikäli ei valittua riviä, päivitä aikavälivalinta ja näytä listaus
        (do
          (e! (tiedot/->PaivitaAikavali (:aikavali @tiedot/tila)))
          [tyomaapaivakirja-listaus e! tiedot]))])))

(defn tyomaapiavakirja [ur]
  ;; TODO.. Käytä urakka parametria jossain?
  ;; (Esim raportin parametreissa)
  [tuck tiedot/tila tyomaapiavakirja*])
