(ns harja.views.vesivaylat.urakka.materiaalit
  (:require [tuck.core :as tuck]
            [cljs.core.async :as async :refer [put! <! chan close!]]
            [harja.ui.grid :as grid]
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
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta])
  (:require-macros [harja.tyokalut.ui :refer [for*]]
                   [cljs.core.async.macros :refer [go]]))


(defn- materiaaliloki [e! urakka-id rivit]
  [:div.vv-materiaaliloki
   [:h3 "Muutokset"]
   [:table
    [:tbody
     (for*
       [{::m/keys [id pvm maara lisatieto] :as rivi} (reverse (sort-by ::m/pvm rivit))]
       [:tr
        [:td {:width "15%"} (pvm/pvm pvm)]
        [:td {:width "15%" :class (if (neg? maara)
                                    "materiaali-miinus"
                                    "materiaali-plus")}
         maara " kpl"]
        [:td {:width "60%"} lisatieto]
        [:td {:width "10%"}
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
          (ikonit/livicon-trash)]]])]]])

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
      {:otsikko "Määrä" :nimi ::m/maara :tyyppi :numero :placeholder maara-placeholder
       :palstoja 1 :kokonaisluku? true
       :pakollinen? true ::lomake/col-luokka "col-lg-6"}
      {:otsikko "Pvm" :nimi ::m/pvm :tyyppi :pvm :palstoja 1 ::lomake/col-luokka "col-lg-6"
       :pakollinen? true}
      {:otsikko "Lisätieto" :nimi ::m/lisatieto :tyyppi :text :koko [30 3] :pituus-max 2000
       :palstoja 3}]
     materiaali]))

(defn- materiaalin-kirjaus [e! {kirjaa-materiaali :kirjaa-materiaali
                                listaus :materiaalilistaus
                                tallennus-kaynnissa? :tallennus-kaynnissa?
                                :as app} nimi]
  [:div.vv-materiaalin-kirjaus
   [napit/yleinen-ensisijainen "Kirjaa käyttö"
    #(e! (tiedot/->AloitaMateriaalinKirjaus nimi :-))
    {:ikoni (ikonit/livicon-minus)
     :luokka "materiaalin-kaytto"}]
   [napit/yleinen-ensisijainen "Kirjaa lisäys"
    #(e! (tiedot/->AloitaMateriaalinKirjaus nimi :+))
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
            :pakollinen? true}
           {:otsikko "Lisätieto" :nimi ::m/lisatieto :tyyppi :text :koko [30 3]
            :pituus-max 2000 :palstoja 3}]
          kirjaa-materiaali]
         (let [maara-nyt (some #(when (= nimi (::m/nimi %))
                                  (::m/maara-nyt %)) listaus)
               kaytettava-maara (::m/maara kirjaa-materiaali)]
           [yleiset/tietoja {}
            "Määrä nyt: " maara-nyt
            "Muutos: " (case tyyppi
                         :- (if kaytettava-maara (- kaytettava-maara) "")
                         :+ (or kaytettava-maara ""))
            "Määrä jälkeen:" ((case tyyppi
                                :- -
                                :+ +)
                               maara-nyt
                               (or kaytettava-maara 0))])]]))])

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
             [napit/uusi "Lisää materiaali" #(e! (tiedot/->AloitaMateriaalinLisays))
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
           :id "vv-materiaalilistaus"
           :tunniste ::m/nimi
           :tyhja "Ei materiaaleja"
           :tallenna (when
                       voi-kirjata?
                       (fn [sisalto]
                         (let [ch (chan)]
                           (e! (tiedot/->MuutaAlkuperainenMaara
                                 {:urakka-id (:urakka-id app)
                                  :uudet-alkuperaiset-maarat (map
                                                               #(select-keys % [::m/nimi ::m/alkuperainen-maara])
                                                               sisalto)
                                  :chan ch}))
                           (go (<! ch)))))
           :vetolaatikot (into {}
                               (map (juxt ::m/nimi
                                          (fn [{muutokset ::m/muutokset}]
                                            [materiaaliloki e! (:urakka-id app) muutokset])))
                               materiaalilistaus)}
          [{:tyyppi :vetolaatikon-tila :leveys 1}
           {:otsikko "Materiaali" :nimi ::m/nimi :tyyppi :string :leveys 30 :muokattava? (constantly false)}
           {:otsikko "Alkuperäinen määrä" :nimi ::m/alkuperainen-maara :tyyppi :numero :leveys 10}
           {:otsikko "Määrä nyt" :nimi ::m/maara-nyt :tyyppi :numero :leveys 10 :muokattava? (constantly false)}
           (when voi-kirjata?
             {:otsikko "Kirjaa" :leveys 15 :tyyppi :komponentti
              :komponentti (fn [{nimi ::m/nimi}]
                             [materiaalin-kirjaus e! app nimi])})]
          materiaalilistaus]]))))

(defn materiaalit [ur]
  [tuck/tuck tiedot/app materiaalit*])
