(ns harja.views.urakka.kohdeluettelo.toteumat
  "Urakan kohdeluettelon toteumat"
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]

            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :as napit]
            [clojure.string :as str]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.paallystys :as paallystys])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(def lomakedata (atom nil))

(def lomaketestidata
  "Oletetaan, että tieto tulee kannasta tässä muodossa (joinataan SQL-kyselyssä ainakin kohdennimi ja tarjoushinta.)"
  {:kohde        308
   :kohdenimi    "Leppäkorven rampit"
   :tarjoushinta 90000
   :valmispvm    (pvm/luo-pvm 2015 10 10)
   :takuupvm     (pvm/luo-pvm 2016 05 11)
   :hinta        5000

   :osoitteet    [{:tie     2846 :aosa 5 :aet 22 :losa 5 :let 9377
                   :ajorata 0 :suunta 0 :kaista 1}
                  {:tie     2846 :aosa 5 :aet 22 :losa 5 :let 9377
                   :ajorata 0 :suunta 0 :kaista 1}]

   :toimenpiteet [{:paallystetyyppi           21
                   :raekoko                   16
                   :massa                     100
                   :rc%                       0
                   :tyomenetelma              12
                   :leveys                    6.5
                   :massamaara                1781
                   :edellinen-paallystetyyppi 12
                   }
                  {:paallystetyyppi           21
                   :raekoko                   10
                   :massa                     512
                   :rc%                       0
                   :tyomenetelma              12
                   :leveys                    4
                   :massamaara                1345
                   :edellinen-paallystetyyppi 11
                   }]

   :kiviaines    [{:esiintyma "KAM Leppäsenoja"
                   :km-arvo   "An 14"
                   :muotoarvo "Fi 20"
                   :sideaine  {:tyyppi "B650/900" :pitoisuus 4.3 :lisaaineet "Tartuke"}}]

   :alustatoimet [{:tie 5
                   :aosa 22
                   :aet 3
                   :losa 5
                   :let 4785
                   :kasittelymenetelma  13
                   :paksuus             30
                   :verkkotyyppi        2
                   :tekninen-toimenpide 2
                   }]

   :tyot         [{:tyyppi           :ajoradan-paallyste
                   :toimenpidekoodi  1350
                   :tilattu-maara    10000
                   :toteutunut-maara 10100
                   :yksikkohinta     20}]})

(defn kohteen-tiedot []
  (let [valmispvm (r/wrap (:valmispvm @lomakedata) (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :valmispvm uusi-arvo))))
        takuupvm (r/wrap (:takuupvm @lomakedata) (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :takuupvm uusi-arvo))))
        toteutunut-hinta (r/wrap (:hinta @lomakedata) (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :hinta uusi-arvo))))]

  [:div.paallystysilmoitus-kohteen-tiedot
   [:h6 "Kohteen tiedot"]
   [:span.paallystysilmoitus-kohteen-tiedot-otsikko "Kohde"] [:span (:kohde @lomakedata) " " (:kohdenimi @lomakedata)]
   [:span.paallystysilmoitus-kohteen-tiedot-otsikko "Valmistumispvm"] [:span [tee-kentta {:tyyppi :pvm} valmispvm]]
   [:span.paallystysilmoitus-kohteen-tiedot-otsikko "Takuupvm"] [:span [tee-kentta {:tyyppi :pvm} takuupvm]]
   [:span.paallystysilmoitus-kohteen-tiedot-otsikko "Toteutunut hinta"] [:span [tee-kentta {:tyyppi :numero} toteutunut-hinta]] [:span " €"]]))

(tarkkaile! "PÄÄ Lomakedata: " lomakedata)

(defn yhteenveto []
  (let [urakkasopimuksen-mukainen-kokonaishinta (reaction (:tarjoushinta @lomakedata))
        muutokset-kokonaishintaan ; Lasketaan jokaisesta työstä muutos tilattuun hintaan (POT-Excelistä "Muutos hintaan") ja summataan yhteen.
        (reaction (reduce + (mapv
                              (fn [tyo]
                                (* (- (:toteutunut-maara tyo) (:tilattu-maara tyo)) (:yksikkohinta tyo)))
                              (:tyot @lomakedata))))
        yhteensa (reaction (+ @urakkasopimuksen-mukainen-kokonaishinta @muutokset-kokonaishintaan))]
    [:div.paallystysilmoitus-yhteenveto
   [:table
    [:tr
     [:td.paallystysilmoitus-yhteenveto-nimi [:span "Urakkasopimuksen mukainen kokonaishinta: "]]
     [:td.paallystysilmoitus-yhteenveto-summa [:span (str @urakkasopimuksen-mukainen-kokonaishinta " €")]]]
    [:tr
     [:td.paallystysilmoitus-yhteenveto-nimi [:span "Muutokset kokonaishintaan ilman kustannustasomuutoksia: "]]
     [:td.paallystysilmoitus-yhteenveto-summa [:span (str @muutokset-kokonaishintaan " €")]]]
    [:tr
     [:td.paallystysilmoitus-yhteenveto-nimi [:span "Yhteensä: "]]
     [:td.paallystysilmoitus-yhteenveto-summa [:span (str @yhteensa " €")]]]]]))

