(ns harja.views.urakka.muutokset.lomake.lomake-johto-hallinto
  "Muutokset välilehden lomakkeet - Johto- ja hallintokorvauksen muutos"
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.muutos-tiedot :as muutos-tiedot]))


(defn lomake-johto-ja-hallintokorvaus
  "johto-ja-hallintokorvaus muutoksen lomakekomponentti"
  [e! {:keys [valittu-hoitokausi urakan-hoitokaudet] :as app}]
  (let [muutostapa (muutos-domain/jjh-korvaus-muutos-vai-vahennys? (:alkupvm @nav/valittu-urakka))
        summa (reduce + 0 (map :tavoitehinnan-muutos (vals @muutos-tiedot/johto-ja-hallintokorvausmuutokset-atom)))]

    [(lomake/ryhma {:otsikko "Perustiedot"}
       {:nimi :hoitovuosi
        :tyyppi :string
        :otsikko "Hoitovuosi"
        :muokattava? (constantly false)
        :hae #(fmt/hoitokauden-jarjestysluku-ja-vuodet valittu-hoitokausi urakan-hoitokaudet "Hoitovuosi")}

       (yhteiset/+rivi-muutoksen-syy+)
       (yhteiset/+rivi-muutos-voimassa+ app))

     (first (yhteiset/liite-kentta e! app))

     {:nimi :johto-ja-hallintokorvaus-muutokset
      :otsikko ""
      :palstoja 2
      :tyyppi :komponentti 
      :uusi-rivi? true
      :komponentti
      (fn [e! {:keys [johto-ja-hallintokorvausten-muutokset valittu-hoitokausi]}]
        [:span
         [:hr]
         [:h3 "Muutokset tavoitehintaan ja kuluihin"]

         [grid/muokkaus-grid
          {:tunniste :pvm
           :luokat ["johto-ja-hallintokorvaus-muutokset-grid"]
           :piilota-toiminnot? true
           :voi-lisata? false
           :voi-kumota? false
           :voi-poistaa? (constantly false)
           :voi-muokata? true
           :rivi-jalkeen [{:teksti "Yhteensä" :sarakkeita 1 :luokka "yhteensa"}
                          {:teksti (fmt/euro-opt summa) :tasaa :oikea :luokka "yhteensa"}]}

          ;; Taulukon kentät
          [{:otsikko "Kalenterikuukausi" 
            :nimi :pvm 
            :tyyppi :string
            :leveys 20
            :muokattava? (constantly false)
            :fmt #(when % (pvm/koko-kuukausi-ja-vuosi % true))}
           
           {:otsikko (if (= muutostapa :muutos)
                       "Muutos € (+/-)"
                       "Vähennys (€)")
            :nimi :tavoitehinnan-muutos 
            :vaadi-negatiivinen? (when (= muutostapa :vahennys) true)
            :tyyppi :numero 
            :fmt fmt/euro-opt 
            :tasaa :oikea 
            :leveys 8}]
          muutos-tiedot/johto-ja-hallintokorvausmuutokset-atom]

         [yleiset/info-laatikko :neutraali
          "Harja luo oikaisevat kulut automaattisesti tallentamisen jälkeen."
          nil nil 
          {:luokka "johto-ja-hallintokorvaus-muutokset-info"}]])}]))
