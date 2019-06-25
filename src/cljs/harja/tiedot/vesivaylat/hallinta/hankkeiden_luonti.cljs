(ns harja.tiedot.vesivaylat.hallinta.hankkeiden-luonti
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def uusi-hanke {})

(defonce tila
  (atom {:nakymassa? false
         :valittu-hanke nil
         :tallennus-kaynnissa? false
         :hankkeiden-haku-kaynnissa? false
         :haetut-hankkeet nil}))

(defrecord ValitseHanke [hanke])
(defrecord Nakymassa? [nakymassa?])
(defrecord UusiHanke [])
(defrecord TallennaHanke [hanke])
(defrecord HankeTallennettu [hanke])
(defrecord HankeEiTallennettu [virhe])
(defrecord HankettaMuokattu [hanke])
(defrecord HaeHankkeet [])
(defrecord HankkeetHaettu [hankkeet])
(defrecord HankkeetEiHaettu [virhe])

(extend-protocol tuck/Event
  ValitseHanke
  (process-event [{hanke :hanke} app]
    (assoc app :valittu-hanke hanke))

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  UusiHanke
  (process-event [_ app]
    (assoc app :valittu-hanke uusi-hanke))

  TallennaHanke
  (process-event [{hanke :hanke} app]
    (assert (some? (:haetut-hankkeet app)) "Hanke ei voi yrittää tallentaa, ennen kuin hankkeiden haku on valmis.")
    (let [tulos! (tuck/send-async! ->HankeTallennettu)
          fail! (tuck/send-async! ->HankeEiTallennettu)]
      (go
        (try
          (let [vastaus (<! (k/post! :tallenna-hanke hanke))]
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (assoc app :tallennus-kaynnissa? true))

  HankeTallennettu
  (process-event [{hanke :hanke} app]
    (viesti/nayta! "Hanke tallennettu!")
    (let [vanhat (group-by :id (:haetut-hankkeet app))
          uusi {(:id hanke) [hanke]}]
      ;; Yhdistetään tallennettu jo haettuihin.
      ;; Gridiin tultaessa Grid hakee vielä taustalla kaikki hankkeet
      ;; Tietokannasta asiat tulevat järjestettynä, mutta yritetään tässä jo saada oikea järjestys aikaan
      (assoc app :haetut-hankkeet (sort-by :alkupvm pvm/jalkeen? (vec (apply concat (vals (merge vanhat uusi)))))
                 :tallennus-kaynnissa? false
                 :valittu-hanke nil)))

  HankeEiTallennettu
  (process-event [{virhe :virhe} app]
    (viesti/nayta! [:span "Virhe tallennuksessa! Hanketta ei tallennettu."] :danger)
    (assoc app :tallennus-kaynnissa? false
               :valittu-hanke nil))

  HankettaMuokattu
  (process-event [{hanke :hanke} app]
    (assoc app :valittu-hanke hanke))

  HaeHankkeet
  (process-event [_ app]
    (let [tulos! (tuck/send-async! ->HankkeetHaettu)
          fail! (tuck/send-async! ->HankkeetEiHaettu)]
      (go
        (try
          (let [vastaus (<! (k/post! :hae-harjassa-luodut-hankkeet {}))]
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (assoc app :hankkeiden-haku-kaynnissa? true))

  HankkeetHaettu
  (process-event [{hankkeet :hankkeet} app]
    (assoc app :haetut-hankkeet hankkeet
               :hankkeiden-haku-kaynnissa? false))

  HankkeetEiHaettu
  (process-event [_ app]
    (viesti/nayta! [:span "Virhe hankkeiden haussa!"] :danger)
    (assoc app :hankkeiden-haku-kaynnissa? false)))
