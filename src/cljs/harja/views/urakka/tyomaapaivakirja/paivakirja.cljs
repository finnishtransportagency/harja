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
            [clojure.string :as str]
            [harja.ui.nakymasiirrin :as siirrin]
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
  "App statesta löytyy muutoshistoria jossa mäpätty: ( ({vers1 {:info , :toiminto <>, :vanhat <>, :uudet <>}}) ... )
  :info : Mistä taulusta on tehty mitäkin, esim '<toiminto=Lisätty> <info=säätietoja>'
  :toiminto : 'muutettu' | 'poistettu' | 'lisatty' 
  :vanhat : Vanhan version arvot 
  :uudet : Nykyisen version arvot"
  [e! {:keys [muutoshistoria historiarivi-auki]}]
  ;; Tehty custom modali koska Harjan nayta-modal herjasi React atom virheitä ilman kummempia tietoja enkä saanut korjattua
  ;; Ei sopinut luultavasti tuck staten kanssa yhteen
  [:div.muutoshistoria-modal {:on-click(fn [e]
                                         ;; Jos klikattiin elementin ulkopuolelle, suljetaan modali
                                         (when (= (-> e .-target .-classList str) "muutoshistoria-modal")
                                           (e! (tiedot/->MuutoshistoriaAuki nil))))}
   [:div#muutoshistoria-dialog
    [:span.klikattava.sulje {:on-click #(e! (tiedot/->MuutoshistoriaAuki nil))}
     [ikonit/harja-icon-navigation-close]]
    [:div.muutoshistoria
     [:div.muutoshistoria-otsikko "Versiohistoria"]
     (when (= (count muutoshistoria) 0)
       [:div.muutoshistoria-ei-tietoja "Ei historiatietoja."])
     ;; Loopataan muutokset ja tehdään gridit, reverse jotta uusimmat muutokset ylimpänä
     (for* [[indeksi versiomuutokset] (map-indexed vector (reverse muutoshistoria))]
       (let [kentta-auki? (get-in historiarivi-auki [indeksi])
             lisattiin? (some #(= (:toiminto %) "lisatty") versiomuutokset)
             poistettiin? (some #(= (:toiminto %) "poistettu") versiomuutokset)
             muutettiin? (some #(= (:toiminto %) "muutettu") versiomuutokset)
             infot (str/join ", " (distinct (map :info versiomuutokset)))
             muokattu (reduce (fn [x y]
                                (if (>
                                     (get-in y [:uudet :versio])
                                     (get-in x [:uudet :versio]))
                                  y x))
                        (first versiomuutokset)
                        versiomuutokset)
             muokattu (or (-> muokattu :uudet :muokattu) (-> muokattu :vanhat :muokattu))
             muokattu (when muokattu
                        ;; En saanut muunnettua päivämääräksi oikein tätä #object[String] muuttujaa ilman että harja kaatuu, apua? 
                        (str (first (str/split muokattu "T")) " " (first (str/split (second (str/split muokattu "T")) "."))))
             toiminto (cond
                        (and lisattiin? poistettiin?)
                        (str "Muutettu " infot)
                        (and (not poistettiin?) (not lisattiin?) muutettiin?)
                        (str "Muutettu " infot)
                        (and poistettiin? (not lisattiin?) (not muutettiin?))
                        (str "Poistettu " infot)
                        (and lisattiin? (not poistettiin?) (not muutettiin?))
                        (str "Lisätty " infot)
                        :else (str "Muutettu " infot))
             fn-kentan-toiminto (fn [kentta]
                                  (cond
                                    (= (:toiminto kentta) "muutettu") "(muutettu)"
                                    (= (:toiminto kentta) "poistettu") "(poistettu)"
                                    (= (:toiminto kentta) "lisatty") "(lisätty)"))
             fn-hae-kentan-nimi (fn [kentta]
                                  ;; Kentän nimet löytyy :info avaimesta mutta näytetään ne hieman erissä muodossa gridissä
                                  (cond
                                    (= (:info kentta) "sääasematietoja") (str "Sääasemien tiedot " (fn-kentan-toiminto kentta))
                                    (= (:info kentta) "kalustoja") (str "Kalusto ja tielle tehdyt toimenpiteet " (fn-kentan-toiminto kentta))
                                    (= (:info kentta) "tiestön toimenpiteä") (str "Toimenpiteet " (fn-kentan-toiminto kentta))
                                    (= (:info kentta) "tapahtumia") (str "Tapahtumat " (fn-kentan-toiminto kentta))
                                    (= (:info kentta) "työnjohtajia") (str "Työnjohtajat " (fn-kentan-toiminto kentta))
                                    (= (:info kentta) "päivystäjiä") (str "Päivystäjät " (fn-kentan-toiminto kentta))
                                    (= (:info kentta) "säätietoja") (str "Poikkeussää " (fn-kentan-toiminto kentta))
                                    (= (:info kentta) "toimeksiantoja") (str "Toimeksiannot " (fn-kentan-toiminto kentta))
                                    :else "Ei tietoja"))
             fn-arvo-tai-tyhja (fn [kentta avain arvo]
                                 (or (get-in kentta [avain arvo]) "(tyhjä)"))
             fn-rivita-pilkun-jalkeen  (fn [teksti]
                                         (let [osat (str/split teksti #", ½n")]
                                           (for* [osa osat] [:div osa])))
             fn-hae-arvo (fn [kentta uusi?]
                           ;; Tehdään kenttiin arvot ja selitykset 
                           (let [avain (if uusi? :uudet :vanhat)]
                             (if (nil? (avain kentta))
                               "(tyhjä)"
                               (cond
                                 (= (:info kentta) "kalustoja")
                                 (str
                                   "Työkoneiden määrä: " (fn-arvo-tai-tyhja kentta avain :tyokoneiden_lkm) ", ½n"
                                   "Lisäkaluston määrä: " (fn-arvo-tai-tyhja kentta avain :lisakaluston_lkm))

                                 (= (:info kentta) "sääasematietoja")
                                 (str
                                   "Tunniste: " (fn-arvo-tai-tyhja kentta avain :aseman_tunniste) ", ½n"
                                   "Tien lämpötila: " (fn-arvo-tai-tyhja kentta avain :tien_lampotila) ", ½n"
                                   "Ilman lämpötila: " (fn-arvo-tai-tyhja kentta avain :ilman_lampotila) ", ½n"
                                   "Tuuli: " (fn-arvo-tai-tyhja kentta avain :keskituuli) " m/s, ½n"
                                   "S-Sum: " (fn-arvo-tai-tyhja kentta avain :sadesumma) " mm")

                                 (= (:info kentta) "tiestön toimenpiteä")
                                 (str
                                   "Tyyppi: " (fn-arvo-tai-tyhja kentta avain :tyyppi) ", ½n"
                                   "Tehtävät: " (fn-arvo-tai-tyhja kentta avain :tehtavat) ", ½n"
                                   "Toimenpiteet: " (fn-arvo-tai-tyhja kentta avain :toimenpiteet))

                                 (or
                                   (= (:info kentta) "työnjohtajia")
                                   (= (:info kentta) "päivystäjiä"))
                                 (str
                                   "Nimi: " (fn-arvo-tai-tyhja kentta avain :nimi))

                                 (= (:info kentta) "tapahtumia")
                                 (str
                                   "Kuvaus: " (fn-arvo-tai-tyhja kentta avain :kuvaus) ", ½n"
                                   "Tyyppi: " (fn-arvo-tai-tyhja kentta avain :tyyppi))

                                 (= (:info kentta) "säätietoja")
                                 (str
                                   "Kuvaus: " (fn-arvo-tai-tyhja kentta avain :kuvaus) ", ½n"
                                   "Paikka: " (fn-arvo-tai-tyhja kentta avain :paikka))

                                 (= (:info kentta) "toimeksiantoja")
                                 (str
                                   "Kuvaus: " (fn-arvo-tai-tyhja kentta avain :kuvaus) ", ½n"
                                   "Aika: " (fn-arvo-tai-tyhja kentta avain :aika))

                                 :else "Ei tietoja"))))]
         [:div.muutoshistoria-grid
          [:span.muutos-tiedot
           [:span.klikattava {:on-click #(e! (tiedot/->ValitseHistoriarivi indeksi))} (if kentta-auki?
                                                                                        (ikonit/harja-icon-navigation-up)
                                                                                        (ikonit/harja-icon-navigation-down))]
           [:span.muutos-pvm muokattu]
           [:span.muutos-toiminto toiminto]]

          [:div {:class (if kentta-auki? "nakyva" "ei-nakyva") :id (str "muutoshistoria-" indeksi)}
           [grid/grid {:tunniste :id
                       :sivuta grid/vakiosivutus
                       :voi-kumota? false
                       :piilota-toiminnot? true
                       :jarjesta :paivamaara
                       :mahdollista-rivin-valinta? false
                       :piilota-muokkaus? true}

            [{:otsikko "Kentän nimi"
              :tyyppi :komponentti
              :komponentti (fn [arvo _] (fn-hae-kentan-nimi arvo))
              :solun-luokka #(str "nakyma-valkoinen-solu muutoshistoria-kentan-nimi")
              :leveys 0.75}
             {:otsikko "Vanha arvo"
              :tyyppi :komponentti
              :komponentti (fn [arvo _] (fn-rivita-pilkun-jalkeen (fn-hae-arvo arvo false)))
              :solun-luokka #(str "nakyma-valkoinen-solu")
              :leveys 1}
             {:otsikko "Uusi arvo"
              :tyyppi :komponentti
              :komponentti (fn [arvo _] (fn-rivita-pilkun-jalkeen (fn-hae-arvo arvo true)))
              :solun-luokka #(str "nakyma-valkoinen-solu")
              :leveys 1}]
            versiomuutokset]]]))]
    [:span.muutoshistoria-sulje
     [:span.nappi-ensisijainen.klikattava.alhaalla.sulje-valitys {:on-click #(e! (tiedot/->MuutoshistoriaAuki nil))}
      [ikonit/ikoni-ja-teksti (ikonit/harja-icon-navigation-close) "Sulje"]]]]])

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

      [:a.klikattava {:on-click #(e! (tiedot/->MuutoshistoriaAuki nil))} "Näytä muutoshistoria"]

      [:span.paivakirja-toimitus
       [:div {:class (str "pallura " (:tila valittu-rivi))}]
       [:span.toimituksen-selite (if (= "myohassa" (:tila valittu-rivi))
                                   "Myöhässä"
                                   "Ok")]]

      ;; Kommentti- nappi scrollaa alas kommentteihin
      [:a.klikattava {:on-click #(siirrin/siirry-elementin-id "Kommentit" 150)}
       [ikonit/ikoni-ja-teksti (ikonit/livicon-kommentti) (if (= (:kommenttien-maara valittu-rivi) 1)
                                                            (str (:kommenttien-maara valittu-rivi) " kommentti")
                                                            (str (:kommenttien-maara valittu-rivi) " kommenttia"))]]]
     [:hr]]))

(defn- paivakirjan-kommentit [e! {:keys [valittu-rivi muutoshistoria]}]
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

     (for* [[indeksi versiomuutokset] (map-indexed vector (reverse muutoshistoria))]
       (let [lisattiin? (some #(= (:toiminto %) "lisatty") versiomuutokset)
             poistettiin? (some #(= (:toiminto %) "poistettu") versiomuutokset)
             muutettiin? (some #(= (:toiminto %) "muutettu") versiomuutokset)
             infot (str/join ", " (distinct (map :info versiomuutokset)))
             muokattu (reduce (fn [x y]
                                (if (>
                                     (get-in y [:uudet :versio])
                                     (get-in x [:uudet :versio]))
                                  y x))
                        (first versiomuutokset)
                        versiomuutokset)
             muokattu (or (-> muokattu :uudet :muokattu) (-> muokattu :vanhat :muokattu))
             muokattu (when muokattu
                        (str (first (str/split muokattu "T")) " " (first (str/split (second (str/split muokattu "T")) "."))))
             toiminto (cond
                        (and lisattiin? poistettiin?)
                        (str "Muutettu " infot)
                        (and (not poistettiin?) (not lisattiin?) muutettiin?)
                        (str "Muutettu " infot)
                        (and poistettiin? (not lisattiin?) (not muutettiin?))
                        (str "Poistettu " infot)
                        (and lisattiin? (not poistettiin?) (not muutettiin?))
                        (str "Lisätty " infot)
                        :else (str "Muutettu " infot))]
         [:span
          [:div.alarivi-tiedot
           [:span muokattu]
           [:span.muutos-info "Jälkikäteismerkintä urakoitsijajärjestelmästä"]]
          [:div.kommentti.muutos
           [:h1.tieto-rivi (str "Työmaapäiväkirja päivitetty: " toiminto)]
           [:a.klikattava.info-rivi
            {:on-click #(do
                          ;; Käyttäjä klikkasi kommentin "Näytä muutoshistoria" nappia
                          ;; -> Avataan klikattu muutoshistoria gridi modalissa ja scrollataan käyttäjä sinne
                          (e! (tiedot/->MuutoshistoriaAuki indeksi))
                          (js/setTimeout (fn [] 
                                           ;; Käytetään muutoshistorian modalin(child element) siirrintä
                                           (siirrin/siirry-lapsi-elementissa (str "muutoshistoria-" indeksi) true "muutoshistoria-dialog")) 
                            200))}
            "Näytä muutoshistoria"]]]))

     [:div#kommentti-lisaa
      [:a.klikattava {:on-click #(do
                                   (toggle-kentat "kommentti-area" "kommentti-lisaa")
                                   (siirrin/siirry-elementin-id "kommentti-area" 150))}

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
      (paivakirjan-kommentit e! app)]

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
           (e! (tiedot/->HaeMuutoshistoria))
           (e! (tiedot/->HaeKommentit))
           [suorita-tyomaapaivakirja-raportti e! app])

         ;; Mikäli ei valittua riviä, päivitä aikavälivalinta ja näytä listaus
         [tyomaapaivakirja-listaus e! app])])))

(defn tyomaapaivakirja []
  [tuck tiedot/tila tyomaapaivakirja*])
