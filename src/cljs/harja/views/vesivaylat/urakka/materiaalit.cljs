(ns harja.views.vesivaylat.urakka.materiaalit
  (:require [tuck.core :as tuck]
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
            [harja.ui.yleiset :as yleiset])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn- materiaaliloki [e! nimi rivit]
  (komp/luo
   (komp/sisaan #(e! (tiedot/->HaeMateriaalinKaytto nimi)))
   (fn [e! nimi rivit]
     [:div.vv-materiaaliloki
      [:h3 "Muutokset"]
      [:table
       [:tbody
        (for*
         [{::m/keys [pvm maara lisatieto]} rivit]
         [:tr
          [:td {:width "15%"} (pvm/pvm pvm)]
          [:td {:width "15%" :class (if (neg? maara)
                                      "materiaali-miinus"
                                      "materiaali-plus")}
           maara " kpl"]
          [:td {:width "70%"} lisatieto]])]]])))

(defn- materiaali-lomake [{:keys [muokkaa! tallenna! maara-placeholder]}
                          materiaali materiaalilistaus]
  [lomake/lomake {:muokkaa! muokkaa!
                  :footer-fn (fn [data]
                            [napit/tallenna "Lisää materiaali"
                             #(tallenna! data)
                             {:disabled (not (lomake/voi-tallentaa-ja-muokattu? data))}])}
   [{:otsikko "Nimi" :nimi ::m/nimi :tyyppi :string :palstoja 3
     :validoi [(fn [nimi]
                 (when (some #(= nimi (::m/nimi %)) materiaalilistaus)
                   "Materiaali on jo käytössä urakassa"))
               [:ei-tyhja]]}
    {:otsikko "Määrä" :nimi ::m/maara :tyyppi :numero :placeholder maara-placeholder
     :palstoja 1
     :validoi [[:ei-tyhja]] ::lomake/col-luokka "col-lg-6"}
    {:otsikko "Pvm" :nimi ::m/pvm :tyyppi :pvm :palstoja 1 ::lomake/col-luokka "col-lg-6"}
    {:otsikko "Lisätieto" :nimi ::m/lisatieto :tyyppi :text :koko [30 3] :pituus-max 2000
     :palstoja 3}]
   materiaali])

(defn- materiaalin-kirjaus [e! {kirjaa-materiaali :kirjaa-materiaali
                                listaus :materiaalilistaus
                                :as app} nimi]
  [:div.vv-materiaalin-kirjaus
   [napit/yleinen-ensisijainen "Kirjaa"
    #(e! (tiedot/->AloitaMateriaalinKirjaus nimi))
    {:ikoni (ikonit/pencil)}]

   (when (= nimi (::m/nimi kirjaa-materiaali))
     [leijuke/leijuke {:otsikko (str "Kirjaa " nimi " käyttö")
                       :sulje! #(e! (tiedot/->PeruMateriaalinKirjaus))}
      [:div
       [lomake/lomake {:muokkaa! #(e! (tiedot/->PaivitaMateriaalinKirjaus %))}
        [{:otsikko "Määrä" :nimi ::m/maara :tyyppi :numero
          ::lomake/col-luokka "col-lg-6"}
         {:otsikko "Pvm" :nimi ::m/pvm :tyyppi :pvm
          ::lomake/col-luokka "col-lg-6"}
         {:otsikko "Lisätieto" :nimi ::m/lisatieto :tyyppi :text :koko [30 3]
          :pituus-max 2000 :palstoja 3}]
        kirjaa-materiaali]
       (let [maara-nyt (some #(when (= nimi (::m/nimi %))
                                (::m/maara-nyt %)) listaus)
             kaytettava-maara (::m/maara kirjaa-materiaali)]
         [yleiset/tietoja {}
          "Määrä nyt: " maara-nyt
          "Muutos: " (or kaytettava-maara "")
          "Määrä jälkeen:" (+ maara-nyt (or kaytettava-maara 0))])
       [napit/tallenna "Tallenna"
        #(e! (tiedot/->KirjaaMateriaali))]]])])

(defn materiaalit* [e! app]
  (komp/luo
   (komp/sisaan #(e! (tiedot/->PaivitaUrakka @nav/valittu-urakka)))
   (komp/watcher nav/valittu-urakka (fn [_ _ ur]
                                      (e! (tiedot/->PaivitaUrakka ur))))
   (fn [e! {:keys [materiaalilistaus materiaalin-kaytto lisaa-materiaali] :as app}]
     [:div.vv-materiaalit

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
             lisaa-materiaali materiaalilistaus]]])]]

      [grid/grid {:tunniste ::m/nimi
                  :tyhja "Ei materiaaleja"
                  :vetolaatikot (into {}
                                      (map (juxt ::m/nimi (fn [{nimi ::m/nimi}]
                                                            [materiaaliloki e! nimi
                                                             (materiaalin-kaytto nimi)])))
                                      materiaalilistaus)}
       [{:tyyppi :vetolaatikon-tila :leveys 1}
        {:otsikko "Materiaali" :nimi ::m/nimi :tyyppi :string :leveys 30}
        {:otsikko "Alkuperäinen määrä" :nimi ::m/alkuperainen-maara :tyyppi :numero :leveys 10}
        {:otsikko "Määrä nyt" :nimi ::m/maara-nyt :tyyppi :numero :leveys 10}
        {:otsikko "Kirjaa" :leveys 10 :tyyppi :komponentti
         :komponentti (fn [{nimi ::m/nimi}]
                        [materiaalin-kirjaus e! app nimi])}]
       materiaalilistaus]])))

(defn materiaalit [ur]
  [tuck/tuck tiedot/app materiaalit*])
