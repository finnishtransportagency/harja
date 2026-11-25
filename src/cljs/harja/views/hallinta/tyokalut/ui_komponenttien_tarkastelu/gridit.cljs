(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.gridit
  (:require [reagent.core :as r]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi :refer [register!]]))

(def ^:private sarakkeet
  [{:otsikko "Nimi" :nimi :nimi :leveys 2}
   {:otsikko "Määrä" :nimi :maara :leveys 1}
   {:otsikko "Yks." :nimi :yksikko :leveys 0.5 :muokattava? (constantly false)}])

(def ^:private rivit
  [{:id 1 :nimi "Sorastus" :maara 12 :yksikko "tn"}
   {:id 2 :nimi "Paikkaus" :maara 4 :yksikko "kpl"}])

(defn- perusgrid [komponentti]
  [kehys/kortti komponentti
   [:p "Taulukko rivien ja sarakkeiden esittämiseen. Sarakkeet ja rivit tulevat käyttöpaikalta."]
   [:div {:data-cy (kehys/ankkuri komponentti "-grid")}
    [grid/grid {:otsikko "Perusgrid"
                :tyhja "Ei rivejä."
                :tunniste :id}
     sarakkeet
     rivit]]])

(defn- muokkausgrid [komponentti]
  (r/with-let [rivit (r/atom {1 {:id 1 :nimi "Murske" :maara 10 :yksikko "tn"}
                              2 {:id 2 :nimi "Sora" :maara 5 :yksikko "tn"}})]
    [kehys/kortti komponentti
     [:p "Muokkausgrid käsittelee rivit atomissa. Muutokset kulkevat käyttöpaikan muutos-callbackille."]
     [:div {:data-cy (kehys/ankkuri komponentti "-grid")}
      [grid/muokkaus-grid {:otsikko "Muokkausgrid"
                           :tyhja "Ei muokattavia rivejä."
                           :voi-muokata? true
                           :voi-lisata? false
                           :voi-kumota? false
                           :muutos (fn [_] nil)}
       [{:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys 2}
        {:otsikko "Määrä" :nimi :maara :tyyppi :numero :leveys 1}
        {:otsikko "Yks." :nimi :yksikko :muokattava? (constantly false) :leveys 0.5}]
       rivit]]]))

;; EDN ei voi sisältää funktioita, joten solutyypit rekisteröidään avaimilla.
;; Tuntematon :solu-avain näyttää sarakkeen tekstinä.
(def ^:private solutyypit
  {:tila-indikaattori (fn [nimi]
                        (fn [rivi _opts]
                          [yleiset/tila-indikaattori (str (get rivi nimi))]))})

(defn- simulaattorin-sarake [sarake]
  (let [solu (get solutyypit (:solu sarake))]
    (cond-> (select-keys sarake [:otsikko :nimi :leveys :tyyppi])
      solu (assoc :tyyppi :komponentti
                  :komponentti (solu (:nimi sarake))))))

(defn- perusgrid-simulaattorissa [solmu _tila _lapsi-fn]
  (let [{:keys [otsikko tyhja tunniste sarakkeet rivit]} (:asetukset solmu)]
    [:div {:data-cy "ui-simulaattori-perusgrid"}
     [grid/grid {:otsikko otsikko
                 :tyhja (or tyhja "Ei rivejä.")
                 :tunniste (or tunniste :id)}
      (mapv simulaattorin-sarake sarakkeet)
      (vec rivit)]]))

(def ^:private muokkausgridin-tyypit #{:string :numero})

(defn- muokkausgridin-sarake [sarake]
  (cond-> (select-keys sarake [:otsikko :nimi :leveys])
    (muokkausgridin-tyypit (:tyyppi sarake)) (assoc :tyyppi (:tyyppi sarake))))

(defn- muokkausgrid-simulaattorissa-sisalto [solmu]
  (r/with-let [rivit (r/atom (into {} (map (juxt :id identity)) (get-in solmu [:asetukset :rivit])))]
    (let [{:keys [otsikko tyhja voi-muokata? voi-lisata? sarakkeet]} (:asetukset solmu)]
      [:div {:data-cy "ui-simulaattori-muokkausgrid"}
       ;; nil ei ole muokkausgridissä sama kuin false, joten booleanit annetaan aina.
       [grid/muokkaus-grid {:otsikko otsikko
                            :tyhja (or tyhja "Ei muokattavia rivejä.")
                            :voi-muokata? (boolean voi-muokata?)
                            :voi-lisata? (boolean voi-lisata?)
                            :voi-kumota? false
                            :muutos (fn [_] nil)}
        (mapv muokkausgridin-sarake sarakkeet)
        rivit]])))

(defn- muokkausgrid-simulaattorissa [solmu _tila _lapsi-fn]
  [muokkausgrid-simulaattorissa-sisalto solmu])

(register! :perusgrid {:esikatselu perusgrid
                       :renderoi perusgrid-simulaattorissa})
(register! :muokkausgrid {:esikatselu muokkausgrid
                          :renderoi muokkausgrid-simulaattorissa})

