(ns harja.tiedot.ilmoitukset
  (:require [reagent.core :refer [atom]]

            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

(defonce ilmoitusnakymassa? (atom false))
(defonce valittu-ilmoitus (atom nil))

(defonce valittu-hallintayksikko (reaction @nav/valittu-hallintayksikko))
(defonce valittu-urakka (reaction @nav/valittu-urakka))
(defonce valitut-tilat (atom []))
(defonce valittu-aikavali (atom nil))
(defonce valitut-ilmoitusten-tyypit (atom []))

(defonce hakuehto (atom nil))

(defonce haetut-ilmoitukset (atom nil))

(def viimeksi-haettu (atom (pvm/nyt)))

(defonce filttereita-vaihdettu? (reaction
                                  @valittu-hallintayksikko
                                  @valittu-urakka
                                  @valitut-tilat
                                  @valittu-aikavali
                                  @valitut-ilmoitusten-tyypit
                                  @hakuehto
                                  true))

(defn hae-ilmoitukset
  []
  ; 1.  Tee haku
  ; 2a. Jos filttereitä vaihdettu, resettaa ilmoitukset
  ; 2b. Jos ei, täydennä ilmoituksia
  (reset! viimeksi-haettu (pvm/nyt))
  (reset! filttereita-vaihdettu? false))

(def pollaus-id (atom nil))

(def +sekuntti+ 1000)
(def +minuutti+ (* 60 +sekuntti+))

(def +intervalli+ +minuutti+)

(defn lopeta-pollaus
  []
  (js/clearInterval @pollaus-id)
  (reset! pollaus-id nil))

(defn aloita-pollaus
  []
  (when @pollaus-id (lopeta-pollaus))
  (reset! pollaus-id (js/setInterval hae-ilmoitukset +intervalli+)))