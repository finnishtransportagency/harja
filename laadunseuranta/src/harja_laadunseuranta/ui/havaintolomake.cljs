(ns harja-laadunseuranta.ui.havaintolomake
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.ui.kamera :as kamera]
            [harja-laadunseuranta.ui.napit :refer [nappi]]
            [harja.ui.ikonit :as ikonit]
            [harja-laadunseuranta.tiedot.havaintolomake :refer [alusta-uusi-lomake!
                                                                tallenna-lomake!
                                                                peruuta-lomake!]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.ui.lomake :refer [kentta tekstialue
                                                    pvm-aika tr-osoite]])
  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(defn- havaintolomakekomponentti [{:keys [lomakedata tallenna-fn peruuta-fn]}]
  (let [kuvaus (reagent/cursor lomakedata [:kuvaus])
        aikaleima (reagent/cursor lomakedata [:aikaleima])
        tr-os (reagent/cursor lomakedata [:tr-osoite])
        kuva (reagent/cursor lomakedata [:kuva])
        kayttajanimi (reagent/cursor lomakedata [:kayttajanimi])
        laadunalitus? (reagent/cursor lomakedata [:laadunalitus?])
        virheita (atom #{})]
    (fn []
      [:div.lomake-container
       [:div.havaintolomake
        [:div.lomake-title "Uuden havainnon perustiedot"]

         [:div.pvm-kellonaika-tarkastaja
          [kentta "Päivämäärä" [pvm-aika aikaleima]]
          [kentta "Tarkastaja" [:span.tarkastaja @kayttajanimi]]]

         [:div.tieosuus
          [kentta "Tieosuus" [tr-osoite @tr-os]]]

         [:div.lisatietoja
          [:div.laatupoikkeama-check
           [:input {:id "laadunalitus"
                    :type "checkbox"
                    :on-change #(swap! laadunalitus? not)}]
           [:label {:for "laadunalitus"} "Laadun alitus"]]
          [:div.title "Lisätietoja"]
          [tekstialue kuvaus]
          [kamera/kamerakomponentti kuva]]

         [:div.lomake-painikkeet
          [nappi "Tallenna" {:on-click #(when (empty? @virheita)
                                         (tallenna-fn @lomakedata))
                             :disabled (not (empty? @virheita))
                             :luokat-str "nappi-myonteinen"
                             :ikoni (ikonit/livicon-save)}]
          [nappi "Peruuta" {:luokat-str "nappi-kielteinen"
                            :on-click peruuta-fn}]]]])))

(defn havaintolomake []
  (let [lomakedata (alusta-uusi-lomake!)]
    [havaintolomakekomponentti
      {:lomakedata lomakedata
       :tallenna-fn tallenna-lomake!
       :peruuta-fn peruuta-lomake!}]))

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
