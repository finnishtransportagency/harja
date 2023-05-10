(ns harja.tiedot.vesivaylat.hallinta.liikennetapahtumien-ketjutus
  (:require [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [reagent.core :refer [atom]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.kanavat.urakka.liikenne :as liikenne]))

(defonce tila (atom {:haettu-urakka-id nil
                     :haettu-sopimus-id nil}))

(defrecord ValitseSopimus [sopimus])
(defrecord TallennaKetjutus [sopimus kaytossa?])
(defrecord KetjutusTallennettu [vastaus])
(defrecord KetjutusEiTallennettu [virhe])
(defrecord HaeSopimukset [sopimus-id urakka-id])
(defrecord SopimuksetHaettu [sopimukset])
(defrecord SopimuksetEiHaettu [virhe])


(extend-protocol tuck/Event
  ValitseSopimus
  (process-event [{sopimus :sopimus} app]
    (assoc app :valittu-sopimus sopimus))

  HaeSopimukset
  (process-event [{sopimus-id :sopimus-id urakka-id :urakka-id} 
                  {:keys [haettu-urakka-id haettu-sopimus-id] :as app}]
    ;; Jos valitun urakan sopimusta ei ole vielä haettu
    (if (or
          ;; Haetaan kaikki sopimukset (hallinta)
          (and
            (= urakka-id nil)
            (= sopimus-id nil))
          ;; Haetaan liikennevälilehdellä tietyn urakan sopimus
          (and
            (not= haettu-urakka-id urakka-id)
            (not= haettu-sopimus-id sopimus-id)
            (not= sopimus-id nil)
            (not= urakka-id nil)))
      (-> app
        (assoc :haettu-urakka-id urakka-id)
        (assoc :haettu-sopimus-id sopimus-id)
        (assoc :sopimuksien-haku-kaynnissa? true)
        (tuck-apurit/post! :hae-vesivayla-kanavien-hoito-sopimukset
          {:sopimus-id sopimus-id
           :urakka-id urakka-id}
          {:onnistui ->SopimuksetHaettu
           :epaonnistui ->SopimuksetEiHaettu}))
      app))

  SopimuksetHaettu
  (process-event [{sopimukset :sopimukset} app]
    (-> app
      (assoc :haetut-sopimukset sopimukset)
      (assoc :sopimuksien-haku-kaynnissa? false)))

  SopimuksetEiHaettu
  (process-event [_ app]
    (viesti/nayta! [:span "Virhe sopimuksien haussa!"] :danger)
    (assoc app :sopimuksien-haku-kaynnissa? false))

  TallennaKetjutus
  (process-event [{sopimus :sopimus kaytossa? :kaytossa?} app]
    (tuck-apurit/post! app :tallenna-ketjutus
      {:tiedot sopimus
       :kaytossa? kaytossa?}
      {:onnistui ->KetjutusTallennettu
       :epaonnistui ->KetjutusEiTallennettu})
    (assoc app :tallennus-kaynnissa? true))

  KetjutusTallennettu
  (process-event [{vastaus :vastaus} app]
    (liikenne/paivita-liikennenakyma)
    (-> app
      (assoc :haetut-sopimukset vastaus)
      (assoc :tallennus-kaynnissa? false)))

  KetjutusEiTallennettu
  (process-event [{virhe :virhe} app]
    (viesti/nayta! [:span "Virhe tallennuksessa! Ketjutusta ei tallennettu."] :danger)
    (viesti/nayta-toast! (str "KetjutusEiTallennettu \n Vastaus: " (pr-str virhe)) :varoitus)
    (assoc app :tallennus-kaynnissa? false :valittu-sopimus nil)))
