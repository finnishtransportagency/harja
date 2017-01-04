(ns harja-laadunseuranta.tiedot.tarkastusajon-paattaminen
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.comms :as comms]
            [harja-laadunseuranta.ui.yleiset.napit :refer [nappi]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [cljs.core.async :refer [<! timeout]]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- aseta-uusi-tarkastusajo-sovelluksen-tilaan [sovellus]
  (-> sovellus
      (assoc
        ;; Tarkastusajon perustiedot
        :valittu-urakka nil
        :tarkastusajo-id nil
        :tarkastusajo-kaynnissa? false
        :tarkastusajo-paattymassa? false
        :tarkastusajon-paattamisvaihe nil
        ;; Ajonaikaiset tiedot
        :reittipisteet []
        :tr-tiedot {:tr-osoite {:tie nil
                                :aosa nil
                                :aet nil}
                    :talvihoitoluokka nil}
        ;; Havainnot
        :jatkuvat-havainnot #{}
        ;; Mittaukset
        :mittaussyotto {:nykyinen-syotto nil
                        :syotot []}
        :soratiemittaussyotto {:tasaisuus 5
                               :kiinteys 5
                               :polyavyys 5}
        :mittaustyyppi nil
        ;; Lomake
        :havaintolomake-auki? false
        :havaintolomakedata {:kayttajanimi nil
                             :tr-osoite nil
                             :aikaleima nil
                             :laadunalitus? false
                             :kuvaus ""
                             :kuva nil}
        :liittyvat-havainnot []
        ;; Kartta
        :kirjauspisteet []
        ;; Muut
        :ilmoitukset [])

      ;; UI:sta resetoidaan vain näkyvyys
      (assoc-in [:ui :paanavigointi :nakyvissa?] true)))

(defn- alusta-uusi-tarkastusajo! []
  (swap! s/sovellus aseta-uusi-tarkastusajo-sovelluksen-tilaan))

(defn paattaminen-peruttu! []
  (reset! s/tarkastusajo-paattymassa? false)
  (reset! s/tarkastusajon-paattamisvaihe nil))

(defn aseta-ajo-paattymaan! []
  (reset! s/tarkastusajo-paattymassa? true)
  (reset! s/tarkastusajon-paattamisvaihe :paattamisvarmistus))

(defn paata-ajo! []
  (go-loop []
           (if (<! (comms/paata-ajo! @s/tarkastusajo-id @s/valittu-urakka-id))
             (alusta-uusi-tarkastusajo!)

             ;; yritä uudelleen kunnes onnistuu, spinneri pyörii
             (do (<! (timeout 1000))
                 (recur)))))

(defn pakota-ajon-lopetus! []
  (let [ajo @s/palautettava-tarkastusajo]
    (reitintallennus/poista-tarkastusajo @s/idxdb (get ajo "tarkastusajo"))
    (reitintallennus/tyhjenna-reittipisteet! @s/idxdb))
  (reset! s/palautettava-tarkastusajo nil))

(defn lopetuspaatos-varmistettu! []
  (reset! s/valittu-urakka-id (:id (first @s/oikeus-urakoihin)))
  (reset! s/tarkastusajon-paattamisvaihe :urakkavarmistus))

(defn urakka-varmistettu! []
  (reset! s/tarkastusajon-paattamisvaihe :paatetaan)
  (paata-ajo!))