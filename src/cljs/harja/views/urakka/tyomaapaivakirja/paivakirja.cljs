(ns harja.views.urakka.tyomaapaivakirja.paivakirja
  "Työmaapäiväkirja urakka välilehti (listaus)"
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.tyomaapaivakirja-tiedot :as tiedot]
            [harja.ui.debug :refer [debug]]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.raportti :refer [muodosta-html]]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.nakymasiirrin :as siirrin]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction-writable]]))

;; TODO 
;; Lisää tähän oikeat arvot
#_ (defonce haun-valinnat
  {:kaikki "Kaikki (123)"
   :myohastyneet "Myöhästyneet (123)"
   :puuttuvat "Puuttuvat (123)"
   :kommentoidut "Kommentoidut (123)"})

(defn haun-valinnat [tiedot]
  (let [myohassa (filter
                   #(= "myohassa" (:tila %))
                   (:tiedot tiedot))
        puuttuu (filter
                  #(= "puuttuu" (:tila %))
                  (:tiedot tiedot))]
    {:kaikki (str "Kaikki (" (count (:tiedot tiedot)) ")")
     :myohastyneet (str "Myöhästyneet (" (count myohassa) ")")
     :puuttuvat (str "Puuttuvat (" (count puuttuu) ")")
     ;:kommentoidut "Kommentoidut (123)" Lisätään kommentoidut sitten, kun niitä voi kommentoida
     }))

#_(def toimituksen-tila [{:class "ok" :selitys "Ok"}
                         {:class "myohassa" :selitys "Myöhässä"}
                         {:class "puuttuu" :selitys "Puuttuu"}])

(def toimituksen-tila {"ok" {:class "ok" :selitys "Ok"}
                       "myohassa" {:class "myohassa" :selitys "Myöhässä"}
                       "puuttuu" {:class "puuttuu" :selitys "Puuttuu"}})

#_(def valittu-hakumuoto (reaction-writable :kaikki))
(def valittu-hakumuoto (atom :kaikki))