(defn paallystysilmoituslomake
  []
  (let [toteutuneet-osoitteet (r/wrap (zipmap (iterate inc 1) (:osoitteet @lomakedata)) (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :osoitteet (vals uusi-arvo)))))
        paallystystoimenpide (r/wrap (zipmap (iterate inc 1) (:toimenpiteet @lomakedata)) (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :toimenpiteet (vals uusi-arvo)))))
        alustalle-tehdyt-toimet (r/wrap (zipmap (iterate inc 1) (:alustatoimet @lomakedata)) (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :alustatoimet (vals uusi-arvo)))))
        toteutuneet-maarat (r/wrap (zipmap (iterate inc 1) (:tyot @lomakedata)) (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :tyot (vals uusi-arvo)))))
        kiviaines (r/wrap (zipmap (iterate inc 1) (:kiviaines @lomakedata)) (fn [uusi-arvo] (reset! lomakedata (assoc @lomakedata :kiviaines (vals uusi-arvo)))))]

    (komp/luo
      (fn [ur]
        [:div.paallystysilmoituslomake

         [:button.nappi-toissijainen {:on-click #(reset! lomakedata nil)}
          (ikonit/chevron-left) " Takaisin toteumaluetteloon"]

         (kohteen-tiedot)

         [grid/muokkaus-grid
          {:otsikko  "Alikohteet"
           :tunniste :tie}
          [{:otsikko "Tie#" :nimi :tie :tyyppi :numero :leveys "10%"}
           {:otsikko "Ajorata" :nimi :ajorata :tyyppi :string :leveys "20%"} ; FIXME Pudotusvalikko
           {:otsikko "Suunta" :nimi :suunta :tyyppi :numero :leveys "10%"} ; FIXME Pudotusvalikko
           {:otsikko "Kaista" :nimi :kaista :leveys "10%" :tyyppi :numero} ; FIXME Pudotusvalikko
           {:otsikko "Alkutieosa" :nimi :aosa :leveys "10%" :tyyppi :numero}
           {:otsikko "Alkuetäisyys" :nimi :aet :leveys "10%" :tyyppi :numero}
           {:otsikko "Lopputieosa" :nimi :losa :leveys "10%" :tyyppi :numero}
           {:otsikko "Loppuetäisyys" :nimi :let :leveys "10%" :tyyppi :numero}
           {:otsikko "Pituus (m)" :nimi :pituus :leveys "10%" :tyyppi :numero}]
          toteutuneet-osoitteet]

         [grid/muokkaus-grid
          {:otsikko "Päällystystoimenpiteen tiedot"
           :voi-lisata? false
           :voi-poistaa? (constantly false)}
          [{:otsikko "Päällystetyyppi" :nimi :paallystetyyppi :tyyppi :string :leveys "20%"} ; FIXME Pudotusvalikko
           {:otsikko "Raekoko" :nimi :raekoko :tyyppi :numero :leveys "10%"}
           {:otsikko "Massa (kg/m2)" :nimi :massa :tyyppi :numero :leveys "10%"}
           {:otsikko "RC-%" :nimi :rc% :leveys "10%" :tyyppi :numero}
           {:otsikko "Pääll.työmenetelmä" :nimi :tyomenetelma :leveys "20%" :tyyppi :string} ; FIXME Pudotusvalikko
           {:otsikko "Leveys (m)" :nimi :leveys :leveys "10%" :tyyppi :numero}
           {:otsikko "Massamäärä (mt)" :nimi :massamaara :leveys "10%" :tyyppi :numero}
           {:otsikko "Pinta-ala (m2)" :nimi :pinta-ala :leveys "10%" :tyyppi :numero}
           {:otsikko "Edellinen päällyste" :nimi :edellinen-paallystettyyppi :leveys "20%" :tyyppi :string}] ; FIXME Pudostusvalikko
          paallystystoimenpide]

         [grid/muokkaus-grid
          {:otsikko "Kiviaines ja sideaine"}
          [{:otsikko "Kiviaines-esiintymä" :nimi :esiintyma :tyyppi :string :leveys "30%"}
           {:otsikko "KM-arvo" :nimi :km-arvo :tyyppi :string :leveys "20%"}
           {:otsikko "Muotoarvo" :nimi :muotoarvo :tyyppi :string :leveys "20%"}
           {:otsikko "Sideaine-tyyppi" :nimi :tyyppi :leveys "30%" :tyyppi :string} ; FIXME Pudostusvalikko
           {:otsikko "Pitoisuus" :nimi :pitoisuus :leveys "20%" :tyyppi :numero}
           {:otsikko "Lisäaineet" :nimi :lisaaineet :leveys "20%" :tyyppi :string}]
          kiviaines]

         [grid/muokkaus-grid
          {:otsikko "Alustalle tehdyt toimet"}
          [{:otsikko "Alkutieosa" :nimi :aosa :tyyppi :string :leveys "10%"}
           {:otsikko "Alkuetäisyys" :nimi :aet :tyyppi :numero :leveys "10%"}
           {:otsikko "Lopputieosa" :nimi :losa :tyyppi :numero :leveys "10%"}
           {:otsikko "Loppuetäisyys" :nimi :let :leveys "10%" :tyyppi :numero}
           {:otsikko "Pituus" :nimi :pituus :leveys "10%" :tyyppi :string}
           {:otsikko "Käsittelymenetelmä" :nimi :kasittelymenetelma :leveys "20%" :tyyppi :numero} ; FIXME Pudostusvalikko
           {:otsikko "Käsittelypaks. (cm)" :nimi :paksuus :leveys "10%" :tyyppi :numero}
           {:otsikko "Tekn. tp." :nimi :tekninen-toimenpide :leveys "20%" :tyyppi :numero}] ; FIXME Pudostusvalikko
          alustalle-tehdyt-toimet]

         [grid/muokkaus-grid
          {:otsikko "Toteutuneet määrät"}
          [{:otsikko "Ajoradan päällyste" :nimi :tyyppi :tyyppi :string :leveys "20%"} ; FIXME Pudostusvalikko
           {:otsikko "Yks." :nimi :yksikko :tyyppi :string :leveys "10%"}
           {:otsikko "Tilattu määrä" :nimi :tilattu-maara :tyyppi :numero :leveys "15%"}
           {:otsikko "Toteutunut määrä" :nimi :toteutunut-maara :leveys "15%" :tyyppi :numero}
           {:otsikko "Ero" :nimi :ero :leveys "15%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (- (:toteutunut-maara rivi) (:tilattu-maara rivi)))}
           {:otsikko "Yks.hinta" :nimi :yksikkohinta :leveys "10%" :tyyppi :numero}
           {:otsikko "Muutos hintaan" :nimi :muutos-hintaan :leveys "15%" :muokattava? (constantly false) :tyyppi :numero :hae (fn [rivi] (* (- (:toteutunut-maara rivi) (:tilattu-maara rivi)) (:yksikkohinta rivi)))}]
          toteutuneet-maarat]

         (yhteenveto)

         [:button.nappi-ensisijainen.laheta-paallystysilmoitus {:on-click #(do (.preventDefault %))} "Lähetä ilmoitus"]

         ]))))

(defonce toteumarivit (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                   [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                   valittu-urakan-valilehti @u/urakan-valittu-valilehti]
                                  (when (and valittu-urakka-id valittu-sopimus-id (= valittu-urakan-valilehti :kohdeluettelo)) ; FIXME Alivälilehti myös valittuna
                                    (log "PÄÄ Haetaan päällystystoteumat.")
                                    (paallystys/hae-paallystystoteumat valittu-urakka-id valittu-sopimus-id))))

(defn toteumaluettelo
  []
  (let []

    (komp/luo
      (fn []
        [:div

         [:button.nappi-ensisijainen {:on-click
                                      ;#(reset! lomake-paallystysilmoitus {}) ; FIXME Käytä tätä kun testidataa ei tarvita
                                      #(reset! lomakedata lomaketestidata)
                                      }
          (ikonit/plus-sign) " Lisää päällystysilmoitus"]

         [grid/grid
          {:otsikko  "Toteumat"
           :tyhja    (if (nil? @toteumarivit) [ajax-loader "Haetaan toteumia..."] "Ei toteumia")
           :tunniste :kohdenumero}
          [{:otsikko "#" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
           {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys "50%"}
           {:otsikko "Tila" :nimi :tila :muokattava? (constantly false) :tyyppi :string :leveys "20%"}
           {:otsikko     "Päällystysilmoitus" :nimi :paallystysilmoitus :muokattava? (constantly false) :leveys "25%" :tyyppi :komponentti
            :komponentti (fn [rivi] [:button.nappi-toissijainen.nappi-grid {:on-click #(go ())}
                                     (ikonit/eye-open) " Päällystysilmoitus"])}]
          @toteumarivit]]))))

(defn toteumat []
  (if @lomakedata
    [paallystysilmoituslomake]
    [toteumaluettelo]))