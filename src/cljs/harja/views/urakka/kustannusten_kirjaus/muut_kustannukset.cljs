(ns harja.views.urakka.kustannusten-kirjaus.muut-kustannukset
  "Tiemerkintöjen muut kustannukset välilehti"
  (:require [harja.views.urakka.kustannusten-kirjaus.muut-kustannukset-tiedot :as tiedot]
            [harja.views.urakka.kustannusten-kirjaus.apurit :as apurit]
            [tuck.core :refer [tuck]]
            [harja.asiakas.kommunikaatio :as komm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka.urakka :as tila]
            [reagent.core :as r]
            [harja.fmt :as fmt]
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


(defn- kustannus-muokkauspaneeli [e! voi-kirjoittaa? voi-tallentaa? valittu-rivi  alkuaika tyypit]
  [:div.overlay-oikealla
   [lomake/lomake
    {:ei-borderia? true
     :voi-muokata? voi-kirjoittaa?
     :tarkkaile-ulkopuolisia-muutoksia? true
     :muokkaa! #(e! (tiedot/->MuokkaaRivia %))
     :header [:div.col-md-12
              [:h2.header-yhteiset "Lisää uusi kustannus"]
              [:hr]]
     :footer [:<>
              [:hr]
              [:div.muokkaus-modal-napit
               ;; Tallenna
               [napit/tallenna "Tallenna muutokset" #(println "test1") {:disabled (not voi-tallentaa?)}]
               ;; Sulje 
               [napit/yleinen-toissijainen "Sulje" #(e! (tiedot/->SuljeMuokkaus))]]]}
    
    ;; Pvm 
    [(lomake/rivi
       {:otsikko "Päivämäärä"
        :pakollinen? true
        :tyyppi :komponentti
        :komponentti (fn []
                       [:span
                        [kentat/tee-kentta {:tyyppi :pvm :vayla-tyyli? true}
                         (r/wrap
                           alkuaika
                           #(println "test5"))]])})

     ;; Tyyppi
     (lomake/ryhma
       {:otsikko "Tyyppi"
        :ryhman-luokka "lomakeryhman-otsikko-tausta lomake-ryhma-otsikko"}
       ;; Alasveto
       (lomake/rivi
         {:otsikko "Tyyppi"
          :pakollinen? true
          :rivi-luokka "lomakeryhman-rivi-tausta"
          :validoi [[:ei-tyhja "Valitse tyyppi"]]
          :nimi :tyyppi
          :tyyppi :valinta
          :valinnat (map :tyyppi tyypit)
          ::lomake/col-luokka "leveys-kokonainen"}))

     ;; Summa
     (lomake/rivi
       {:otsikko "Summa"
        :pakollinen? true
        :rivi-luokka "lomakeryhman-rivi-tausta"
        :nimi :kustannus
        :tyyppi :euro
        :teksti-oikealla "EUR"
        :vayla-tyyli? true
        :validoi [[:ei-tyhja "Syötä kustannusarvo"]]
        ::lomake/col-luokka "maara-valinnat"})]
    valittu-rivi]])


(defn muut-kustannukset-listaus [e! {:keys [rivit valinnat muokataan valittu-rivi
                                            haku-kaynnissa? kustannukset tyypit] :as app}]

  (let [alkuaika (:alkuaika valittu-rivi)
        ;; TODO 
        voi-kirjoittaa? true
        voi-tallentaa? true
        muokkauspaneeli (kustannus-muokkauspaneeli e! voi-kirjoittaa? voi-tallentaa? valittu-rivi alkuaika tyypit)]

    (apurit/nakyma-body
      e! app
      rivit valinnat muokataan valittu-rivi
      haku-kaynnissa? kustannukset tyypit "Muut kustannukset" muokkauspaneeli nil
      ;; Grid
      [grid/grid {:tyhja (if false ; TODO haku-kaynnissa?
                           [ajax-loader-pieni "Haku käynnissä..."]
                           "Valitulle aikavälille ei löytynyt mitään.")
                  :tunniste :id
                  :sivuta grid/vakiosivutus
                  :voi-kumota? false
                  :piilota-toiminnot? true
                  :mahdollista-rivin-valinta? true
                  :rivi-klikattu #(e! (tiedot/->AvaaKustannusModal %))}

       [{:otsikko-komp (fn [_ _]
                         [:div.pvm "Päivämäärä"
                          [:div [ikonit/action-sort-descending]]])
         :tyyppi :komponentti
         :komponentti (fn [arvo _] (str (pvm/pvm (:pvm arvo))))
         :luokka "semibold text-nowrap"
         :leveys 0.2}

        {:otsikko "Tyyppi"
         :tyyppi :string
         :nimi :tyyppi
         :luokka "text-nowrap"
         :leveys 0.2}

        {:otsikko "Selite"
         :tyyppi :string
         :nimi :selite
         :luokka "text-nowrap"
         :leveys 0.2}

        {:otsikko "Pk-luokka"
         :tyyppi :numero
         :nimi :luokka
         :luokka "text-nowrap"
         :leveys 0.2}

        {:otsikko "Kustannus"
         :tyyppi :euro
         :tasaa :oikea
         :nimi :kustannus
         :luokka "text-nowrap"
         :leveys 0.1}]
       rivit])))


(defn muut-kustannukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan
      #(do
         (e! (tiedot/->HaeTiedot))))
    (fn [e! app] [muut-kustannukset-listaus e! app])))


(defn muut-kustannukset []
  [tuck tiedot/tila muut-kustannukset*])