(defn tyomaapaivakirja-listaus [e! {:keys [nayta-rivit valinnat] :as app}]
  (let [#_ (js/console.log "tyomaapaivakirja-listaus ::  app: " (pr-str app))
        ;; TODO
        ;; Lisää tähän oikea toiminnallisuus mikäli toimitus puuttuu (tekee "puuttuu-tausta" tekee oranssin solun taustan)
        ;; Tällä hetkellä :tila tulee tyomaapaivakirja.sql joka on randomisti generoitu
        solu-fn (fn [arvo _]
                  (let [rivin-paivamaara (:paivamaara arvo)
                        viimeksi-klikattu-rivi (-> @tiedot/tila :viimeksi-valittu :paivamaara)]
                    ;; Kun käyttäjä klikkaa riviä, vaihda tämän rivin väriä
                    ;; ja scrollaa tähän luokkaan kun poistutaan näkymästä takaisin listaukseen
                    (if (= viimeksi-klikattu-rivi rivin-paivamaara)
                      "viimeksi-valittu-tausta"
                      (when (= (:tila arvo) "puuttuu") "puuttuu-tausta"))))

        ;; Toimituksen tila
        toimituksen-tila-fn (fn [arvo _]
                              (let [toimitus-tiedot (get toimituksen-tila (:tila arvo))]
                                [:span.paivakirja-toimitus
                                 [:div {:class (str "pallura " (:class toimitus-tiedot))}]
                                 [:span.toimituksen-selite (:selitys toimitus-tiedot)]]))]

    [:div.tyomaapaivakirja
     [:div.paivakirja-listaus
      [debug app {:otsikko "TUCK STATE"}]
      [:h1.header-yhteiset "Työmaapäiväkirja"]

      [:div.row.filtterit
       [valinnat/aikavali tiedot/aikavali-atom {:otsikko "Aikaväli"
                                                :for-teksti "filtteri-aikavali"
                                                :luokka #{"label-ja-aikavali " "ei-tiukkaa-leveytta "}
                                                :ikoni-sisaan? true
                                                :vayla-tyyli? true}]

       [:div.tyomaa-haku-suodatin
        [kentat/tee-kentta {:tyyppi :radio-group
                            :vaihtoehdot (into [] (keys (haun-valinnat app)))
                            :vayla-tyyli? true
                            :nayta-rivina? true
                            :vaihtoehto-nayta (haun-valinnat app)
                            :valitse-fn #(e! (tiedot/->PaivitaHakumuoto %))}
         valittu-hakumuoto]]]

      [grid/grid {;:tyhja "Ei Tietoja."
                  :tunniste :paivamaara
                  :voi-kumota? false
                  :piilota-toiminnot? true
                  :jarjesta :paivamaara
                  :mahdollista-rivin-valinta? true
                  :rivin-luokka solu-fn
                  :rivi-klikattu #(e! (tiedot/->ValitseRivi %))}

       [{:otsikko-komp (fn [_ _]
                         [:div.tyopaiva "Työpäivä"
                          [:div [ikonit/action-sort-descending]]])
         :tyyppi :komponentti
         :komponentti (fn [arvo _]
                        (str (pvm/pvm (:paivamaara arvo))))
         :luokka "semibold text-nowrap"
         :leveys 0.3}

        {:otsikko "Saapunut"
         :tyyppi :komponentti
         :komponentti (fn [arvo _]
                        (str (pvm/pvm-aika-klo (:luotu arvo))))
         :luokka "text-nowrap"
         :leveys 0.5}

        {:otsikko "Viim. muutos"
         :tyyppi :komponentti
         :komponentti (fn [arvo _]
                        (str (pvm/pvm-aika-klo (:muokattu arvo))))
         :luokka "text-nowrap"
         :leveys 0.5}

        {:otsikko "Urakka"
         :tyyppi :string
         :nimi :urakka-nimi
         :leveys 1}

        {:otsikko "Toimituksen tila"
         :tyyppi :komponentti
         :komponentti toimituksen-tila-fn
         :leveys 0.5}

        {:otsikko "Kommentit"
         :tyyppi :komponentti
         :komponentti (fn [rivi _]
                        (if (:kommenttien-maara rivi)
                          [:span
                           [:a.ei-tekstityylia.kommentti-valistys
                            [ikonit/livicon-kommentti]] (:kommenttien-maara rivi)]
                          [:span "-"]))
         :leveys 0.5}]
       nayta-rivit]]]))

(defn suorita-tyomaapaivakirja-raportti [e!]
  (if-let [tiedot @tiedot/raportin-tiedot]
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
      [muodosta-html (assoc-in tiedot [1 :tunniste] tiedot/raportti-avain)]]

     ;; Sticky bar (Edellinen - Seuraava) Tallenna PDF 
     [:div.ala-valinnat-fixed

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

(defn tyomaapaivakirja* [e! app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan-ulos
      #(do
         (add-watch tiedot/aikavali-atom
           :aikavali-haku
           (fn [_ _ vanha uusi]
             (when-not (and (pvm/sama-pvm? (first vanha) (first uusi))
                         (pvm/sama-pvm? (second vanha) (second uusi)))
               (e! (tiedot/->PaivitaAikavali {:aikavali uusi})))))

         #_(add-watch valittu-hakumuoto
             :valituu-hakumuoto
             (fn [_ _ vanha uusi]
               (when-not (= vanha uusi)
                 (e! (tiedot/->PaivitaHakumuoto uusi)))))

         (e! (tiedot/->HaeTiedot @nav/valittu-urakka-id)))

      #(do
         #_(remove-watch tiedot/aikavali-atom :aikavali-haku)
         #_(remove-watch valittu-hakumuoto :valituu-hakumuoto)
         (e! (tiedot/->PoistaRiviValinta))))

    (fn [e! {:keys [valittu-rivi] :as app}]
      [:div
       (if valittu-rivi
         ;; Jos valittu rivi, näytä päiväkirjanäkymä (tehty raporttien puolelle)
         [suorita-tyomaapaivakirja-raportti e!]

         ;; Mikäli ei valittua riviä, päivitä aikavälivalinta ja näytä listaus
         (do
           #_(e! (tiedot/->PaivitaAikavali (:aikavali @tiedot/tila)))
           [tyomaapaivakirja-listaus e! app]))])))

(defn tyomaapaivakirja []
  [tuck tiedot/tila tyomaapaivakirja*])
