(ns harja.tiedot.urakka.laadunseuranta.tarkastukset-kartalla
  (:require [reagent.core :as r]
            [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.openlayers :as openlayers]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.urakka :as u-domain])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce karttataso-tarkastukset (r/atom false))

(defonce karttataso-tieturvallisuusverkko (reaction
                                            (and
                                              @karttataso-tarkastukset
                                              @tarkastukset/nakymassa?
                                              (= :tieturvallisuus @tarkastukset/tarkastustyyppi))))

(defonce karttataso-ei-kayty-tieturvallisuusverkko (reaction
                                                     (and
                                                       @tarkastukset/nakymassa?
                                                       (= :tieturvallisuus @tarkastukset/tarkastustyyppi)
                                                       (= :ei-kayty @tarkastukset/valittu-karttataso))))

(defonce karttataso-tieturvallisuus-heatmap (reaction
                                              (and
                                                @tarkastukset/nakymassa?
                                                (= :tieturvallisuus @tarkastukset/tarkastustyyppi)
                                                (= :kayntimaarat @tarkastukset/valittu-karttataso))))

(defn- luo-tarkastusreitit-kuvataso [taso-paalla? urakkatyyppi parametrit]
  (when taso-paalla?
    (openlayers/luo-kuvataso
     :tarkastusreitit (if (not (u-domain/vesivaylaurakkatyyppi? urakkatyyppi))
                        esitettavat-asiat/tarkastus-selitteet-reiteille
                        esitettavat-asiat/tarkastus-selitteet-ikoneille)
     "tr" (k/url-parametri (assoc parametrit
                             :valittu {:id (:id @tarkastukset/valittu-tarkastus)})))))

(defonce tieturvallisuusverkko-kartalla
  (reaction<!
    [paalla? @karttataso-tieturvallisuusverkko
     urakka @nav/valittu-urakka-id]
    (when (and paalla? urakka)
      (go
        (esitettavat-asiat/kartalla-esitettavaan-muotoon
          (<! (k/post! :hae-urakan-tieturvallisuusverkko
                {:urakka-id urakka})))))))

(defonce ei-kayty-tieturvallisuusverkko-kartalla
  (reaction<!
    [paalla? @karttataso-ei-kayty-tieturvallisuusverkko
     urakka @nav/valittu-urakka-id
     [alkupvm loppupvm] (tarkastukset/naytettava-aikavali
                          @tiedot-urakka/valittu-urakka-kaynnissa?
                          @tiedot-urakka/valittu-hoitokausi
                          @tiedot-urakka/valittu-hoitokauden-kuukausi
                          @tarkastukset/valittu-aikavali)]
    (when (and paalla? urakka)
      (go
        (esitettavat-asiat/kartalla-esitettavaan-muotoon
          (<! (k/post! :hae-tarkastamattomat-tiet
                {:urakka-id urakka
                 :alkupvm alkupvm
                 :loppupvm loppupvm})))))))

(defonce tieturvallisuus-heatmap-kartalla
  (reaction<!
    [paalla? @karttataso-tieturvallisuus-heatmap
     urakka @nav/valittu-urakka-id
     [alkupvm loppupvm] (tarkastukset/naytettava-aikavali
                          @tiedot-urakka/valittu-urakka-kaynnissa?
                          @tiedot-urakka/valittu-hoitokausi
                          @tiedot-urakka/valittu-hoitokauden-kuukausi
                          @tarkastukset/valittu-aikavali)]
    (when (and paalla? urakka)
      (go
        (esitettavat-asiat/kartalla-esitettavaan-muotoon
          (<! (k/post! :hae-tarkastuspisteet-heatmapille
                (tarkastukset/kasaa-heatmap-parametrit urakka alkupvm loppupvm))))))))

(def tarkastusreitit-kartalla
  (reaction
    @tarkastukset/urakan-tarkastukset
    (luo-tarkastusreitit-kuvataso
      @karttataso-tarkastukset
      (:tyyppi @nav/valittu-urakka)
      (tarkastukset/kasaa-haun-parametrit
        @tiedot-urakka/valittu-urakka-kaynnissa?
        @nav/valittu-urakka-id
        @tiedot-urakka/valittu-hoitokausi
        @tiedot-urakka/valittu-hoitokauden-kuukausi
        @tarkastukset/valittu-aikavali
        @tarkastukset/tienumero @tarkastukset/tarkastustyyppi
        @tarkastukset/havaintoja-sisaltavat?
        @tarkastukset/vain-laadunalitukset?
        @tarkastukset/tarkastuksen-tekija))))
