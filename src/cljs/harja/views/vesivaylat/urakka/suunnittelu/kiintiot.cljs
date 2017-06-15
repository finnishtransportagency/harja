(ns harja.views.vesivaylat.urakka.suunnittelu.kiintiot
  (:require [tuck.core :as tuck]
            [harja.ui.grid :as grid]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.tiedot.vesivaylat.urakka.suunnittelu.kiintiot :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]))


(defn kiintiot* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (e! (tiedot/->HaeKiintiot)))
                      #(do (e! (tiedot/->Nakymassa? false))))
    #_(komp/sisaan #(e! (tiedot/->PaivitaUrakka @nav/valittu-urakka)))
    #_(komp/watcher nav/valittu-urakka (fn [_ _ ur]
                                       (e! (tiedot/->PaivitaUrakka ur))))
    (fn [e! app]
      [grid/muokkaus-grid
       {:otsikko ""
        :voi-muokata? (constantly true) ;;TODO oikeustarkastus
        :voi-poistaa? (constantly false) ;;TODO oikeustarkastus
        :tyhja "Ei määriteltyjä kiintiöitä"
        :jarjesta ::kiintio/nimi
        :tunniste ::kiintio/id
        :uusi-rivi (fn [rivi] rivi)
        }
       [{:otsikko "Nimi"
         :nimi ::kiintio/nimi}
        {:otsikko "Kuvaus"
         :nimi ::kiintio/kuvaus}
        {:otsikko "Koko"
         :nimi ::kiintio/koko}
        {:otsikko "Toteutunut"
         :nimi :toteutunut
         :hae (constantly 0)}]])))

(defn kiintiot []
  [tuck/tuck tiedot/app kiintiot*])
