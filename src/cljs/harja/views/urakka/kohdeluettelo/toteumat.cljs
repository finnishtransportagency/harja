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
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.paallystys :as paallystys])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(def lomake-paallystysilmoitus (atom nil))

(def lomaketestidata
  {:kohde 308
   :valmispvm "2005-11-14 00:00:00+02"
   :hinta 5000

   :osoitteet [{:tie 2846 :aosa 5 :aet 22 :losa 5 :let 9377
                :ajorata 0 :suunta 0 :kaista 1}
               {:tie 2848 :aosa 5 :aet 22 :losa 5 :let 9377
                :ajorata 0 :suunta 0 :kaista 1}]

   :toimenpiteet [{:paallystetyyppi 21
                   :raekoko 16
                   :massa 100
                   :rc% 0
                   :tyomenetelma 12
                   :leveys 6.5
                   :massamaara 1781
                   :edellinen-paallystetyyppi 12
                   }
                  {:paallystetyyppi 21
                   :raekoko 10
                   :massa 512
                   :rc% 0
                   :tyomenetelma 12
                   :leveys 4
                   :massamaara 1345
                   :edellinen-paallystetyyppi 11
                   }]

   :kiviaines [{:esiintymä "KAM Leppäsenoja"
                :km-arvo "An 14"
                :muotoarvo "Fi 20"
                :sideaine {:tyyppi "B650/900" :pitoisuus 4.3 :lisaaineet "Tartuke"}}]

   :alustatoimet [{:tie 5 :aosa 22 :aet 3 :losa 5 :let 4785
                   :kasittelymenetelma {:nimi "Remix-stabilointi"        :lyhenne "REST" :koodi 13}
                   :paksuus 30
                   :verkkotyyppi {:nimi "Lasikuituverkko" :koodi 2}
                   :tekninen-toimenpide {:nimi "Suuntauksen parantaminen" :koodi 2}
                   }]

   :tyot [{:tyyppi :ajoradan-paallyste
           :toimenpidekoodi 1350
           :tilattu-maara 1000
           :toteutunut-maara 5800
           :yksikkohinta 20}]})

(defn paallystysilmoituslomake
  []
  (let []

    (komp/luo
      (fn [ur]
        [:div
         [:p "TODO Tähän tulee tosi iso ja monimutkainen päällystysilmoituslomake :)"]]))))

(defonce toteumarivit (reaction<! (let [valittu-urakka-id (:id @nav/valittu-urakka)
                                        [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                        valittu-urakan-valilehti @u/urakan-valittu-valilehti]
                                    (when (and valittu-urakka-id valittu-sopimus-id (= valittu-urakan-valilehti :kohdeluettelo)) ; FIXME Alivälilehti myös valittuna
                                      (log "PÄÄ Haetaan päällystystoteumat.")
                                      (paallystys/hae-paallystystoteumat valittu-urakka-id valittu-sopimus-id)))))

(defn toteumaluettelo
  []
  (let []

    (komp/luo
      (fn []
        [:div

         [:button.nappi-ensisijainen {:on-click
                                      ;#(reset! lomake-paallystysilmoitus {}) ; FIXME Käytä tätä kun testidataa ei tarvita
                                      #(reset! lomake-paallystysilmoitus lomaketestidata)
                                      }
          (ikonit/plus-sign) " Lisää päällystysilmoitus"]

         [grid/grid
          {:otsikko "Toteumat"
           :tyhja (if (nil? @toteumarivit) [ajax-loader "Haetaan toteumia..."] "Ei toteumia")}
          [{:otsikko "#" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
           {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys "50%"}
           {:otsikko "Tila" :nimi :tila :muokattava? (constantly false) :tyyppi :string :leveys "20%"}
           {:otsikko "Päällystysilmoitus" :nimi :paallystysilmoitus :muokattava? (constantly false) :leveys "25%" :tyyppi :komponentti
            :komponentti (fn [rivi] [:button.nappi-toissijainen.nappi-grid {:on-click #(go ())}
                                     (ikonit/eye-open) " Päällystysilmoitus"])}]
          @toteumarivit]]))))

(defn toteumat []
  (if @lomake-paallystysilmoitus
    [paallystysilmoituslomake]
    [toteumaluettelo]))