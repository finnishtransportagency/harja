(ns harja.views.urakka.kustannusten-kirjaus.sakot-ja-bonukset
  "Tiemerkintöjen sakot ja bonukset välilehti"
  (:require [harja.views.urakka.kustannusten-kirjaus.sakot-bonukset-tiedot :as tiedot]
            [harja.views.urakka.kustannusten-kirjaus.yhteiset :as yhteiset]
            [tuck.core :refer [tuck]]
            [harja.asiakas.kommunikaatio :as komm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka.urakka :as tila]
            [reagent.core :as r]
            [harja.fmt :as fmt]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.pvm :as pvm]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.transit :as transit]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr-domain]))


(defn- sakot-bonukset-grid [e! rivit]
  [grid/grid {:tyhja (if false ; TODO haku-kaynnissa?
                       [ajax-loader-pieni "Haku käynnissä..."]
                       "Valitulle aikavälille ei löytynyt mitään.")
              :tunniste :id
              :sivuta grid/vakiosivutus
              :voi-kumota? false
              :piilota-toiminnot? true
              :mahdollista-rivin-valinta? true
              :rivi-klikattu #(e! (tiedot/->AvaaModal %))}

               ;; TODO 
   [{:otsikko-komp (fn [_ _]
                     [:div.pvm "Päivämäärä"
                      [:div [ikonit/action-sort-descending]]])
     :tyyppi :komponentti
     :komponentti (fn [arvo _] (str (pvm/pvm (:pvm arvo))))
     :luokka "semibold text-nowrap"
     :leveys 0.2}

    {:otsikko "Laji"
     :tyyppi :string
     :nimi :tyyppi
     :luokka "text-nowrap"
     :leveys 0.2}

    {:otsikko "Kohde"
     :tyyppi :string
     :nimi :selite
     :luokka "text-nowrap"
     :leveys 0.2}

    {:otsikko "Selite"
     :tyyppi :string
     :nimi :selite
     :luokka "text-nowrap"
     :leveys 0.2}

    {:otsikko "Määrä"
     :tyyppi :euro
     :tasaa :oikea
     :nimi :kustannus
     :luokka "text-nowrap"
     :leveys 0.1}

    {:otsikko "Liite"
     :tyyppi :euro
     :tasaa :oikea
     :nimi :kustannus
     :luokka "text-nowrap"
     :leveys 0.1}]
   rivit])


(defn- sakot-bonukset-muokkauspaneeli
  "Toteumien muokkauspaneeli / rivin klikkaus"
  [e! {:keys [lajit]} voi-kirjoittaa? voi-tallentaa? valittu-rivi  alkuaika tyypit]

  [:div.overlay-oikealla
   [lomake/lomake
    {:ei-borderia? true
     :voi-muokata? voi-kirjoittaa?
     :tarkkaile-ulkopuolisia-muutoksia? true
     :muokkaa! #(e! (tiedot/->MuokkaaRivia %))
     :header [:div.col-md-12
              [:h2.header-yhteiset "Lisää uusi sakko tai bonus"]
              [:hr]]
     :footer [:<>
              [:hr]
              [:div.muokkaus-modal-napit
               [napit/tallenna "Tallenna" #(println "tallenna") {:disabled (not voi-tallentaa?)}]
               [napit/yleinen-toissijainen "Peruuta" #(e! (tiedot/->SuljeMuokkaus))]]]}

    [(lomake/rivi
       {:otsikko "Päivämäärä"
        :pakollinen? true
        :tyyppi :komponentti
        :komponentti (fn []
                       [:span
                        [kentat/tee-kentta {:tyyppi :pvm :vayla-tyyli? true}
                         (r/wrap
                           alkuaika
                           #(println "test5"))]])
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:otsikko "Laji"
        :tyyppi :radio-group
        :vaihtoehto-arvo :luokka ;; TODO 
        :pakollinen? true
        :vayla-tyyli? true
        :vaihtoehdot (keys lajit)
        :vaihtoehto-nayta lajit
        :validoi [#(when (nil? %) "Anna kustannuksen tyyppi")]})

     ;; TODO 
     (let [testi-var [{:id 0 :kohde "Kohde 1"} {:id 1 :kohde "Kohde 2"}]
           testi-valinnat (mapv :id testi-var)
           testi-kuvaukset (into {} (map (fn [{:keys [id kohde]}] [id kohde]) testi-valinnat))]
       (lomake/rivi
         {:otsikko "Päällystys- tai paikkauskohde"
          :pakollinen? true
          :validoi [[:ei-tyhja "Valitse kohde"]]
          :nimi :kohde ;; TODO 
          :tyyppi :valinta
          :valinnat (into [nil] testi-valinnat)
          :valinta-nayta #(if %
                            (testi-kuvaukset %)
                            "Yleinen (ei kohdetta)")
          ::lomake/col-luokka "leveys-kokonainen"}))

     (lomake/rivi
       {:nimi :kustannus-selite ;; TODO 
        :otsikko "Selite"
        :tyyppi :text
        :pakollinen? true
        :piilota-checkbox? true
        :piilota-dropdown? true
        :salli-kirjoitus? true
        :validoi [[:ei-tyhja "Kirjoita kustannuksen selite"]]
        ::lomake/col-luokka "leveys-kokonainen"})

     ;; TODO 
     (let [testi-var [{:id 0 :kohde "Kohde 1"} {:id 1 :kohde "Kohde 2"}]
           testi-valinnat (mapv :id testi-var)
           testi-kuvaukset (into {} (map (fn [{:keys [id kohde]}] [id kohde]) testi-valinnat))]

       (lomake/rivi
         {:otsikko "Kulun kohdistus"
          :pakollinen? true
          :validoi [[:ei-tyhja "Valitse toimenpide"]]
          :nimi :kohde ;; TODO 
          :tyyppi :valinta
          :valinnat (into [nil] testi-valinnat)
          :valinta-nayta #(if %
                            (testi-kuvaukset %)
                            "Valitse toimenpide")
          ::lomake/col-luokka "leveys-kokonainen"}))

     (lomake/rivi
       {:otsikko "Summa"
        :pakollinen? true
        :vayla-tyyli? true
        :nimi :kustannus
        :tyyppi :euro
        :teksti-oikealla "EUR"
        :validoi [[:ei-tyhja "Syötä kustannusarvo"]]
        ::lomake/col-luokka "col-xs-6 summa-valinta"})
     
     ;; TODO 
     (let [test-atom (atom nil)
           urakka-id 35
           liitteet {}
           uusi-liite {}]
       (lomake/rivi
         {:otsikko "Liitteet"
          :nimi :liitteet
          :tyyppi :komponentti
          :komponentti (fn [_]
                         [liitteet/liitteet-ja-lisays urakka-id liitteet
                          {:uusi-liite-atom (r/wrap uusi-liite
                                              #(swap! test-atom assoc :uusi-liite %))
                           :uusi-liite-teksti "Lisää liite"
                           :salli-poistaa-lisatty-liite? true
                           :poista-lisatty-liite-fn #(swap! test-atom dissoc :uusi-liite)
                           :salli-poistaa-tallennettu-liite? false}])}))]
    valittu-rivi]])

(defn sakot-bonukset-listaus [e! {:keys [rivit valinnat muokataan valittu-rivi
                                         haku-kaynnissa? kustannukset tyypit valittu-laji] :as app}]

  (let [alkuaika (:alkuaika valittu-rivi)
        ;; TODO 
        voi-kirjoittaa? true
        voi-tallentaa? true
        muokkauspaneeli (sakot-bonukset-muokkauspaneeli e! valinnat voi-kirjoittaa? voi-tallentaa? valittu-rivi alkuaika tyypit)
        grid (sakot-bonukset-grid e! rivit)
        laji-suodatin [kentat/tee-kentta {:tyyppi :radio-group
                                          :space-valissa? true
                                          :vaihtoehdot [:kaikki :sakko :bonus]
                                          :vayla-tyyli? true
                                          :nayta-rivina? true
                                          :valitse-fn #(e! (tiedot/->ValitseLaji %))
                                          :vaihtoehto-nayta tiedot/laji-valinnat}
                       (atom valittu-laji)]]

    (yhteiset/nakyma-body "Sakot ja bonukset"
      e! app
      rivit valinnat muokataan valittu-rivi
      haku-kaynnissa? kustannukset tyypit muokkauspaneeli grid laji-suodatin)))


(defn sakot-ja-bonukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan
      #(do
         (e! (tiedot/->HaeTiedot))))
    (fn [e! app] [sakot-bonukset-listaus e! app])))


(defn sakot-ja-bonukset []
  [tuck tiedot/tila sakot-ja-bonukset*])
