(ns harja.views.kanavat.urakka.liikenne
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.urakka.liikenne :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.modal :as modal]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.urakka.valinnat :as suodattimet]
            [harja.ui.napit :as napit]

            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-nippu :as lt-nippu])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn liikennetapahtumalomake [e! app]
  [:div
   [napit/takaisin "Takaisin" #(e! (tiedot/->ValitseTapahtuma nil))]
   [:div "WIP"]])

(defn liikennetapahtumataulukko [e! {:keys [tapahtumarivit
                                            liikennetapahtumien-haku-kaynnissa?] :as app}]
  [:div
   [debug app]
   [valinnat/urakkavalinnat
    {}
    ^{:key "valinnat"}
    [suodattimet/urakan-sopimus-ja-hoitokausi-ja-aikavali @nav/valittu-urakka]]
   [napit/uusi
    "Lisää tapahtuma"
    #(e! (tiedot/->ValitseTapahtuma tiedot/uusi-tapahtuma))]
   [grid/grid
    {:otsikko "Liikennetapahtumat"
     :tunniste (juxt ::lt/id ::lt-alus/id ::lt-nippu/id)
     :tyhja (if liikennetapahtumien-haku-kaynnissa?
              [ajax-loader "Haku käynnissä"]
              "Ei liikennetapahtumia")}
    [{:otsikko "Päivämäärä"
      :nimi ::lt/aika
      :fmt pvm/pvm-aika-opt}
     {:otsikko "Kohde"
      :nimi :kohteen-nimi}
     {:otsikko "Tyyppi"
      :nimi ::lt/toimenpide
      :fmt lt/toimenpide->str}
     {:otsikko "Suunta"
      :nimi :suunta}
     {:otsikko "Alus"
      :nimi ::lt-alus/nimi}
     {:otsikko "Aluslaji"
      :nimi ::lt-alus/laji}
     {:otsikko "Matkustajia"
      :nimi ::lt-alus/matkustajalkm}
     {:otsikko "Aluksia"
      :nimi ::lt-alus/lkm}
     {:otsikko "IP kpl"
      :nimi ::lt/palvelumuoto-lkm}
     {:otsikko "Nippuja"
      :nimi ::lt-nippu/lkm}
     {:otsikko "Ylävesi"
      :nimi ::lt/vesipinta-ylaraja}
     {:otsikko "Alavesi"
      :nimi ::lt/vesipinta-alaraja}
     {:otsikko "Kuittaaja"
      :nimi :kuittaaja
      :hae (comp ::kayttaja/kayttajanimi ::lt/kuittaaja)}]
    tapahtumarivit]])

(defn liikenne* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->HaeLiikennetapahtumat)))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {:keys [valittu-liikennetapahtuma] :as app}]
      (if-not valittu-liikennetapahtuma
        [liikennetapahtumataulukko e! app]
        [liikennetapahtumalomake e! app]))))

(defc liikenne []
  [tuck tiedot/tila liikenne*])

