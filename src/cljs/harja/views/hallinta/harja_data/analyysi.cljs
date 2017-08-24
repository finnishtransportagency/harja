(ns harja.views.hallinta.harja-data.analyysi
  (:require [reagent.core :refer [atom wrap] :as r]
            [tuck.core :as tuck]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as y]
            [harja.ui.debug :as debug]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.tiedot.hallinta.harja-data.analyysi :as tiedot]))

;; TODO yhteiseen tämmöset
(defn hakuasetukset-leijuke
  [e! hakuasetukset]
  [leijuke/leijuke
    {:otsikko "Hakuasetukset"
     :sulje! #(e! (tiedot/->PaivitaArvoFunktio not :hakuasetukset-nakyvilla?))}
    [lomake/lomake
      {:muokkaa! #(e! (tiedot/->PaivitaArvo % :hakuasetukset))
       :footer [napit/yleinen-ensisijainen "Päivitä" #(e! (tiedot/->HaeAnalyysi))]}
      [{:otsikko "Naytettavat ryhmät"
        :nimi :naytettavat-ryhmat
        :tyyppi :checkbox-group
        :vaihtoehdot [:hae :tallenna :urakka :muut]
        :nayta-rivina? true
        ::lomake/col-luokka ""}]
      hakuasetukset]])

(defn hakuasetukset-bar
  [e! hakuasetukset-nakyvilla? hakuasetukset]
  [:div.container
    ^{:key "analyysi-hakuasetukset"}
    [:div.inline-block
      [napit/yleinen-ensisijainen "Hakuasetukset" #(e! (tiedot/->PaivitaArvoFunktio not :hakuasetukset-nakyvilla?))]
      (when hakuasetukset-nakyvilla? [hakuasetukset-leijuke e! hakuasetukset])]])

(defn yhteyskatkosanalyysi
  [{:keys [eniten-katkoksia pisimmat-katkokset rikkinaiset-lokitukset eniten-katkosryhmia] :as analyysi}]
  [:div
    [:p (str "Rikkinaisia lokituksia: " rikkinaiset-lokitukset)]
    [:p (str "Eniten katkoksia näillä palvelukutsuilla: " (pr-str eniten-katkoksia))]
    [:p (str "Eniten katkosryhmiä näillä palvelukutsuilla: " (pr-str eniten-katkosryhmia))]
    [:p (str "Pisimmät katkosvälit näillä palvelukutsuilla: " (pr-str pisimmat-katkokset))]])

(defn analyysi-paanakyma
  [e! {:keys [analyysi-tehty? analyysi hakuasetukset hakuasetukset-nakyvilla?]}]
  [:div
    [hakuasetukset-bar e! hakuasetukset-nakyvilla? hakuasetukset]
    [:h3 "Yhteyskatkosanalyysi"]
    (if analyysi-tehty?
      [yhteyskatkosanalyysi analyysi]
      [y/ajax-loader])])

(defn analyysi* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (tiedot/->Nakymassa? true)
                           (when (empty? (:analyysi app))
                             (e! (tiedot/->HaeAnalyysi))))
                      #(e! (tiedot/->Nakymassa? false)))
    (fn [e! app]
      [:div
        [debug/debug app]
        [analyysi-paanakyma e! app]])))

(defn analyysi []
  [tuck/tuck tiedot/app analyysi*])
