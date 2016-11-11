(ns harja-laadunseuranta.ui.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn off-painike [{:keys [nimi avain lopeta-mittaus] :as tiedot}]
  [:button
   {:class "nappi nappi-kielteinen nappi-peruuta"
    :on-click (fn [_]
                (lopeta-mittaus avain))}
   (str nimi " päättyy")])

(defn- nappaimistokomponentti [{:keys [nimi avain lopeta-mittaus mittaustyyppi] :as tiedot}]
  []
  [:div.nappaimisto-container
   [:div.nappaimisto
   [off-painike {:nimi nimi
                 :avain avain
                 :mittaustyyppi mittaustyyppi
                 :lopeta-mittaus lopeta-mittaus}]
   #_[avattu-nuoli]
   #_[kitkamittaustiedot keskiarvo-atom]
   #_[kitkamittaus/kitkamittauskomponentti (fn [mittaus]
                                           (swap! keskiarvo-atom #(conj % mittaus))
                                           (kitkamittaus-kirjattu mittaus))]]])

(defn nappaimisto [havainto]
  [nappaimistokomponentti {:mittaustyyppi (get-in havainto [:mittaus :tyyppi])
                           :nimi (get-in havainto [:mittaus :nimi])
                           :avain (:avain havainto)
                           :lopeta-mittaus s/lopeta-jatkuvan-havainnon-mittaus!}])

;; TODO Kirjaa tähän tyyliin:
#_(kirjaa-kertakirjaus @s/idxdb
                       {:sijainti (select-keys (:nykyinen @s/sijainti) [:lat :lon])
                        :aikaleima (tc/to-long (lt/local-now))
                        :tarkastusajo @s/tarkastusajo-id
                        :havainnot @s/jatkuvat-havainnot
                        :mittaukset {:lumisuus @s/talvihoito-lumimaara
                                     :talvihoito-tasaisuus @s/talvihoito-tasaisuus
                                     :kitkamittaus @s/talvihoito-kitkamittaus
                                     :soratie-tasaisuus @s/soratie-tasaisuus
                                     :polyavyys @s/soratie-polyavyys
                                     :kiinteys @s/soratie-kiinteys}
                        ;; TODO Nämä tulee kai lomakkeelta? Pitää selvittää, miten toimii.
                        ;:kuvaus kuvaus
                        ;:laadunalitus (true? laadunalitus?)
                        ;:kuva kuva
                        })