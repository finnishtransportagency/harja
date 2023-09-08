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
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]
            [harja.ui.modal :as modal]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn haun-valinnat [tiedot]
  (let [myohassa (filter
                   #(= "myohassa" (:tila %))
                   (:tiedot tiedot))
        puuttuu (filter
                  #(= "puuttuu" (:tila %))
                  (:tiedot tiedot))
        kommentoitu (filter
                      #(> (:kommenttien-maara %) 0)
                      (:tiedot tiedot))]
    
    {:kaikki (str "Kaikki (" (count (:tiedot tiedot)) ")")
     :myohastyneet (str "Myöhästyneet (" (count myohassa) ")")
     :puuttuvat (str "Puuttuvat (" (count puuttuu) ")")
     :kommentoidut (str "Kommentoidut (" (count kommentoitu) ")")}))

(def toimituksen-tila {"ok" {:class "ok" :selitys "Ok"}
                       "myohassa" {:class "myohassa" :selitys "Myöhässä"}
                       "puuttuu" {:class "puuttuu" :selitys "Puuttuu"}})

(defn tyomaapaivakirja-listaus [e! {:keys [nayta-rivit valinnat] :as app}]
  (println "nayta rivit " (type nayta-rivit))
  (let [hakumuoto (atom (:hakumuoto valinnat))
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
       [valinnat/aikavali
        tiedot/aikavali-atom
        {:otsikko "Aikaväli"
         :for-teksti "filtteri-aikavali"
         :luokka #{"label-ja-aikavali " "ei-tiukkaa-leveytta "}
         :ikoni-sisaan? true
         :vayla-tyyli? true
         :aikavalin-rajoitus [6 :kuukausi]}]

       [:div.width-half
        [kentat/tee-kentta {:tyyppi :radio-group
                            :vaihtoehdot (into [] (keys (haun-valinnat app)))
                            :vayla-tyyli? true
                            :nayta-rivina? true
                            :vaihtoehto-nayta (haun-valinnat app)
                            :valitse-fn #(e! (tiedot/->PaivitaHakumuoto %))}
         hakumuoto]]]

      [grid/grid {:tyhja "Työmaapäiväkirjoja ei ole valitulle aikavälille."
                  :tunniste :paivamaara
                  :sivuta grid/vakiosivutus
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
                        (if (:luotu arvo)
                          (str (pvm/pvm-aika-klo (:luotu arvo)))
                          "-"))
         :luokka "text-nowrap"
         :leveys 0.5}

        {:otsikko "Viim. muutos"
         :tyyppi :komponentti
         :komponentti (fn [arvo _]
                        (if (:muokattu arvo)
                          (str (pvm/pvm-aika-klo (:muokattu arvo)))
                          "-"))
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

(defn nayta-muutoshistoria
  "Tuck statesta löytyy muutoshistoria jossa mäpätty: ( {vers1 {:info , :toiminto <>, :vanhat <>, :uudet <>}} ... )
  :info : Mistä taulusta on tehty mitäkin, esim '<Lisätty> <säätietoja>' (toiminto, info)
  :toiminto : 'muutettu' | 'poistettu' | 'lisatty' 
  :vanhat : Vanhan version arvot 
  :uudet : Nykyisen version arvot"
  [e! {:keys [muutoshistoria historiarivi-auki]}]

  ;; Tehty kustom modali koska Harjan nayta-modal herjasi React atom virheitä ilman kummempia tietoja enkä saanut korjattua
  [:div.muutoshistoria-modal {:on-click(fn [e]
                                         ;; Jos klikattiin elementin ulkopuolelle, suljetaan modali
                                         (when (= (-> e .-target .-classList str) "muutoshistoria-modal")
                                           (e! (tiedot/->MuutoshistoriaAuki))))}
   [:div.muutoshistoria-dialog
    [:div.muutoshistoria-otsikko "Versiohistoria"]

    [:div.muutoshistoria
      ;; Loopataan muutokset ja tehdään rivit, reverse jotta uusimmat muutokset ylimpänä
     (for* [[indeksi versiomuutokset] (map-indexed vector (reverse muutoshistoria))]

       (let [kentta-auki? (get-in historiarivi-auki [indeksi])

             _ (println "{" indeksi "} [" kentta-auki? "] X: " versiomuutokset)
             _ (println "\n")
             solu-fn #(str "nakyma-valkoinen-solu muutoshistoria-kentan-nimi")

             fn-hae-arvo (fn [kentta uusi?]

                           (let [avain (if uusi? :uudet :vanhat)]
                             (if (nil? (avain kentta))
                               "(tyhjä)"
                               (cond
                                 (= (:info kentta) "kalustoja")
                                 (str
                                   "Työkoneiden määrä: " (get-in kentta [avain :tyokoneiden_lkm]) ", "
                                   "Lisäkaluston määrä: " (get-in kentta [avain :lisakaluston_lkm]))

                                 (= (:info kentta) "sääasematietoja")
                                 (str
                                   "Tunniste: " (get-in kentta [avain :aseman_tunniste]) ", "
                                   "Tien lämpötila: " (get-in kentta [avain :tien_lampotila]) ", "
                                   "Ilman lämpötila: " (get-in kentta [avain :ilman_lampotila]) ", "
                                   "Tuuli: " (get-in kentta [avain :keskituuli]) " m/s, "
                                   "S-Sum: " (get-in kentta [avain :sadesumma]) " mm")

                                 :else "Ei tietoja"))))]

         [:div.muutoshistoria-grid
          [:span.klikattava {:on-click #(e! (tiedot/->ValitseHistoriarivi indeksi))} (ikonit/harja-icon-navigation-down)]
          [:span.muutos-pvm "11.10.2022 08:10 Lisätty rekka-kolari"]

          [:div {:style {:display (if kentta-auki? "block" "none")}}
           [grid/grid {:tyhja "Työmaapäiväkirjoja ei ole valitulle aikavälille."
                       :tunniste :id
                       :sivuta grid/vakiosivutus
                       :voi-kumota? false
                       :piilota-toiminnot? true
                       :jarjesta :paivamaara
                       :mahdollista-rivin-valinta? false
                       :piilota-muokkaus? true}

            [{:otsikko "Kentän nimi"
              :tyyppi :string
              :nimi :info
              :solun-luokka solu-fn
              :leveys 1}
             {:otsikko "Vanha arvo"
              :tyyppi :komponentti
              :komponentti (fn [arvo _] (fn-hae-arvo arvo false))
              :solun-luokka solu-fn
              :leveys 1}
             {:otsikko "Uusi arvo"
              :tyyppi :komponentti
              :komponentti (fn [arvo _] (fn-hae-arvo arvo true))
              :solun-luokka solu-fn
              :leveys 1}]
            versiomuutokset]]]))]]])

(defn paivakirjan-header [e! {:keys [valittu-rivi historiatiedot-auki] :as app}]
  (when valittu-rivi
    [:<>

     (when historiatiedot-auki
       (nayta-muutoshistoria e! app))

     [:h3.header-yhteiset (:urakka-nimi valittu-rivi)]
     [:h1.header-yhteiset (str "Työmaapäiväkirja " (pvm/pvm (:paivamaara valittu-rivi)))]

     [:div.nakyma-otsikko-tiedot

      [:span (str "Saapunut " (pvm/pvm-aika-klo (:luotu valittu-rivi)))]
      (when (:muokattu valittu-rivi)
        [:span (str "Päivitetty " (pvm/pvm-aika-klo (:muokattu valittu-rivi)))])
      [:span (str "Versio " (:versio valittu-rivi))]

      [:a.klikattava {:on-click #(do
                                   (e! (tiedot/->HaeMuutoshistoria))
                                   (e! (tiedot/->MuutoshistoriaAuki)))} "Näytä muutoshistoria"]

      [:span.paivakirja-toimitus
       [:div {:class (str "pallura " (:tila valittu-rivi))}]
       [:span.toimituksen-selite (if (= "myohassa" (:tila valittu-rivi))
                                   "Myöhässä"
                                   "Ok")]]

      ;; Kommentti- nappi scrollaa alas kommentteihin
      [:a.klikattava {:on-click #(tiedot/siirry-elementin-id "Kommentit" 150)}
       [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) (if (= (:kommenttien-maara valittu-rivi) 1)
                                                            (str (:kommenttien-maara valittu-rivi) " kommentti")
                                                            (str (:kommenttien-maara valittu-rivi) " kommenttia"))]]]
     [:hr]]))

(defn- paivakirjan-kommentit [e! valittu-rivi]
  (let [toggle-kentat (fn [nayta piilota]
                        ;; Tämä tehtiin alunperin raporttien puolelle jonka takia käytetään DOM manipulaatiota eikä tuckin tila atomia
                        ;; Toggleaa kun toinen element näytetään niin toinen piiloitetaan
                        ;; Parametrina elementtien ID:t, resetoi aina kommenttikentän (text-area)
                        (let [nayta-element (.-classList (.getElementById js/document nayta))
                              kommentti-element (.getElementById js/document "kommentti-teksti")
                              piilota-element (.-classList (.getElementById js/document piilota))]
                          (set! (.-value kommentti-element) "")
                          (.add piilota-element "piilota-kentta")
                          (.remove nayta-element "piilota-kentta")))
        
        kommentit (:kommentit valittu-rivi)]

    ;; Käyttäjien kommentit
    [:div#Kommentit.row.filtterit.kommentit-valistys
     [:h2 "Kommentit"]
     (for [{:keys [id luotu kommentti etunimi sukunimi luoja]} kommentit]
       ^{:key id}
       [:span
        [:div.alarivi-tiedot
         [:span (str (pvm/pvm-aika luotu))]
         [:span (str etunimi " " sukunimi)]]
        
        [:div.kommentti
         [:h1.tieto-rivi kommentti]
         [:span.klikattava.kommentti-poista
          {:on-click #(do
                       (e! (tiedot/->PoistaKommentti
                            {:id id :tyomaapaivakirja_id (:tyomaapaivakirja_id valittu-rivi) :luoja luoja}))
                        (tiedot/scrollaa-kommentteihin))}
          
          (ikonit/action-delete)]]])

     ;; TODO Tällä tehdään muutoshistoria kommentti
     ;; Muutoshistoria tiedot
     #_[:div.alarivi-tiedot
        [:span "11.10.2022 07:45"]
        [:span "Tauno Työnjohtaja"]
        [:span.muutos-info "Jälkikäteismerkintä urakoitsijajärjestelmästä"]]

     ;; Muutoshistoria
     #_[:div.kommentti.muutos
        [:h1.tieto-rivi "Työmaapäiväkirja päivitetty 11.10.2022 08:10: lisätty rekka-kolari."]
        [:a.klikattava.info-rivi "Näytä muutoshistoria"]]

     [:div#kommentti-lisaa
      [:a.klikattava {:on-click #(do 
                                   (toggle-kentat "kommentti-area" "kommentti-lisaa")
                                   (tiedot/siirry-elementin-id "kommentti-area" 150))}

       [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) "Lisää kommentti"]]]

     [:span#kommentti-area.kentta-text.piilota-kentta
      [:span "Lisää kommentti"]
      [:textarea#kommentti-teksti]
      [:span "Myös työmaapäiväkirjaan kirjoitetut kommentit tallentuvat PDF:ään ja ne arkistoidaan muun työpäiväkirjan mukana."]

      [:div
       [:span
        [napit/tallenna "Tallenna"
         (fn []
           (let [kirjoitettu-teksti (-> (.getElementById js/document "kommentti-teksti") .-value)]
             (toggle-kentat "kommentti-lisaa" "kommentti-area")
             (e! (tiedot/->TallennaKommentti kirjoitettu-teksti))
             (tiedot/scrollaa-kommentteihin)))
         {:vayla-tyyli? true}]]

       [:span
        [napit/tallenna "Peruuta"
         #(toggle-kentat "kommentti-lisaa" "kommentti-area")
         {:luokka "nappi-toissijainen" :vayla-tyyli? true}]]]]]))

(defn- paivakirjan-sticky [e!]
  ;; Sticky bar
  [:div.ala-valinnat-fixed

   [:div.napit.klikattava {:on-click #(e! (tiedot/->SelaaPaivakirjoja :edellinen))}
    [:span.nuoli
     [ikonit/harja-icon-navigation-previous-page]]
    [:span "Edellinen"]]

   [:div.napit.klikattava {:on-click #(e! (tiedot/->SelaaPaivakirjoja :seuraava))}
    [:span "Seuraava"]
    [:span.nuoli
     [ikonit/harja-icon-navigation-next-page]]]

   [:div.napit.ei-reunoja.klikattava
    ^{:key "raporttipdf"}
    [:form {:target "_blank" :method "POST"
            :action (k/pdf-url :raportointi)}
     [:input {:type "hidden" :name "parametrit"
              :value (t/clj->transit @tiedot/raportin-parametrit)}]

     [:button {:type "submit"}
      [:span.nuoli
       [ikonit/livicon-download]]
      [:span "Tallenna PDF"]]]]

   [:div.napit.ei-reunoja.klikattava {:on-click #(tiedot/scrollaa-viimeksi-valitulle-riville e!)}
    [:span.nuoli [ikonit/harja-icon-navigation-close]]
    [:span "Sulje"]]])

(defn suorita-tyomaapaivakirja-raportti [e! {:keys [valittu-rivi] :as app}]
  (if-let [tiedot @tiedot/raportin-tiedot]
    [:div.tyomaapaivakirja
     ;; Päiväkirjanäkymä
     [:div.paivakirja-nakyma
      ;; Takaisin nappi
      [:div.klikattava {:class "sulje" :on-click #(tiedot/scrollaa-viimeksi-valitulle-riville e!)}
       [ikonit/harja-icon-navigation-close]]
      
      ;; Header 
      (paivakirjan-header e! app)

      ;; Raportin html
      [muodosta-html (assoc-in tiedot [1 :tunniste] tiedot/raportti-avain)]

      ;; Kommentit
      (paivakirjan-kommentit e! valittu-rivi)]

     ;; Sticky bar (Edellinen - Seuraava) Tallenna PDF
     (paivakirjan-sticky e!)]

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
         
         (e! (tiedot/->HaeTiedot)))

      #(e! (tiedot/->PoistaRiviValinta)))

    (fn [e! {:keys [valittu-rivi] :as app}]
      [:div
       (if valittu-rivi
         ;; Jos valittu rivi, näytä päiväkirjanäkymä (tehty raporttien puolelle)
         (do 
           (e! (tiedot/->HaeKommentit))
           [suorita-tyomaapaivakirja-raportti e! app])

         ;; Mikäli ei valittua riviä, päivitä aikavälivalinta ja näytä listaus
         [tyomaapaivakirja-listaus e! app])])))

(defn tyomaapaivakirja []
  [tuck tiedot/tila tyomaapaivakirja*])
