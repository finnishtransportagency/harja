(ns harja-laadunseuranta.ui.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn off-painike [otsikko havainnot avain]
  [:button.nappaimisto-lopeta
   {:on-click (fn [_]
                (swap! havainnot #(assoc % avain false)))}
   otsikko])

(defn- nappaimistokomponentti [{:keys [otsikko havainnot] :as tiedot}]
  []
  #_[:div.nappaimisto
   [off-painike otsikko havainnot :liukasta :on-click #(reset! keskiarvo-atom nil)]
   [avattu-nuoli]
   [kitkamittaustiedot keskiarvo-atom]
   [kitkamittaus/kitkamittauskomponentti (fn [mittaus]
                                           (swap! keskiarvo-atom #(conj % mittaus))
                                           (kitkamittaus-kirjattu mittaus))]])

(defn nappaimisto [otsikko]
  [:div.nappaimisto "Näppäimistö tulee tähän"]
  #_[nappaimistokomponentti {:otsikko otsikko
                             :havainnot @s/jatkuvat-havainnot}])

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