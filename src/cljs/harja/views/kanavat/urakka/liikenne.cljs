(ns harja.views.kanavat.urakka.liikenne
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.urakka.liikenne :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.pvm :as pvm]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.modal :as modal]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.urakka.valinnat :as suodattimet]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]

            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as ur]
            [harja.domain.sopimus :as sop]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-nippu :as lt-nippu]
            [harja.domain.kanavat.kanavan-kohde :as kohde])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn liikennetapahtumalomake [e! app]
  [:div
   [napit/takaisin "Takaisin" #(e! (tiedot/->ValitseTapahtuma nil))]
   [:div "WIP"]])

(defn valinnat [e! {:keys [urakan-kohteet] :as app}]
  (let [atomi (partial tiedot/valinta-wrap e! app)]
    [valinnat/urakkavalinnat
     {}
     ^{:key "valinnat"}
     [suodattimet/urakan-sopimus-ja-hoitokausi-ja-aikavali @nav/valittu-urakka]
     [valinnat/kanava-kohde
      (atomi ::lt/kohde)
      (into [nil] urakan-kohteet)
      #(let [nimi (kohde/fmt-kohteen-kanava-nimi %)]
         (if-not (empty? nimi)
           nimi
           "Kaikki"))]
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Suunta"
       :kentta-params {:tyyppi :valinta
                       :valinnat (into [nil] lt/suunta-vaihtoehdot)
                       :valinta-nayta #(or (lt/suunta->str %) "Molemmat")}
       :arvo-atom (atomi ::lt-alus/suunta)}]
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Toimenpidetyyppi"
       :kentta-params {:tyyppi :valinta
                       :valinta-nayta #(or (lt/toimenpide->str %) "Kaikki")
                       :valinnat (into [nil] lt/toimenpide-vaihtoehdot)}
       :arvo-atom (atomi ::lt/toimenpide)}]
     [valinnat/kanava-aluslaji
      (atomi ::lt-alus/laji)
      (into [nil] lt-alus/aluslajit)
      #(or (lt-alus/aluslaji->str %) "Kaikki")]
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Uittoniput?"
       :kentta-params {:tyyppi :checkbox}
       :arvo-atom (atomi :niput?)}]]))

(defn liikennetapahtumataulukko [e! {:keys [tapahtumarivit
                                            liikennetapahtumien-haku-kaynnissa?] :as app}]
  [:div
   [debug app]
   [valinnat e! app]
   [napit/uusi
    "Lisää tapahtuma"
    #(e! (tiedot/->ValitseTapahtuma tiedot/uusi-tapahtuma))]
   [grid/grid
    {:otsikko (if liikennetapahtumien-haku-kaynnissa?
                [ajax-loader-pieni "Päivitetään listaa.."]
                "Liikennetapahtumat")
     :tunniste (juxt ::lt/id ::lt-alus/id ::lt-nippu/id)
     :tyhja (if liikennetapahtumien-haku-kaynnissa?
              [ajax-loader "Haku käynnissä"]
              "Ei liikennetapahtumia")}
    [{:otsikko "Aika"
      :nimi ::lt/aika
      :fmt pvm/pvm-aika-opt}
     {:otsikko "Kohde"
      :nimi :kohteen-nimi}
     {:otsikko "Tyyppi"
      :nimi ::lt/toimenpide
      :fmt lt/toimenpide->str}
     {:otsikko "Suunta"
      :nimi :suunta
      :fmt lt/suunta->str}
     {:otsikko "Alus"
      :nimi ::lt-alus/nimi}
     {:otsikko "Aluslaji"
      :nimi ::lt-alus/laji
      :fmt lt-alus/aluslaji->str}
     {:otsikko "Matkustajia"
      :nimi ::lt-alus/matkustajalkm}
     {:otsikko "Aluksia"
      :nimi ::lt-alus/lkm}
     {:otsikko "Palvelumuoto"
      :nimi :palvelumuoto-ja-lkm
      :hae tiedot/palvelumuoto->str}
     {:otsikko "Nippuja"
      :nimi ::lt-nippu/lkm}
     {:otsikko "Ylävesi"
      :nimi ::lt/vesipinta-ylaraja}
     {:otsikko "Alavesi"
      :nimi ::lt/vesipinta-alaraja}
     {:otsikko "Kuittaaja"
      :nimi :kuittaaja
      :hae (comp ::kayttaja/kayttajanimi ::lt/kuittaaja)}]
    (sort-by
      ;; Tarvitaan aika monta vaihtoehtoista sorttausavainta, koska
      ;; yhdelle kohteelle voi tulla yhdellä kirjauksella aika monta riviä
      (juxt ::lt/aika
            :kohteen-nimi
            ::lt/toimenpide
            ::lt-alus/laji
            ::lt-alus/nimi
            ::lt-alus/lkm)
      tapahtumarivit)]])

(defn liikenne* [e! app valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           ;; Valintojen päivittäminen laukaisee myös liikennetapahtumien haun
                           (e! (tiedot/->PaivitaValinnat {::ur/id (:urakka valinnat)
                                                          ::sop/id (:sopimus valinnat)
                                                          :aikavali (:aikavali valinnat)}))
                           (e! (tiedot/->HaeKohteet)))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {:keys [valittu-liikennetapahtuma] :as app}]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.
      (if-not valittu-liikennetapahtuma
        [liikennetapahtumataulukko e! app]
        [liikennetapahtumalomake e! app]))))

(defn liikennetapahtumat [e! app]
  [liikenne* e! app {:urakka (:id @nav/valittu-urakka)
                     :aikavali @u/valittu-aikavali
                     :sopimus (first @u/valittu-sopimusnumero)}])

(defc liikenne []
  [tuck tiedot/tila liikennetapahtumat])

