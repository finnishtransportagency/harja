(ns harja.views.hallinta.rahavarausten-tehtavat
  (:require [clojure.string :as str]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.tiedot.hallinta.rahavaraukset :as tiedot]))

(defn tehtavat-vetolaatikko
  "Anna parametrina valittu tehtävä sekä kaikki mahdolliset tehtävät, joista voidaan valita."
  [e! rahavaraus-id rahavarauksen-tehtavat kaikki-tehtavat kaikki-rahavaraukset-tehtavineen]
  (let [rahavarausten-tehtavat (mapcat :tehtavat kaikki-rahavaraukset-tehtavineen)
        ;; Poistetaan kaikista tehtävistä ne, jotka on jo valittuna rahavarauksille
        kaikki-muut-tehtavat (remove (fn [tehtava]
                                       (some #(= (:id %) (:id tehtava)) rahavarausten-tehtavat))
                               kaikki-tehtavat)
        ;; Tehtäviä on tyyliin 150.
        ;; Ryhmitellään ne tehtäväryhmän otsikoiden perusteella kuten suunnittelu/tehtävät ja määrät sivulla
        ryhmat (map first (sort-by first (group-by :otsikko kaikki-muut-tehtavat)))
        ;; Otetaan ryhman otsikosta ensimmäinen tavu, joka on numero ja tehdään siitä keyword
        ota-keyword-otsikosta (fn [otsikko]
                                (keyword (first (str/split otsikko #" "))))
        k-ryhmat (mapv (fn [r] (keyword (first (str/split r #" ")))) ryhmat)
        anna-ryhma-otsikko (fn [k]
                             (->> ryhmat
                               (filter #(= (ota-keyword-otsikosta %) k))
                               first))
        ryhmittely-fn (fn [r] (ota-keyword-otsikosta (:otsikko r)))]
    [:div.rahavaraus-tehtava.col-md-8
     [napit/uusi "Lisää tehtävä" #(e! (tiedot/->LisaaUusiTehtavaRahavaraukselle rahavaraus-id)) {}]
     [grid/grid
      {:piilota-toiminnot? true}
      [{:otsikko "Tehtävä" :nimi :nimi
        :leveys 10
        :tyyppi :komponentti
        :komponentti (fn [rivi]
                       [yleiset/livi-pudotusvalikko {:class "alasveto-tehtava"
                                                     :valinta rivi
                                                     :format-fn :nimi
                                                     :vayla-tyyli? true
                                                     :nayta-ryhmat k-ryhmat
                                                     :ryhmittely ryhmittely-fn
                                                     :ryhman-otsikko #(anna-ryhma-otsikko %)
                                                     :valitse-fn #(e! (tiedot/->TallennaTehtavaRahavaraukselle rahavaraus-id % (:id rivi)))}
                        ;; Jos ollaan lisäämässä uutta, niin ei anneta valita jo valittua tehtävää uudestaan.
                        (if (= 0 (:id rivi))
                          kaikki-muut-tehtavat
                          kaikki-tehtavat)])}
       {:otsikko ""
        :leveys 2
        :tyyppi :komponentti
        :komponentti (fn [rivi]
                       (when-not (= 0 (:id rivi))
                         [napit/poista "Poista"
                          #(e! (tiedot/->PoistaTehtavaRahavaraukselta rahavaraus-id (:id rivi)))
                          {:vayla-tyyli?  true
                           :teksti-nappi? true
                           :style         {:font-size   "14px"
                                           :margin-left "auto"}}]))}]
      rahavarauksen-tehtavat]]))

(defn rahavarausten-tehtavat* [e! _app]
  (komp/luo
    (komp/sisaan #(do
                    ;; Haetaan ennen sivun renderöintiä kaikki rahavaraukset ja niihin liitetyt tehtävät
                    (e! (tiedot/->HaeRahavarauksetTehtavineen))
                    ;; Haetaan kaikki tehtävät, joita voidaan liittää rahavarauksiin alasvetovalikon avulla
                    (e! (tiedot/->HaeTehtavat))))
    (fn [e! {:keys [rahavaraukset-tehtavineen tehtavat] :as app}]
      [:div.rahavaraukset-hallinta
       [:h1 "Rahavarauksen tehtävät"]
       [:div.urakan-rahavaraukset
        [grid/grid
         {:otsikko "Rahavaraukset"
          :tunniste :id
          :piilota-toiminnot? true
          :vetolaatikot (into {}
                          (map (juxt :id (fn [rivi] [tehtavat-vetolaatikko e! (:id rivi) (:tehtavat rivi) tehtavat rahavaraukset-tehtavineen]))
                            rahavaraukset-tehtavineen))}
         [{:tyyppi :vetolaatikon-tila :leveys 1}
          {:otsikko "Rahavaraus" :nimi :nimi :tyyppi :string :leveys 12}]
         rahavaraukset-tehtavineen]]])))

(defn rahavarausten-tehtavat []
  [tuck tiedot/tila rahavarausten-tehtavat*])
