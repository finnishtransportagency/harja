(ns harja-laadunseuranta.ui.havaintolomake
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.ui.kamera :as kamera]
            [harja-laadunseuranta.tiedot.paanavigointi :as paanavigointi]
            [harja-laadunseuranta.tiedot.havaintolomake :refer [alusta-uusi-lomake!
                                                                tallenna-lomake!
                                                                peruuta-lomake!]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.ui.yleiset.lomake :as lomake]
            [cljs-time.format :as time-fmt]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [harja-laadunseuranta.ui.yleiset.yleiset :as yleiset])

  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(defn- havaintolomakekomponentti [{:keys [lomakedata tallenna-fn peruuta-fn
                                          tr-osoite-lomakkeen-avauksessa
                                          liittyvat-havainnot havainnot-ryhmittain]}]
  (let [kuvaus-atom (reagent/cursor lomakedata [:kuvaus])
        aikaleima-atom (reagent/cursor lomakedata [:aikaleima])
        tr-osoite-atom (reagent/cursor lomakedata [:tr-osoite])
        esikatselukuva-atom (reagent/cursor lomakedata [:esikatselukuva])
        kayttajanimi-atom (reagent/cursor lomakedata [:kayttajanimi])
        laadunalitus-atom (reagent/cursor lomakedata [:laadunalitus?])
        lomake-liittyy-havaintoon-atom (reagent/cursor lomakedata [:liittyy-havaintoon])
        lomake-virheet-atom (atom #{})
        alusta-tr-osoite! (fn [tr-osoite-atom]
                            (when (:tie tr-osoite-lomakkeen-avauksessa)
                              (reset! tr-osoite-atom tr-osoite-lomakkeen-avauksessa)))]
    (alusta-tr-osoite! tr-osoite-atom)
    (fn []
      [lomake/lomake
       {:otsikko "Havainnon perustiedot"
        :peruuta-fn peruuta-fn
        :tallenna-fn tallenna-fn
        :lomakedata-atom lomakedata
        :lomake-virheet-atom lomake-virheet-atom}

       (when-not (empty? liittyvat-havainnot)
         [lomake/rivi
          [lomake/kentta "Lomake liittyy havaintoon"
           [lomake/liittyvat-havainnot
            {:liittyvat-havainnot liittyvat-havainnot
             :lomake-liittyy-havaintoon-atom lomake-liittyy-havaintoon-atom
             :havainnot-ryhmittain havainnot-ryhmittain}]]])

       [lomake/rivi
        [lomake/kentta "Päivämäärä"
         [:span (fmt/pvm-klo @aikaleima-atom)]]
        [lomake/kentta "Tarkastaja"
         [:span @kayttajanimi-atom]]]

       [lomake/rivi
        [lomake/kentta "Tieosuus"
         [lomake/tr-osoite
          {:tr-osoite-atom tr-osoite-atom
           :virheet-atom lomake-virheet-atom
           :muokattava? (some? @lomake-liittyy-havaintoon-atom)}]]]

       [lomake/rivi
        [lomake/kentta ""
         [lomake/checkbox "Laadunalitus" laadunalitus-atom]]]

       [lomake/rivi
        [lomake/kentta "Lisätietoja"
         [lomake/tekstialue kuvaus-atom]]
        [lomake/kentta ""
         [kamera/kamerakomponentti esikatselukuva-atom]]]])))

(defn havaintolomake []
  (let [lomakedata (alusta-uusi-lomake!)
        tr-osoite-lomakkeen-avauksessa @s/tr-osoite]
    (fn []
      [havaintolomakekomponentti
       {:lomakedata lomakedata
        :tr-osoite-lomakkeen-avauksessa tr-osoite-lomakkeen-avauksessa
        :tallenna-fn tallenna-lomake!
        :havainnot-ryhmittain paanavigointi/havainnot-ryhmittain
        :peruuta-fn peruuta-lomake!
        :liittyvat-havainnot @s/liittyvat-havainnot}])))

(def test-model (atom {:kayttajanimi "Jalmari Järjestelmävastuuhenkilö"
                       :tr-osoite {:tie 20 :aosa 3 :aet 3746}
                       :aikaleima (l/local-now)
                       :kuvaus ""
                       :havainnot {:tasaisuus 5
                                   :kitkamittaus 0.45
                                   :lampotila -12
                                   :lumisuus 3}
                       :sijainti {:lon 428147
                                  :lat 7208956
                                  :heading 45}}))

(defcard havaintolomake-card
  (fn [_ _]
    (reagent/as-element [havaintolomake (str "http://localhost:8000" asetukset/+wmts-url+)
                         (str "http://localhost:8000" asetukset/+wmts-url-kiinteistojaotus+)
                         (str "http://localhost:8000" asetukset/+wmts-url-ortokuva+) test-model #() #()]))
  test-model
  {:watch-atom true
   :inspect-data true})
