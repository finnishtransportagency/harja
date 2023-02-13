(ns harja.views.vesivaylat.urakka.materiaalit
  (:require [tuck.core :as tuck]
            [cljs.core.async :as async :refer [put! <! chan close!]]
            [harja.ui.grid :as grid]
            [harja.ui.grid.protokollat :as grid-protokolla]
            [harja.ui.napit :as napit]
            [harja.domain.vesivaylat.materiaali :as m]
            [harja.tiedot.vesivaylat.urakka.materiaalit :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.lomake :as lomake]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.ikonit :as ikonit]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :as yleiset]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.fmt :as fmt]
            [harja.ui.debug :as debug])
  (:require-macros [harja.tyokalut.ui :refer [for*]]
                   [cljs.core.async.macros :refer [go]]))

(defn- muuta-yksikko-materiaalin-muuttuessa
  [grid-tila predikaatti]
  (into {} (map (fn [[avain arvo]]
                  (if (predikaatti arvo)
                    [avain (assoc arvo :yksikko (-> arvo :tallennetut-materiaalit ::m/yksikko))]
                    [avain arvo]))
                grid-tila)))

(defn hoida-materiaalitaulukon-yksikko
  [grid-komponentti]
  ;; Tässä on tarkoituksena laittaa gridissä käsiteltävän rivin
  ;; :yksikko avaimen alle oikea yksikkö, kun valitaan materiaali valikosta.
  (let [grid-tila (grid-protokolla/hae-muokkaustila grid-komponentti)
        materiaali-muutettu? #(and (:tallennetut-materiaalit %)
                                (not= (-> % :tallennetut-materiaalit ::m/yksikko)
                                      (:yksikko %)))
        joku-materiaali-muutettu? (some materiaali-muutettu? (vals grid-tila))]
    (when joku-materiaali-muutettu?
      (grid-protokolla/aseta-muokkaustila! grid-komponentti (muuta-yksikko-materiaalin-muuttuessa grid-tila materiaali-muutettu?)))))

(defn- materiaaliloki [e! urakka-id rivit nimi yksikko nayta-kaikki?]
  (let [rivit-jarjestyksessa (reverse (sort-by (juxt ::m/pvm ::m/luotu) rivit))
        rivin-voi-poistaa? (fn [rivi]
                             ;; Ensimmäistä luontiajan mukaista kirjausta käytetään määrittämään
                             ;; materiaalin alkuperäinen määrä, siksi sen saa poistaa vain jos se on ainoa kirjaus,
                             ;; jolloin koko materiaali poistuu.
                             ;; Kirjauspvm:ää ei voi käyttää määrittämään alkuperäistä määrää, koska ensimmäiselle
                             ;; kirjauspäivälle saattaa olla useita kirjauksia.
                             (or (and (= rivi (last rivit-jarjestyksessa))
                                      (= (count rivit) 1))
                                 ;; Muut kirjaukset saa poistaa aina
                                 (not= rivi (last rivit-jarjestyksessa))))
        rivien-maara (count rivit)
        info-rivi (with-meta [:tr
                              [:td {:colSpan "4"}
                               [napit/nappi ""
                                #(e! (tiedot/->NaytaKaikkiKirjauksetVaihto nimi))
                                {:ikoninappi? true
                                 :luokka "klikattava"
                                 :ikoni (if nayta-kaikki?
                                          (ikonit/livicon-chevron-up)
                                          (ikonit/livicon-chevron-down))}]
                               (when-not nayta-kaikki?
                                 [:span {:style {:margin-left "0.5em"}}
                                  (str "Materiaalin käyttörivejä on vielä lisäksi "
                                       (- rivien-maara 5) " kappaletta.")])]]
                             {:key (str "info-rivi-" nimi)})
        taulukko-rivit (for*
                         [{::m/keys [id pvm maara lisatieto] :as rivi} rivit-jarjestyksessa]
                         [:tr
                          [:td {:width "15%"} (pvm/pvm pvm)]
                          [:td {:width "15%" :class (if (neg? maara)
                                                      "materiaali-miinus"
                                                      "materiaali-plus")}
                           (str maara " " yksikko)]
                          [:td {:width "60%"} lisatieto]
                          [:td {:width "10%"}
                           (when (rivin-voi-poistaa? rivi)
                             [:span.klikattava
                              {:on-click (fn []
                                           (varmista-kayttajalta/varmista-kayttajalta
                                             {:otsikko "Poistetaanko kirjaus?"
                                              :sisalto [:span
                                                        (str "Poistetaanko "
                                                             (pvm/pvm pvm)
                                                             " kirjattu materiaalinkäyttö: "
                                                             maara " kpl?")]

                                              :hyvaksy "Poista"
                                              :toiminto-fn #(e! (tiedot/->PoistaMateriaalinKirjaus {:materiaali-id id
                                                                                                    :urakka-id urakka-id}))}))}
                              (ikonit/livicon-trash)])]])
        taulukko-rivit (if (> rivien-maara 5)
                         (concat taulukko-rivit [[info-rivi]])
                         taulukko-rivit)
        alku (take 5 taulukko-rivit)
        keski (butlast (drop 5 taulukko-rivit))
        loppu (last taulukko-rivit)]
    [:div.vv-materiaaliloki
     [:h3 "Muutokset"]
     [:table
      [:tbody
       (cond
         (and nayta-kaikki? (> rivien-maara 5)) (concat alku keski loppu)
         (and (not nayta-kaikki?) (> rivien-maara 5)) (concat alku loppu)
         :else taulukko-rivit)]]]))

