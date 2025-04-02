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
  "Toteumien luonti / muokkaus"
  [e! {:keys [pvm] :as valittu-rivi} {:keys [pk-luokat]} voi-kirjoittaa? voi-tallentaa? alkuaika tyypit]
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
                           pvm
                           #(e! (tiedot/->AsetaToteumanPvm %)))]])
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:otsikko "Tyyppi"
        :nimi :tyyppi
        :tyyppi :valinta
        :pakollinen? true
        :vayla-tyyli? true
        :valinnat (map :tyyppi tyypit)
        :valinta-nayta #(get tiedot/tyyppi-valinnat (keyword %))
        :validoi [[:ei-tyhja "Valitse tyyppi"]]
        ::lomake/col-luokka "col-xs-6"})

     (lomake/rivi
       {:nimi :selite
        :tyyppi :text
        :otsikko "Selite"
        :pakollinen? true
        :salli-kirjoitus? true
        :piilota-checkbox? true
        :piilota-dropdown? true
        :validoi [[:ei-tyhja "Kirjoita kustannuksen selite"]]
        ::lomake/col-luokka "leveys-kokonainen"})

     (lomake/rivi
       {:otsikko "PK-luokka"
        :pakollinen? true
        :vayla-tyyli? true
        :tyyppi :radio-group
        :nimi :lomake-luokka
        :vaihtoehdot (keys tiedot/mahd-pk-luokat)
        :vaihtoehto-nayta #(get tiedot/mahd-pk-luokat %)
        :validoi [#(when (nil? %) "Syötä jokin luokka, tai 'Ei PK-luokkaa'")]})

     (lomake/rivi
       {:otsikko "Summa"
        :nimi :hinta
        :tyyppi :euro
        :pakollinen? true
        :vayla-tyyli? true
        :teksti-oikealla "EUR"
        :validoi [[:ei-tyhja "Syötä kustannusarvo"]]
        ::lomake/col-luokka "col-xs-6 summa-valinta"})]
    valittu-rivi]])


(defn- suodattimet-aikavali [e!]
  [:div {:on-click #(e! (tiedot/->HaeTiedot))}
   [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]])


(defn- muut-kustannukset-grid [e! rivit haku-kaynnissa?]
  [grid/grid {:tyhja (if haku-kaynnissa?
                       [ajax-loader-pieni "Haku käynnissä..."]
                       "Aikavälille ei löytynyt tuloksia.")
              :tunniste :id
              :voi-kumota? false
              :piilota-toiminnot? true
              :sivuta grid/vakiosivutus
              :mahdollista-rivin-valinta? true
              :rivi-klikattu #(e! (tiedot/->AvaaKustannusModal %))}

   [{:otsikko-komp (fn [_ _]
                     [:div.pvm "Päivämäärä"
                      [:div [ikonit/action-sort-descending]]])
     :tyyppi :komponentti
     :komponentti (fn [arvo _] (str (pvm/pvm (:pvm arvo))))
     :luokka "caption text-nowrap"
     :leveys 0.2}

    {:otsikko "Tyyppi"
     :tyyppi :komponentti
     :komponentti (comp #(tiedot/tyyppi-valinnat %) :tyyppi)
     :luokka "text-nowrap"
     :leveys 0.2}

    {:otsikko "Selite"
     :tyyppi :string
     :nimi :selite
     :luokka "text-nowrap"
     :leveys 0.2}

    {:otsikko "Pk-luokka"
     :tyyppi :komponentti
     :komponentti (comp str :nimi :yllapitoluokka)
     :luokka "text-nowrap"
     :leveys 0.2}

    {:otsikko "Kustannus"
     :tyyppi :euro
     :tasaa :oikea
     :nimi :hinta
     :luokka "text-nowrap"
     :leveys 0.1}]
   rivit])


(defn muut-kustannukset-listaus [e! {:keys [rivit valinnat muokataan valittu-rivi
                                            haku-kaynnissa? kustannukset tyypit] :as app}]
  (let [alkuaika (:alkuaika valittu-rivi)
        ;; TODO 
        voi-kirjoittaa? true
        voi-tallentaa? true
        aikavali (suodattimet-aikavali e!)
        grid (muut-kustannukset-grid e! rivit haku-kaynnissa?)
        lisaa-uusi-fn #(e! (tiedot/->AvaaKustannusModal nil))
        muokkauspaneeli (muut-kustannukset-muokkauspaneeli e! valittu-rivi valinnat voi-kirjoittaa? voi-tallentaa? alkuaika tyypit)]

    (yhteiset/nakyma-body "Muut kustannukset"
      e! lisaa-uusi-fn aikavali
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
