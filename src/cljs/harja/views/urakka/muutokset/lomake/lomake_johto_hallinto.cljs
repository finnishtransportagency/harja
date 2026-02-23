(ns harja.views.urakka.muutokset.lomake.lomake-johto-hallinto
  "Muutokset välilehden lomakkeet - Johto- ja hallintokorvauksen muutos"
  (:require [reagent.core :as r]

            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.ui.grid.protokollat :as grid-protokollat]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]))


(defn lomake-johto-ja-hallintokorvaus
  "johto-ja-hallintokorvaus muutoksen lomakekomponentti"
  [e! {:keys [valittu-hoitokausi urakan-hoitokaudet muokattava-muutos
              haku-kaynnissa? muutoksen-tiedot-haku-kaynnissa?] :as app}]
  (let [muutostapa (muutos-domain/jjh-korvaus-muutos-vai-vahennys? (:alkupvm @nav/valittu-urakka))
        rivit (:johto-ja-hallintokorvaukset muokattava-muutos)
        summa (reduce + 0 (map :tavoitehinnan-muutos (vec (vals rivit))))
        rivit-atom (r/atom rivit)]

    [(lomake/ryhma {:otsikko "Perustiedot"}
       {:nimi :hoitovuosi
        :tyyppi :string
        :otsikko "Hoitovuosi"
        :muokattava? (constantly false)
        :hae #(fmt/hoitokauden-jarjestysluku-ja-vuodet valittu-hoitokausi urakan-hoitokaudet "Hoitovuosi")}

       (yhteiset/+rivi-muutoksen-syy+)

       (merge
         (yhteiset/+rivi-muutos-voimassa+ urakan-hoitokaudet valittu-hoitokausi)
         {:aseta (fn [rivi arvo]
                   (-> rivi
                     (assoc :voimassa_alkaen arvo)
                     (assoc :mahdolliset-hoitovuodet-lomakkeella urakan-hoitokaudet)
                     (assoc :johto-ja-hallintokorvaukset (:johto-ja-hallintokorvaukset rivi))))}))

     (first (yhteiset/liite-kentta e! app))

     (if muutoksen-tiedot-haku-kaynnissa?
       {:tyyppi :komponentti
        :uusi-rivi? true
        :komponentti (fn [_rivi]
                       [yleiset/ajax-loader "Haetaan muutoksen tietoja..."])}
       {:nimi :johto-ja-hallintokorvaus-muutokset
        :otsikko ""
        :palstoja 2
        :tyyppi :komponentti
        :uusi-rivi? true
        :komponentti
        (fn [_e! _]
          [:span [:hr]
           [:h3 "Muutokset tavoitehintaan ja kuluihin"]

           [grid/muokkaus-grid
            {:tunniste :pvm
             :luokat ["johto-ja-hallintokorvaus-muutokset-grid"]
             :jarjesta :pvm
             :piilota-toiminnot? true
             :voi-lisata? false
             :voi-kumota? false
             :voi-poistaa? (constantly false)
             :muutos #(e! (t-yhteiset/->MuokkaaJohtoJaHallintoMuutosta (grid-protokollat/hae-muokkaustila %)))
             :voi-muokata? true
             :rivi-jalkeen [{:teksti "Yhteensä" :sarakkeita 1 :luokka "yhteensa"}
                            {:teksti (fmt/euro-opt summa) :tasaa :oikea :luokka "yhteensa"}]}

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
              :leveys 8
              :muokattava? (constantly true)}]
            rivit-atom]

           [yleiset/info-laatikko :neutraali
            "Harja luo oikaisevat kulut automaattisesti tallentamisen jälkeen."
            nil nil
            {:luokka "johto-ja-hallintokorvaus-muutokset-info"}]])})]))
