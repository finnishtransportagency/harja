(ns harja-laadunseuranta.ui.havaintolomake
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.ui.kamera :as kamera]
            [harja-laadunseuranta.ui.napit :refer [nappi]]
            [harja-laadunseuranta.tiedot.havaintolomake :refer [alusta-uusi-lomake!
                                                                tallenna-lomake!
                                                                peruuta-lomake!]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.ui.lomake :refer [kentta tekstialue
                                                    pvm-aika tr-osoite]]
            [cljs-time.format :as time-fmt]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat])

  (:require-macros [reagent.ratom :refer [run!]]
                   [devcards.core :refer [defcard]]))

(defn- havaintolomakekomponentti [{:keys [lomakedata tallenna-fn peruuta-fn]}]
  (let [kuvaus (reagent/cursor lomakedata [:kuvaus])
        aikaleima (reagent/cursor lomakedata [:aikaleima])
        tr-os (reagent/cursor lomakedata [:tr-osoite])
        esikatselukuva (reagent/cursor lomakedata [:esikatselukuva])
        kayttajanimi (reagent/cursor lomakedata [:kayttajanimi])
        laadunalitus? (reagent/cursor lomakedata [:laadunalitus?])
        lomake-virheet (atom #{})]
    (fn []
      [:div.lomake-container
       [:div.havaintolomake
        [:div.lomake-title "Uuden havainnon perustiedot"]

        [:div.pvm-kellonaika-tarkastaja
         ;; Päivämäärä-kenttää ei ole koskaan voinut muokata, vaikka on input-tyyppinen
         ;; Näytetään toistaiseksi vain tekstinä.
         ;; TODO Jatkossa olisi hyvä, jos voi muokata. Tässä voinee käyttää
         ;; HTML5:n natiivia date ja time tyyppiä, on hyvn tuettu mobiilissa.
         [kentta "Päivämäärä" (time-fmt/unparse fmt/pvm-fmt @aikaleima)]
         #_[kentta "Päivämäärä" [pvm-aika aikaleima]]
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
         [kamera/kamerakomponentti esikatselukuva]]

        [:div.lomake-painikkeet
         [nappi "Tallenna" {:on-click (fn []
                                        (.log js/console. "Tallenna. Virheet: " (pr-str @lomake-virheet))
                                        (when (empty? @lomake-virheet)
                                          (tallenna-fn @lomakedata)))
                            :disabled (not (empty? @lomake-virheet))
                            :luokat-str "nappi-myonteinen"
                            :ikoni (kuvat/svg-sprite "tallenna-18")}]
         [nappi "Peruuta" {:luokat-str "nappi-kielteinen"
                           :on-click peruuta-fn}]]]])))

(defn havaintolomake []
  (let [lomakedata (alusta-uusi-lomake!)]
    (fn []
      [havaintolomakekomponentti
       {:lomakedata lomakedata
        :tallenna-fn tallenna-lomake!
        :peruuta-fn peruuta-lomake!}])))

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
