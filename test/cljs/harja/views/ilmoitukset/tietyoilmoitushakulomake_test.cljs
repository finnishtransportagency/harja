(ns harja.views.ilmoitukset.tietyoilmoitushakulomake-test
  (:require [tuck.core :refer [tuck]]
            [cljs.test :as t :refer-macros [deftest is]]
            [reagent.core :as r]
            [clojure.string :as str]
            [cljs-react-test.simulate :as sim]
            [harja.ui.grid :as g]
            [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu jvh-fixture]]
            [harja.testutils.shared-testutils :as u]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-tiedot]
            [harja.views.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-view])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]
                   [cljs.core.async.macros :refer [go]]))

(t/use-fixtures :each u/komponentti-fixture fake-palvelut-fixture jvh-fixture)

(defn fakeilmoitushaku [{:keys [alkuaika
                                loppuaika
                                sijainti
                                urakka
                                vain-kayttajan-luomat] :as hakuehdot}]
  (println "---> hakuehdot" hakuehdot)
  [])

(deftest tietyoilmoitusten-haku

  (let [app tietyoilmoitukset-tiedot/tietyoilmoitukset
        haku (fake-palvelukutsu :hae-tietyoilmoitukset fakeilmoitushaku)]
    
    (komponenttitesti
      [tuck app tietyoilmoitukset-view/ilmoitukset*]
      --
      (<! haku)

      (is (nil? (u/grid-solu "tietyoilmoitushakutulokset" 0 0)))
      (is (= "Ei löytyneitä tietoja" (u/text :.tyhja)))
      --

      "Avataan aikahaku"
      (u/click :button.nappi-alasveto)
      --
      (u/click ".harja-alasvetolistaitemi:nth-child(1) > a")
      (<! haku)


      )

    )
  )

