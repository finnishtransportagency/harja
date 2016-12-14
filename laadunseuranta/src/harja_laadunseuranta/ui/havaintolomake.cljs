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

(defn- havaintolomakekomponentti [{:keys [lomakedata tallenna-fn peruuta-fn
                                          tr-osoite-lomakkeen-avauksessa]}]
  (let [kuvaus-atom (reagent/cursor lomakedata [:kuvaus])
        aikaleima-atom (reagent/cursor lomakedata [:aikaleima])
        tr-osoite-atom (reagent/cursor lomakedata [:tr-osoite])
        esikatselukuva-atom (reagent/cursor lomakedata [:esikatselukuva])
        kayttajanimi-atom (reagent/cursor lomakedata [:kayttajanimi])
        laadunalitus-atom (reagent/cursor lomakedata [:laadunalitus?])
        lomake-virheet-atom (atom #{})
        alusta-tr-osoite! (fn [tr-osoite-atom]
                            (when (:tie tr-osoite-lomakkeen-avauksessa)
                              (reset! tr-osoite-atom tr-osoite-lomakkeen-avauksessa)))]
    (alusta-tr-osoite! tr-osoite-atom)
    (fn []
      [:div.lomake-container
       [:div.havaintolomake
        [:div.lomake-title "Uuden havainnon perustiedot"]

        [:div.pvm-kellonaika-tarkastaja
         ;; Päivämäärä-kenttää ei ole koskaan voinut muokata, vaikka on input-tyyppinen
         ;; Näytetään siis toistaiseksi vain tekstinä.
         ;; TODO Jatkossa olisi hyvä, jos voi muokata. Tässä voinee käyttää
         ;; HTML5:n natiivia date ja time tyyppiä, on hyvn tuettu mobiilissa.
         [kentta "Päivämäärä" (str (time-fmt/unparse fmt/pvm-fmt @aikaleima-atom)
                                   " "
                                   (time-fmt/unparse fmt/klo-fmt @aikaleima-atom))]
         #_[kentta "Päivämäärä" [pvm-aika aikaleima-atom]]
         [kentta "Tarkastaja" [:span.tarkastaja @kayttajanimi-atom]]]

        [:div.tieosuus
         [kentta "Tieosuus" [tr-osoite tr-osoite-atom lomake-virheet-atom]]]

        [:div.lisatietoja
         [:div.laatupoikkeama-check
          [:input {:id "laadunalitus"
                   :type "checkbox"
                   :on-change #(swap! laadunalitus-atom not)}]
          [:label {:for "laadunalitus"} "Laadun alitus"]]
         [:div.title "Lisätietoja"]
         [tekstialue kuvaus-atom]
         [kamera/kamerakomponentti esikatselukuva-atom]]

        [:div.lomake-painikkeet
         [nappi "Tallenna" {:on-click (fn []
                                        (.log js/console. "Tallenna. Virheet: " (pr-str @lomake-virheet-atom))
                                        (when (empty? @lomake-virheet-atom)
                                          (tallenna-fn @lomakedata)))
                            :disabled (not (empty? @lomake-virheet-atom))
                            :luokat-str (str "nappi-myonteinen "
                                             (when-not (empty? @lomake-virheet-atom)
                                               "nappi-disabloitu"))
                            :ikoni (kuvat/svg-sprite "tallenna-18")}]
         [nappi "Peruuta" {:luokat-str "nappi-kielteinen"
                           :on-click peruuta-fn}]]]])))

(defn havaintolomake []
  (let [lomakedata (alusta-uusi-lomake!)
        tr-osoite-lomakkeen-avauksessa @s/tr-osoite]
    (fn []
      [havaintolomakekomponentti
       {:lomakedata lomakedata
        :tr-osoite-lomakkeen-avauksessa tr-osoite-lomakkeen-avauksessa
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
