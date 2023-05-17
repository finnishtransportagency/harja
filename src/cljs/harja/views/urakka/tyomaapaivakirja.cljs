(ns harja.views.urakka.tyomaapaivakirja
  "Työmaapäiväkirja urakka välilehti (listaus)"
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.tyomaapaivakirja :as tiedot]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
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

(def toimituksen-tila [{:class "ok" :selitys "Ok"}
                       {:class "myohassa" :selitys "Myöhässä"}
                       {:class "puuttuu" :selitys "Puuttuu"}])

(defonce raportti-avain :tyomaapaivakirja-nakyma)
(def valittu-hakumuoto (reaction-writable :kaikki))

(defn tyomaapaivakirja-listaus [e! {:keys [nayta-rivit valinnat] :as tiedot}]
  (let [aikavali-atom (atom (:aikavali valinnat))

        ;; TODO
        ;; Lisää tähän oikea toiminnallisuus mikäli toimitus puuttuu (tekee "puuttuu-tausta" tekee oranssin solun taustan)
        ;; Tällä hetkellä :tila tulee tyomaapaivakirja.sql joka on randomisti generoitu
        solu-fn (fn [arvo _]
                  (let [rivin-id (:id arvo)
                        viimeksi-klikattu-id (-> @tiedot/tila :viimeksi-valittu :id)]
                    ;; Kun käyttäjä klikkaa riviä, vaihda tämän rivin väriä
                    ;; ja scrollaa tähän luokkaan kun poistutaan näkymästä takaisin listaukseen
                    (if (= viimeksi-klikattu-id rivin-id)
                      "viimeksi-valittu-tausta"
                      (when (= (:tila arvo) 2) "puuttuu-tausta"))))

        ;; Toimituksen tila
        toimituksen-tila-fn (fn [arvo _]
                              ;; TODO 
                              ;; Lisää tähän toimituksen tilan tiedot
                              ;; Tällä hetkellä :tila tulee tyomaapaivakirja.sql joka on randomisti generoitu
                              (let [toimitus-tiedot (get toimituksen-tila (:tila arvo))]
                                [:span.paivakirja-toimitus
                                 [:div {:class (str "pallura " (:class toimitus-tiedot))}]
                                 [:span.toimituksen-selite (:selitys toimitus-tiedot)]]))]

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

    [:div.tyomaapaivakirja
    [:div.paivakirja-listaus
     [:h1.header-yhteiset "Työmaapäiväkirja"]

     [:div.row.filtterit
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
                 :rivin-luokka solu-fn
                 :rivi-klikattu #(e! (tiedot/->ValitseRivi %))}

      [{:otsikko-komp (fn [_ _]
                        [:div.tyopaiva "Työpäivä"
                         [:div [ikonit/action-sort-descending]]])
        :tyyppi :komponentti
        :komponentti (fn [arvo _]
                       (str (pvm/pvm (:alkupvm arvo))))
        :luokka "semibold text-nowrap"
        :leveys 0.3}

       {:otsikko "Saapunut"
        :tyyppi :komponentti
        :komponentti (fn [arvo _]
                       (str (pvm/pvm-aika-klo (:loppupvm arvo))))
        :luokka "text-nowrap"
        :leveys 0.5}

       {:otsikko "Viim. muutos"
        :tyyppi :komponentti
        :komponentti (fn [arvo _]
                       (str (pvm/pvm-aika-klo (:loppupvm arvo))))
        :luokka "text-nowrap"
        :leveys 0.5}

       {:otsikko "Urakka"
        :tyyppi :string
        :nimi :nimi
        :leveys 1}

       {:otsikko "Toimituksen tila"
        :tyyppi :komponentti
        :komponentti toimituksen-tila-fn
        :leveys 0.5}

       {:otsikko "Kommentit"
        :tyyppi :komponentti
        :komponentti (fn [_ _]
                       ;; TODO
                       ;; Lisää kommenttien määrä tähän
                       [:span
                        [:a.ei-tekstityylia.kommentti-valistys
                         [ikonit/livicon-kommentti]] "1"])
        :leveys 0.5}]
      nayta-rivit]]]))

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
    [:div.tyomaapaivakirja
     ;; Päiväkirjanäkymä 
     [:div.paivakirja-nakyma
      ;; Takaisin nappi 
      [:div.klikattava {:class "sulje" :on-click #(do
                                                    (e! (tiedot/->PoistaRiviValinta))
                                                    ;; Rullataan käyttäjä viimeksi klikatulle riville
                                                    (.setTimeout js/window (fn [] (siirrin/kohde-elementti-luokka "viimeksi-valittu-tausta")) 150))}
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
                                                      ;; Rullataan käyttäjä viimeksi klikatulle riville
                                                      (.setTimeout js/window (fn [] (siirrin/kohde-elementti-luokka "viimeksi-valittu-tausta")) 150))}
       [:span.nuoli [ikonit/harja-icon-navigation-close]]
       [:span "Sulje"]]]]

    [yleiset/ajax-loader "Ladataan tietoja..."]))

(defn tyomaapaivakirja* [e! _]
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

(defn tyomaapaivakirja [ur]
  ;; TODO.. Käytä urakka parametria jossain?
  ;; (Esim raportin parametreissa)
  [tuck tiedot/tila tyomaapaivakirja*])
