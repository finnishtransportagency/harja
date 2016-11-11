(ns harja-laadunseuranta.ui.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn off-painike [{:keys [nimi avain poista-jatkuva-havainto] :as tiedot}]
  [:button.nappi-kielteinen.nappaimisto-lopeta-nappi
   {:on-click (fn [_]
                (poista-jatkuva-havainto avain))}
   (str nimi " päättyy")])

(defn- nappaimistokomponentti [{:keys [nimi poista-jatkuva-havainto mittaustyyppi] :as tiedot}]
  []
  [:div.nappaimisto-container
   [:div.nappaimisto
   [off-painike {:nimi nimi
                 :mittaustyyppi mittaustyyppi
                 :poista-jatkuva-havainto poista-jatkuva-havainto}]
   #_[avattu-nuoli]
   #_[kitkamittaustiedot keskiarvo-atom]
   #_[kitkamittaus/kitkamittauskomponentti (fn [mittaus]
                                           (swap! keskiarvo-atom #(conj % mittaus))
                                           (kitkamittaus-kirjattu mittaus))]]])

(defn nappaimisto [mittaus]
  [nappaimistokomponentti {:mittaustyyppi (:tyyppi mittaus)
                           :nimi (:nimi mittaus)
                           :poista-jatkuva-havainto s/poista-jatkuva-havainto!}])

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