(defn- materiaali-lomake [{:keys [muokkaa! tallenna! maara-placeholder]}
                          materiaali materiaalilistaus tallennus-kaynnissa?]
  (let [ikoni (if tallennus-kaynnissa?
                [yleiset/ajax-loader-pieni]
                [ikonit/tallenna])]
    [lomake/lomake {:muokkaa! muokkaa!
                    :footer-fn (fn [data]
                                 [napit/tallenna "Lisää materiaali"
                                  #(tallenna! data)
                                  {:disabled (or
                                               tallennus-kaynnissa?
                                               (not (lomake/voi-tallentaa-ja-muokattu? data)))
                                   :ikoni ikoni}])}
     [{:otsikko "Nimi" :nimi ::m/nimi :tyyppi :string :palstoja 3
       :pakollinen? true
       :validoi [(fn [nimi]
                   (when (some #(= nimi (::m/nimi %)) materiaalilistaus)
                     "Materiaali on jo käytössä urakassa"))]}
      (lomake/rivi
        {:otsikko "Määrä" :nimi ::m/maara :tyyppi :numero :placeholder maara-placeholder
         :kokonaisluku? true :pakollinen? true ::lomake/col-luokka "col-lg-6"}
        {:otsikko "Yksikkö" :nimi ::m/yksikko :tyyppi :string :pakollinen? true
         ::lomake/col-luokka "col-lg-6"})
      {:otsikko "Hälytysraja" :nimi ::m/halytysraja :tyyppi :numero :palstoja 1
       ::lomake/col-luokka "col-lg-6" :pakollinen? false}
      {:otsikko "Pvm" :nimi ::m/pvm :tyyppi :pvm :palstoja 1 ::lomake/col-luokka "col-lg-6"
       :pakollinen? true}
      {:otsikko "Lisätieto" :nimi ::m/lisatieto :tyyppi :text :koko [30 3] :pituus-max 2000
       :palstoja 3}]
     materiaali]))

(defn- materiaalin-kirjaus [e! {kirjaa-materiaali :kirjaa-materiaali
                                listaus :materiaalilistaus
                                tallennus-kaynnissa? :tallennus-kaynnissa?
                                :as app} nimi]

  (let [ensimmainen-kirjaus (->> (filter #(= nimi (::m/nimi %)) listaus)
                                 first
                                 ::m/muutokset
                                 (sort-by ::m/pvm)
                                 first
                                 ::m/pvm)
        yksikko (->> (filter #(= nimi (::m/nimi %)) listaus)
                     first
                     ::m/yksikko)]
    [:div.vv-materiaalin-kirjaus
     [napit/yleinen-ensisijainen "Kirjaa käyttö"
      #(e! (tiedot/->AloitaMateriaalinKirjaus nimi :- yksikko))
      {:ikoni (ikonit/livicon-minus)
       :luokka "materiaalin-kaytto"}]
     [napit/yleinen-ensisijainen "Kirjaa lisäys"
      #(e! (tiedot/->AloitaMateriaalinKirjaus nimi :+ yksikko))
      {:ikoni (ikonit/livicon-plus)
       :luokka "materiaalin-lisays"}]

     (when (= nimi (::m/nimi kirjaa-materiaali))
       (let [tyyppi (:tyyppi kirjaa-materiaali)]
         [leijuke/leijuke {:otsikko (str "Kirjaa " nimi
                                         (case tyyppi
                                           :- " käyttö"
                                           :+ " lisäys"))
                           :sulje! #(e! (tiedot/->PeruMateriaalinKirjaus))}
          [:div
           [lomake/lomake {:muokkaa! #(e! (tiedot/->PaivitaMateriaalinKirjaus %))
                           :footer-fn (fn [tiedot]
                                        [napit/tallenna "Tallenna"
                                         #(e! (tiedot/->KirjaaMateriaali))
                                         {:disabled
                                          (or
                                            tallennus-kaynnissa?
                                            (not (lomake/voi-tallentaa-ja-muokattu? tiedot)))
                                          :ikoni (if tallennus-kaynnissa?
                                                   [yleiset/ajax-loader-pieni]
                                                   [ikonit/tallenna])}])}
            [{:otsikko "Määrä" :nimi ::m/maara :tyyppi :positiivinen-numero
              ::lomake/col-luokka "col-lg-6"
              :pakollinen? true
              :kokonaisluku? true}
             {:otsikko "Pvm" :nimi ::m/pvm :tyyppi :pvm
              ::lomake/col-luokka "col-lg-6"
              :validoi [[:pvm-toisen-pvmn-jalkeen ensimmainen-kirjaus
                         (str "Kirjaus täytyy olla ensimmäisen kirjauspäivän ("
                              (fmt/pvm ensimmainen-kirjaus)
                              ") jälkeen.")]]
              :pakollinen? true}
             {:otsikko " Lisätieto " :nimi ::m/lisatieto :tyyppi :text :koko [30 3]
              :pituus-max 2000 :palstoja 3}]
            kirjaa-materiaali]
           (let [maara-nyt (some #(when (= nimi (::m/nimi %))
                                    (::m/maara-nyt %)) listaus)
                 kaytettava-maara (::m/maara kirjaa-materiaali)]
             [yleiset/tietoja {}
              "Määrä nyt: " maara-nyt
              "Muutos: " (case tyyppi
                           :- (if kaytettava-maara (- kaytettava-maara) " ")
                           :+ (or kaytettava-maara " "))
              "Määrä jälkeen: " ((case tyyppi
                                   :- -
                                   :+ +)
                                  maara-nyt
                                  (or kaytettava-maara 0))])]]))]))

(defn materiaalit* [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->PaivitaUrakka @nav/valittu-urakka)))
    (komp/watcher nav/valittu-urakka (fn [_ _ ur]
                                       (e! (tiedot/->PaivitaUrakka ur))))
    (fn [e! {:keys [materiaalilistaus lisaa-materiaali tallennus-kaynnissa?]
             :as app}]
      (let [voi-kirjata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-vesivayla-materiaalit
                                                   (:urakka-id app))]
        [:div.vv-materiaalit
         (when voi-kirjata?
           [valinnat/urakkatoiminnot {}
            ^{:key "lisaa-materiaali"}
            [:div.inline-block
             [napit/uusi " Lisää materiaali " #(e! (tiedot/->AloitaMateriaalinLisays))
              {:disabled lisaa-materiaali}]
             (when lisaa-materiaali
               [:div.vv-lisaa-materiaali-leijuke
                [leijuke/leijuke {:otsikko "Lisää materiaali"
                                  :sulje! #(e! (tiedot/->PeruMateriaalinLisays))
                                  :ankkuri "lisaa-nappi" :suunta :oikea}
                 [materiaali-lomake {:muokkaa! #(e! (tiedot/->PaivitaLisattavaMateriaali %))
                                     :tallenna! #(e! (tiedot/->LisaaMateriaali))
                                     :maara-placeholder "Syötä alkutilanne"}
                  lisaa-materiaali materiaalilistaus tallennus-kaynnissa?]]])]])

         [grid/grid
          {:voi-lisata? false
           :voi-poistaa? (constantly false)
           :id "vv-materiaalilistaus"
           :tunniste ::m/nimi
           :tyhja "Ei materiaaleja"
           :tallenna (when
                       voi-kirjata?
                       (fn [sisalto]
                         (let [ch (chan)]
                           (e! (tiedot/->MuutaAlkuperaisetTiedot
                                 {:urakka-id (:urakka-id app)
                                  :uudet-alkuperaiset-tiedot (map
                                                               #(select-keys % [::m/alkuperainen-maara ::m/muutokset ::m/yksikko ::m/halytysraja])
                                                               sisalto)
                                  :chan ch}))
                           ch)))
           :vetolaatikot (into {}
                               (map (juxt ::m/nimi
                                          (fn [{muutokset ::m/muutokset nimi ::m/nimi yksikko ::m/yksikko nayta-kaikki? :nayta-kaikki?}]
                                            [materiaaliloki e! (:urakka-id app) muutokset nimi yksikko nayta-kaikki?])))
                               materiaalilistaus)}
          [{:tyyppi :vetolaatikon-tila :leveys 1}
           {:otsikko "Materiaali" :nimi ::m/nimi :tyyppi :string :leveys 30 :muokattava? (constantly false)}
           {:otsikko "Alkuperäinen määrä" :nimi ::m/alkuperainen-maara :tyyppi :numero :leveys 10}
           {:otsikko "Määrä nyt" :nimi ::m/maara-nyt :tyyppi :numero :leveys 10 :muokattava? (constantly false)}
           {:otsikko "Yksikkö" :nimi ::m/yksikko :tyyppi :string :leveys 10}
           {:otsikko "Hälytysraja" :nimi ::m/halytysraja :tyyppi :numero :leveys 10}
           (when voi-kirjata?
             {:otsikko "Kirjaa" :leveys 15 :tyyppi :komponentti
              :komponentti (fn [{nimi ::m/nimi}]
                             [materiaalin-kirjaus e! app nimi])})]
          materiaalilistaus]
         [debug/debug app]]))))

(defn materiaalit [ur]
  [tuck/tuck tiedot/app materiaalit*])
