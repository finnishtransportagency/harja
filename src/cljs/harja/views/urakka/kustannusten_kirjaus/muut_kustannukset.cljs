(ns harja.views.urakka.kustannusten-kirjaus.muut-kustannukset
  "Tiemerkintöjen muut kustannukset välilehti"
  (:require [harja.views.urakka.kustannusten-kirjaus.muut-kustannukset-tiedot :as tiedot]
            [harja.views.urakka.kustannusten-kirjaus.yhteiset :as yhteiset]
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


(defn- muut-kustannukset-muokkauspaneeli 
  "Toteumien muokkauspaneeli / rivin klikkaus"
  [e! {:keys [pk-luokat]} voi-kirjoittaa? voi-tallentaa? valittu-rivi  alkuaika tyypit]

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
       {:otsikko "Tyyppi"
        :vayla-tyyli? true
        :pakollinen? true
        :nimi :tyyppi
        :tyyppi :valinta
        :valinnat (map :tyyppi tyypit)
        :validoi [[:ei-tyhja "Valitse tyyppi"]]
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:nimi :kustannus-selite
        :otsikko "Selite"
        :tyyppi :text
        :pakollinen? true
        :piilota-checkbox? true
        :piilota-dropdown? true
        :salli-kirjoitus? true
        :validoi [[:ei-tyhja "Kirjoita kustannuksen selite"]]
        ::lomake/col-luokka "leveys-kokonainen"})

     (lomake/rivi
       {:otsikko "PK-luokka"
        :tyyppi :radio-group
        :vaihtoehto-arvo :luokka
        :pakollinen? true
        :vayla-tyyli? true
        :vaihtoehdot (keys pk-luokat)
        :vaihtoehto-nayta pk-luokat
        :validoi [#(when (nil? %) "Syötä jokin luokka, tai 'Ei PK-luokkaa'")]})
     
     (lomake/rivi
       {:otsikko "Summa"
        :pakollinen? true
        :vayla-tyyli? true
        :nimi :kustannus
        :tyyppi :euro
        :teksti-oikealla "EUR"
        :validoi [[:ei-tyhja "Syötä kustannusarvo"]]
        ::lomake/col-luokka "col-xs-6 summa-valinta"})]
    valittu-rivi]])


(defn- muut-kustannukset-grid [e! rivit]
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
     :desimaalien-maara 0
     :nimi :luokka
     :luokka "text-nowrap"
     :leveys 0.2}

    {:otsikko "Kustannus"
     :tyyppi :euro
     :tasaa :oikea
     :nimi :kustannus
     :luokka "text-nowrap"
     :leveys 0.1}]
   rivit])


(defn muut-kustannukset-listaus [e! {:keys [rivit valinnat muokataan valittu-rivi
                                            haku-kaynnissa? kustannukset tyypit] :as app}]
  (let [alkuaika (:alkuaika valittu-rivi)
        ;; TODO 
        voi-kirjoittaa? true
        voi-tallentaa? true
        grid (muut-kustannukset-grid e! rivit)
        lisaa-uusi-fn #(e! (tiedot/->AvaaKustannusModal nil))
        muokkauspaneeli (muut-kustannukset-muokkauspaneeli e! valinnat voi-kirjoittaa? voi-tallentaa? valittu-rivi alkuaika tyypit)]

    (yhteiset/nakyma-body "Muut kustannukset"
      e! lisaa-uusi-fn
      rivit valinnat muokataan valittu-rivi
      haku-kaynnissa? kustannukset tyypit muokkauspaneeli grid nil)))


(defn muut-kustannukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan
      #(do
         (e! (tiedot/->HaeTiedot))))
    (fn [e! app] [muut-kustannukset-listaus e! app])))


(defn muut-kustannukset []
  [tuck tiedot/tila muut-kustannukset*])
