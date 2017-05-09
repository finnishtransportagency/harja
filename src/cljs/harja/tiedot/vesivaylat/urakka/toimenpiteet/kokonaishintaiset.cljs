(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila
  (atom {:nakymassa? false
         :infolaatikko-nakyvissa? false
         ;; TODO Testidataa vain
         :toimenpiteet [{::to/id 0
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Kopio, Iisalmen väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/vikakorjaus true
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 1
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Kuopio, Iisalmen väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 2
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Kopio, Iisalmen väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 3
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 600
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Asennustyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 601
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 1"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 602
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 2"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 603
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 3"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 604
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 4"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 605
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 5"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 606
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 6"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 607
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 7"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 608
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 8"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 609
                         ::to/tyolaji :viitat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Testihuolto 9"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 4
                         ::to/tyolaji :kiinteat
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 5
                         ::to/tyolaji :poijut
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}
                        {::to/id 6
                         ::to/tyolaji :poijut
                         ::to/vayla {:nimi "Varkaus, Kuopion väylä"}
                         ::to/tyoluokka "Asennus ja huolto"
                         ::to/toimenpide "Huoltotyö"
                         ::to/pvm (pvm/nyt)
                         ::to/turvalaite {:nimi "Siitenluoto (16469)"}}]}))

(defrecord Nakymassa? [nakymassa?])
(defrecord ValitseToimenpide [tiedot])
(defrecord ValitseTyolaji [tiedot])
(defrecord AsetaInfolaatikonTila [uusi-tila])

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

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

  AsetaInfolaatikonTila
  (process-event [{uusi-tila :uusi-tila} app]
    (assoc app :infolaatikko-nakyvissa? uusi-tila)))