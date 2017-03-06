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
                                          kuvaa-otetaan-atom lomake-koskettu-atom
                                          liittyvat-havainnot havainnot-ryhmittain]}]
  (let [kuvaus-atom (reagent/cursor lomakedata [:kuvaus])
        aikaleima-atom (reagent/cursor lomakedata [:aikaleima])
        tr-osoite-atom (reagent/cursor lomakedata [:tr-osoite])
        esikatselukuva-atom (reagent/cursor lomakedata [:esikatselukuva])
        kayttajanimi-atom (reagent/cursor lomakedata [:kayttajanimi])
        laadunalitus-atom (reagent/cursor lomakedata [:laadunalitus?])
        lomake-liittyy-havaintoon-atom (reagent/cursor lomakedata [:liittyy-havaintoon])
        liittyy-varmasti-tiettyyn-havaintoon? (reagent/cursor lomakedata [:liittyy-varmasti-tiettyyn-havaintoon?])
        lomake-virheet-atom (atom #{})
        alusta-tr-osoite! (fn [tr-osoite-atom]
                            (when (:tie tr-osoite-lomakkeen-avauksessa)
                              (reset! tr-osoite-atom tr-osoite-lomakkeen-avauksessa)))]
    (alusta-tr-osoite! tr-osoite-atom)
    (fn []
      ^{:key "Havaintolomake"}
      [lomake/lomake
       {:otsikko "Havainnon perustiedot"
        :peruuta-fn peruuta-fn
        :tallenna-fn tallenna-fn
        :lomakedata-atom lomakedata
        :lomake-koskettu-atom lomake-koskettu-atom
        :lomake-virheet-atom lomake-virheet-atom}

       (when-not (empty? liittyvat-havainnot)
         ^{:key "Lomake liittyy havaintoon rivi"}
         [lomake/rivi
          ^{:key "Lomake liittyy havaintoon"}
          [lomake/kentta (if @liittyy-varmasti-tiettyyn-havaintoon?
                           "Lomake liittyy havaintoon"
                           ;; Jos on teksti 'liitty havaintoon' ja lomakkeella on vain yksi vaihtoehto
                           ;; valittavissa -> voi syntyä käsitys, että lomake liittyy tähän asiaan, vaikka
                           ;; kyseessä on vain ehdotus. Siksi eri otsikko eri tilanteeseen.
                           "Liitä lomake havaintoon?")
           [lomake/liittyvat-havainnot
            {:liittyvat-havainnot liittyvat-havainnot
             :lomake-liittyy-havaintoon-atom lomake-liittyy-havaintoon-atom
             :liittyy-varmasti-tiettyyn-havaintoon? @liittyy-varmasti-tiettyyn-havaintoon?
             :havainnot-ryhmittain havainnot-ryhmittain}]]])

       ^{:key "pvm-rivi"}
       [lomake/rivi
        ^{:key "Päivämäärä"}
        [lomake/kentta "Päivämäärä"
         [:span (fmt/pvm-klo @aikaleima-atom)]]
        ^{:key "Tarkastaja"}
        [lomake/kentta "Tarkastaja"
         [:span @kayttajanimi-atom]]]

       ^{:key "Tieosuusrivi"}
       [lomake/rivi
        ^{:key "Tieosuus"}
        [lomake/kentta "Tieosuus"
         [lomake/tr-osoite
          {:tr-osoite-atom tr-osoite-atom
           :virheet-atom lomake-virheet-atom
           :liittyva-havainto (first (filter #(= (:id %) @lomake-liittyy-havaintoon-atom)
                                             liittyvat-havainnot))}]]]

       ^{:key "Laadunalitusrivi"}
       [lomake/rivi
        ^{:key "Laadunalitus"}
        [lomake/kentta ""
         [lomake/checkbox "Laadunalitus" laadunalitus-atom]]]

       ^{:key "Lisätietorivi"}
       [lomake/rivi
        ^{:key "Lisätietokenttä"}
        [lomake/kentta "Lisätietoja"
         [lomake/tekstialue kuvaus-atom]]
        ^{:key "Kamera"}
        [lomake/kentta ""
         [kamera/kamerakomponentti {:esikatselukuva-atom esikatselukuva-atom
                                    :kuvaa-otetaan-atom kuvaa-otetaan-atom}]]]])))

(defn havaintolomake []
  (let [lomakedata (alusta-uusi-lomake!)
        tr-osoite-lomakkeen-avauksessa @s/tr-osoite]
    (fn []
      [havaintolomakekomponentti
       {:lomakedata lomakedata
        :tr-osoite-lomakkeen-avauksessa tr-osoite-lomakkeen-avauksessa
        :tallenna-fn tallenna-lomake!
        :kuvaa-otetaan-atom s/kuvaa-otetaan?
        :lomake-koskettu-atom s/lomake-koskettu?
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
