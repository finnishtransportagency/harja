(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce tila
  (atom {:valinnat {:urakka-id nil
                    :sopimus-id nil
                    :aikavali [nil nil]
                    :vaylatyyppi :kauppamerenkulku}
         :nakymassa? false
         :infolaatikko-nakyvissa? false
         ;; TODO Testidataa vain
         :toimenpiteet [{::to/id 0
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Kuopio, Iisalmen väylä"
                                     ::va/id 1}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/vikakorjaus true
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 1
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Kuopio, Iisalmen väylä"
                                     ::va/id 1}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 2
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Kuopio, Iisalmen väylä"
                                     ::va/id 1}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 3
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 600
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Asennustyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 601
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 1"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 602
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 2"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 603
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 3"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 604
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 4"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 605
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 5"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 606
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 6"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 607
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 7"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 608
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 8"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 609
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 9"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 610
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 10"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 611
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 11"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 612
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 12"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 613
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 13"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 614
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 14"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 615
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 15"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 616
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 16"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 617
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 17"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 618
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 18"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 619
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 19"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 620
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 20"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 621
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 21"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 622
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 22"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 623
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 23"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 624
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 24"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 625
                         ::to/tyolaji :viitat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 25"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 4
                         ::to/tyolaji :kiinteat
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 5
                         ::to/tyolaji :poijut
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}
                        {::to/id 6
                         ::to/tyolaji :poijut
                         ::to/vayla {::va/nimi "Varkaus, Kuopion väylä"
                                     ::va/id 2}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {::tu/nimi "Siitenluoto (16469)"}}]}))

(def valinnat
  (reaction
    {:urakka-id (:id @nav/valittu-urakka)
     :sopimus-id (first @u/valittu-sopimusnumero)
     :aikavali @u/valittu-aikavali}))

(defrecord Nakymassa? [nakymassa?])
(defrecord ValitseToimenpide [tiedot])
(defrecord ValitseTyolaji [tiedot])
(defrecord ValitseVayla [tiedot])
(defrecord PaivitaValinnat [tiedot])
(defrecord AsetaInfolaatikonTila [uusi-tila])

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  PaivitaValinnat
  (process-event [{tiedot :tiedot} app]
    (assoc app :valinnat (merge (:valinnat app)
                                (select-keys tiedot
                                             [:urakka-id :sopimus-id :aikavali :vaylatyyppi]))))

  ValitseToimenpide
  (process-event [{tiedot :tiedot} {:keys [toimenpiteet] :as app}]
    (let [toimenpide-id (:id tiedot)
          valinta (:valinta tiedot)
          paivitetty-toimenpide (-> (to/toimenpide-idlla toimenpiteet toimenpide-id)
                                    (assoc :valittu? valinta))]
      (assoc app :toimenpiteet (mapv #(if (= (::to/id %) toimenpide-id) paivitetty-toimenpide %)
                                     toimenpiteet))))

  ValitseTyolaji
  (process-event [{tiedot :tiedot} {:keys [toimenpiteet] :as app}]
    (let [tyolaji (:tyolaji tiedot)
          valinta (:valinta tiedot)
          paivitetyt-toimenpiteet (mapv #(if (= (::to/tyolaji %) tyolaji)
                                           (assoc % :valittu? valinta)
                                           %)
                                        toimenpiteet)]
      (assoc app :toimenpiteet paivitetyt-toimenpiteet)))

  ValitseVayla
  (process-event [{tiedot :tiedot} {:keys [toimenpiteet] :as app}]
    (let [vayla-id (:vayla-id tiedot)
          valinta (:valinta tiedot)
          paivitetyt-toimenpiteet (mapv #(if (= (get-in % [::to/vayla ::va/id]) vayla-id)
                                           (assoc % :valittu? valinta)
                                           %)
                                        toimenpiteet)]
      (assoc app :toimenpiteet paivitetyt-toimenpiteet)))

  AsetaInfolaatikonTila
  (process-event [{uusi-tila :uusi-tila} app]
    (assoc app :infolaatikko-nakyvissa? uusi-tila)))